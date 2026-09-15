package net.jojoaddison.config.dbmigrations;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.jojoaddison.domain.enumeration.MembershipStatus;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Reduces every patient holding more than one {@code PENDING} membership to their newest, cancelling the rest.
 *
 * <h2>What this is repairing</h2>
 *
 * <p>{@code POST /api/memberships} let a patient stack pending memberships without limit — every tap of CHOOSE wrote
 * another — while item 19's verifier applies an email-keyed acknowledgement to the patient's <em>single</em> pending
 * choice and refuses rather than guessing if there is not exactly one. On 2026-09-11 an administrator verified a plan
 * and the frame dead-lettered three times over:
 * <em>"who holds 3 PENDING memberships ([…]); refusing rather than choosing one"</em>. The patient's app said
 * "Awaiting confirmation" indefinitely and nothing else was wrong. Backlog item 40.</p>
 *
 * <p>{@link net.jojoaddison.service.MembershipService} now keeps the invariant on every write, and its sweep cancels
 * <em>every</em> other pending membership rather than one — so a stacked record repairs itself the next time that
 * patient chooses a plan. This exists because they should not have to: their record is already in a state the back
 * office cannot act on, and the thing that would prompt them to tap CHOOSE again is the confirmation that is not
 * coming.</p>
 *
 * <h2>"Newest" is a guess, and in the incident it was a guess with nothing under it</h2>
 *
 * <p><strong>All three records in the incident carried no {@code created_date} at all</strong>, were on the same plan
 * and had the same name — there was no honest way to tell them apart, and the fact that they were identical is what
 * makes any choice among them harmless there. The tiebreak still has to be written down, because the collection at
 * large is not that uniform:</p>
 *
 * <ol>
 *   <li><strong>{@code created_date} first</strong>, latest wins, and a record that has none sorts as the
 *       <em>older</em>. It is stamped on every creation through the resource, so its absence means the record predates
 *       that stamping or was written outside the service — either way it is the older guess, and it is a guess.
 *       {@code created_date} is a {@code LocalDate} on {@link net.jojoaddison.domain.Membership}, so it resolves a day
 *       and never two choices made on one, which is exactly the case this migration is about.</li>
 *   <li><strong>then {@code _id}</strong>, greatest wins. An {@code ObjectId} opens with a big-endian creation
 *       timestamp and its hex spelling sorts in the same order as its bytes, so for the ids this service generates
 *       this really is creation order to the second, and within one process to the write. <b>For a hand-written
 *       string id it is a total order and nothing more</b> — deterministic, repeatable, and not a claim about time.
 *       Said plainly rather than implied: the seeded fixtures carry ids like {@code membership-kojo}.</li>
 * </ol>
 *
 * <p><strong>Why "newest" and not "the one on the plan the patient's app is showing".</strong> There is nothing here
 * to read that from — the choice the patient believes they made is in their client, not in this collection — and the
 * last thing they did is the best available reading of what they meant. The others are cancelled rather than deleted,
 * so a wrong pick is visible and reversible; a deletion would not be. That is the same reasoning
 * {@link MembershipStatusAsEnumMigration} gives for setting an unrecognised status aside instead of guessing at it.</p>
 *
 * <h2>Idempotent, and it must be</h2>
 *
 * <p>There is no transaction — production runs MongoDB standalone — so a partial run is a state this has to be
 * resumable from. After a pass every patient holds at most one pending membership, so {@link #selectStackedPending}
 * returns nothing and a second run writes nothing. <b>The selection is what a test has to observe</b>, not the
 * documents: a criterion that re-selected the same records every run and set {@code CANCELLED} over {@code CANCELLED}
 * would leave them byte-identical and churn the collection on every application start for ever. That is the trap 002
 * records in the same words, and the reason the selection is a package-private method rather than an inlined query.</p>
 *
 * <p><strong>A patient holding zero or one pending membership is never touched</strong> — the grouping keeps only
 * sets larger than one, which is both the correctness rule and the reason a re-run is free.</p>
 *
 * <h2>Two things it will not do</h2>
 *
 * <p><strong>Memberships with no owner are left exactly as they are.</strong> A {@code patient_id} that is absent or
 * blank names nobody, so grouping them together would be asserting that they belong to one person and cancelling all
 * but one of them on the strength of it. They are counted into the log line instead, so that a reader of it knows
 * they were seen and passed over.</p>
 *
 * <p><strong>It announces nothing on {@code patient-events}, and it could not.</strong> A change unit runs before the
 * application is serving and has no business publishing; and the supersession is silent on the live path too, for the
 * reason {@code MembershipService.supersedeOtherPendingChoices} sets out — hc-admin keys one plan group per patient
 * and replaces it wholesale, so a frame about a superseded membership overwrites the choice that is in force. Their
 * row already names whichever membership was last announced; this migration does not move that.</p>
 *
 * <p><strong>It does not restamp {@code modified_by} or {@code modified_date} either.</strong> Tempting, and wrong:
 * those record who last touched the record, and overwriting them to say "a migration did" loses a fact in order to
 * duplicate one Mongock's own changelog already holds.</p>
 */
@ChangeUnit(id = "membership-single-pending", order = "003", author = "hc-patient")
public class SinglePendingMembershipMigration {

    private static final Logger LOG = LoggerFactory.getLogger(SinglePendingMembershipMigration.class);

    private static final String MEMBERSHIP = "membership";
    private static final String STATUS = "status";
    private static final String PATIENT_ID = "patient_id";
    private static final String CREATED_DATE = "created_date";
    private static final String ID = "_id";

    /**
     * Oldest first, so the one to keep is the last.
     *
     * <p>Two keys, in the order the class javadoc argues for. {@code null} sorts first on both, which for
     * {@code created_date} is the deliberate reading that an unstamped record is the older one.</p>
     */
    private static final Comparator<Document> OLDEST_FIRST = Comparator
        .comparing(SinglePendingMembershipMigration::createdAt, Comparator.nullsFirst(Comparator.<Date>naturalOrder()))
        .thenComparing(SinglePendingMembershipMigration::idOf);

    private final MongoTemplate mongoTemplate;

    public SinglePendingMembershipMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Execution
    public void migrate() {
        Map<String, List<Document>> stacked = selectStackedPending();

        if (stacked.isEmpty()) {
            LOG.debug("No patient holds more than one PENDING membership");
            return;
        }

        int superseded = 0;
        for (Map.Entry<String, List<Document>> patient : stacked.entrySet()) {
            List<Document> oldestFirst = new ArrayList<>(patient.getValue());
            oldestFirst.sort(OLDEST_FIRST);
            Document keep = oldestFirst.removeLast();

            LOG.warn(
                "Patient {} holds {} PENDING memberships, which item 19's verifier refuses rather than choosing among; " +
                "keeping {} as the newest and cancelling {}",
                patient.getKey(),
                oldestFirst.size() + 1,
                keep.get(ID),
                oldestFirst.stream().map(document -> document.get(ID)).toList()
            );

            for (Document supersede : oldestFirst) {
                // The raw _id value, handed straight back to the query rather than stringified. Membership._id is an
                // ObjectId and JSON.stringify renders it as bare hex, so a criterion built from the printed form
                // matches nothing and reports a successful no-op — which cost time during the incident's manual
                // recovery, and where a near miss the other way would have deleted the record being kept.
                mongoTemplate.updateFirst(
                    Query.query(Criteria.where(ID).is(supersede.get(ID))),
                    new Update().set(STATUS, MembershipStatus.CANCELLED.name()),
                    MEMBERSHIP
                );
                superseded++;
            }
        }
        LOG.info("Reduced {} patient(s) to a single PENDING membership, cancelling {} superseded choice(s)", stacked.size(), superseded);
    }

    /**
     * The patients this migration still has work to do on: those holding more than one {@code PENDING} membership.
     *
     * <p>Extracted so a test can assert it is <b>empty on a second pass</b>, which is the only assertion that pins
     * idempotence — see the class javadoc for why comparing documents cannot.</p>
     *
     * <p>Read as raw {@link Document}s against the collection name rather than through {@code MembershipRepository}:
     * the fields this groups and sorts on — {@code patient_id}, {@code created_date} — are the stored spellings, and a
     * migration that went through the mapped type would be describing the model rather than the data. It is also the
     * only way to see a document the mapped type would refuse, which is the state 002 exists to clear up.</p>
     *
     * <p>Grouped in the application rather than by an aggregation pipeline. The whole pending set is small — it is one
     * document per patient awaiting a decision, plus exactly the stacking this repairs — and the comparator is two
     * keys with a documented null reading that would be harder to check written as BSON.</p>
     *
     * @return pending memberships by owner, keyed by {@code patient_id}, holding only owners with more than one. The
     *     iteration order is by patient id, so a log of a re-run reads the same way twice.
     */
    Map<String, List<Document>> selectStackedPending() {
        List<Document> pending = mongoTemplate.find(
            Query.query(Criteria.where(STATUS).is(MembershipStatus.PENDING.name())),
            Document.class,
            MEMBERSHIP
        );

        Map<String, List<Document>> byPatient = new TreeMap<>();
        int ownerless = 0;
        for (Document membership : pending) {
            Object owner = membership.get(PATIENT_ID);
            String patientId = owner == null ? null : String.valueOf(owner).trim();
            if (patientId == null || patientId.isEmpty()) {
                // Named nobody, so it can be grouped with nothing. Blank counts as absent, the same reading
                // PatientEventPublisher applies to a subject key.
                ownerless++;
                continue;
            }
            byPatient.computeIfAbsent(patientId, key -> new ArrayList<>()).add(membership);
        }
        if (ownerless > 0) {
            LOG.warn("{} PENDING membership(s) carry no patient_id and were left alone — they can be attributed to nobody", ownerless);
        }

        Map<String, List<Document>> stacked = new LinkedHashMap<>();
        byPatient.forEach((patientId, memberships) -> {
            if (memberships.size() > 1) {
                stacked.put(patientId, memberships);
            }
        });
        return stacked;
    }

    /**
     * When a membership says it was created, or null when it does not say.
     *
     * <p>Spring Data stores a {@code LocalDate} as a BSON date, so the stored value is a {@link Date}. Anything else —
     * a string written by hand, a value of a type this service never wrote — reads as absent rather than being parsed
     * on a guess, which sends the decision to the {@code _id} tiebreak. A migration must not fail on data it did not
     * expect; that is how one unparseable field empties a whole dataset, as {@code DevelopmentDataInitializer}
     * already records.</p>
     */
    private static Date createdAt(Document membership) {
        return membership.get(CREATED_DATE) instanceof Date created ? created : null;
    }

    /**
     * The membership's id as text, for the tiebreak.
     *
     * <p>{@code String.valueOf} rather than a cast: {@code _id} is an {@code ObjectId} for anything this service
     * wrote and a {@code String} for the hand-written fixtures, and the tiebreak only needs a total order over both.
     * See the class javadoc for what that order does and does not mean.</p>
     */
    private static String idOf(Document membership) {
        return String.valueOf(membership.get(ID));
    }

    /**
     * Deliberately does nothing.
     *
     * <p>The answer 001 and 002 both give, and here it is stronger than convention. Putting the cancelled
     * memberships back to {@code PENDING} would recreate exactly the state item 19's verifier refuses to act on — a
     * rollback that reinstates the defect. And by the time one ran, an administrator may have verified the membership
     * this kept; restoring its rivals would leave a patient holding an {@code ACTIVE} plan and a pending request for
     * another.</p>
     */
    @RollbackExecution
    public void rollback() {
        LOG.warn("membership-single-pending is not rolled back automatically — see the class comment");
    }
}
