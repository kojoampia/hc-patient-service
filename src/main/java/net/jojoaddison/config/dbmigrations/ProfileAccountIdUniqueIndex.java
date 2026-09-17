package net.jojoaddison.config.dbmigrations;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.List;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;

/**
 * Makes {@code Profile.account_id} unique in the database, because the two writers of it cannot make it unique
 * between themselves.
 *
 * <h2>The race this closes, which is not hypothetical and has no other fix</h2>
 *
 * <p>Both writers do check-then-act. {@link ProfileAccountIdBackfill} asks whether any profile already holds an
 * account id and then writes; {@code OnboardingService} asks {@code findOneByAccountId} and then writes. Mongock's
 * distributed lock serialises concurrent application <em>starts</em>, so two backfills cannot interleave — but the
 * backfill runs as an {@code ApplicationRunner}, by which point this service is already accepting requests, so a
 * patient can be onboarding <em>while</em> it runs. Both checks see nothing, both write, and two profiles then hold
 * one account id. Mongo runs standalone here with no replica set and therefore no transaction to make either
 * check-and-write atomic.</p>
 *
 * <p><strong>What makes that worth a database-level guard rather than a follow-up.</strong> The result is silent:
 * {@code GET /api/profile/{accountId}} starts answering nondeterministically, nothing in this service ever looks
 * again, and the identifier corrupted is the one hc-admin is about to name people by. A wrong, permanent,
 * undetectable answer to "which patient is this" is not a thing to carry across two more items.</p>
 *
 * <h2>Why an index created here and not {@code @Indexed} on the field</h2>
 *
 * <p>{@code spring.data.mongodb.auto-index-creation} is unset and nothing in this repository declares an index, so
 * the annotation would be <em>decorative</em> — a claim on the field that nothing acts on. That is an argument
 * against the annotation, not against the index. A change unit creates it in every environment the application
 * starts in, which is exactly the coverage the annotation would have needed the setting for.</p>
 *
 * <h2>Partial, and the filter is load-bearing</h2>
 *
 * <p>The filter is <code>{account_id: {$exists: true}}</code>. Neither writer stores an explicit null: Spring Data's
 * converter omits a null property, so a profile with no account id has <em>no such key</em>, and the backfill only
 * ever {@code $set}s a value it has. A plain unique index would therefore see every unlinked profile as sharing one
 * key — {@code null} — and refuse the second one ever written. {@code ProfileAccountIdUniqueIndexIT} asserts the
 * omission at each writer rather than trusting it, because the whole index rests on it.</p>
 *
 * <p>A {@code sparse} unique index would also work today and is the older spelling of the same idea; partial is
 * preferred because the predicate is written down rather than implied, and because it can be narrowed later without
 * changing what kind of index it is.</p>
 *
 * <h2>The order is {@code 003.5}, and that is deliberate rather than a typo</h2>
 *
 * <p>This must exist <em>before</em> {@link ProfileAccountIdBackfill} runs, so that the backfill's very first pass is
 * already protected: with the index in place the race becomes a caught {@code DuplicateKeyException} at whichever
 * writer loses, the profile stays unlinked, and the next start reconsiders it. Created <em>after</em> the backfill it
 * would instead turn a first-run race into a refusal to start.</p>
 *
 * <p>Mongock orders change units by {@code String.compareTo} on the {@code order} field — verified against
 * {@code ChangeLogComparator} in mongock-runner-core 5.4.1 — so {@code "003.5"} sorts after {@code "003"} and before
 * {@code "004"}. It is not numbered {@code 005} because that would put it after the writer it protects, and the
 * backfill is not renumbered to {@code 005} because three documents and two javadocs already name it {@code 004}.</p>
 *
 * <h2>Run always, so the invariant cannot quietly stop being true</h2>
 *
 * <p>{@code createIndex} is a no-op when an identical index is already there, so this costs one command per start and
 * re-asserts the guarantee every time. An index dropped by hand is back on the next restart.</p>
 */
@ChangeUnit(id = "profile-account-id-unique-index", order = "003.5", author = "hc-patient", runAlways = true)
public class ProfileAccountIdUniqueIndex {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileAccountIdUniqueIndex.class);

    /** Named rather than left to Mongo's {@code account_id_1}, so a log line and a test can both say which index. */
    static final String INDEX_NAME = "profile_account_id_unique";

    private static final String PROFILE = "profile";
    private static final String ACCOUNT_ID = "account_id";
    private static final String PROFILES = "profiles";

    private final MongoTemplate mongoTemplate;

    public ProfileAccountIdUniqueIndex(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Execution
    public void migrate() {
        Index unique = new Index()
            .on(ACCOUNT_ID, Sort.Direction.ASC)
            .unique()
            .partial(PartialIndexFilter.of(Criteria.where(ACCOUNT_ID).exists(true)))
            .named(INDEX_NAME);
        try {
            mongoTemplate.indexOps(PROFILE).createIndex(unique);
        } catch (RuntimeException conflict) {
            // DIAGNOSIS, NOT A SECOND GUARD, and the distinction is why this runs here rather than before the
            // createIndex above. A pre-flight duplicate check would express the same rule twice, and this repository
            // has already paid for that once: with a criterion in both the read and the compare-and-set, deleting it
            // from the write left 26 integration tests green because the read had excluded everything the write would
            // have caught. Running only on the failure path cannot make the real guard untestable — by the time this
            // executes, the real guard has already fired.
            List<String> duplicates = duplicateAccountIds();
            if (duplicates.isEmpty()) {
                LOG.error(
                    "Could not create {} on `{}`, and no account id is held by more than one profile — so this is " +
                    "not a duplicate-key conflict. An index of the same name with different options is the usual cause.",
                    INDEX_NAME,
                    PROFILE
                );
                throw conflict;
            }
            LOG.error(
                "REFUSING TO START: {} account id(s) are held by more than one profile, so account_id cannot be made " +
                "unique: {}. Two profiles claiming one gateway account makes GET /api/profile/{{accountId}} answer " +
                "nondeterministically. Resolve them — one of the pair is a profile that should never have been linked " +
                "— and restart; this change unit runs on every start.",
                duplicates.size(),
                duplicates
            );
            throw new IllegalStateException(
                "account_id is not unique: " + duplicates.size() + " account id(s) are held by more than one profile: " + duplicates,
                conflict
            );
        }
        LOG.info("{} is in place on `{}`: account_id is unique among the profiles that carry one", INDEX_NAME, PROFILE);
    }

    /**
     * The account ids more than one profile claims, in a stable order.
     *
     * <p>Package-private so a test can read them, and only ever called after {@code createIndex} has already
     * refused — see the comment at the call site for why it is not a pre-flight check.</p>
     *
     * <p>Grouped by an aggregation rather than in the application, unlike {@link SinglePendingMembershipMigration},
     * because there is no comparator to argue about here and no reason to pull a whole collection back to count it.
     * Read against the collection name with raw {@link Document}s, so the field is the stored spelling.</p>
     */
    List<String> duplicateAccountIds() {
        Aggregation heldTwice = Aggregation.newAggregation(
            Aggregation.match(Criteria.where(ACCOUNT_ID).exists(true).ne(null)),
            Aggregation.group(ACCOUNT_ID).count().as(PROFILES),
            Aggregation.match(Criteria.where(PROFILES).gt(1)),
            Aggregation.sort(Sort.Direction.ASC, "_id")
        );
        return mongoTemplate
            .aggregate(heldTwice, PROFILE, Document.class)
            .getMappedResults()
            .stream()
            .map(group -> String.valueOf(group.get("_id")))
            .toList();
    }

    /**
     * Deliberately does nothing, and here that is a stronger answer than the convention 001 to 004 follow.
     *
     * <p>Dropping the index would remove a guarantee while every value it guards stays exactly where it is — the
     * rollback of a constraint is not the removal of the data that satisfies it. And because this unit runs on every
     * start, the very next one would put it back, so a rollback that dropped it would be undone rather than honoured.
     * Drop it by hand if that is really what is wanted, and then stop this unit from running.</p>
     */
    @RollbackExecution
    public void rollback() {
        LOG.warn("{} is not dropped automatically — see the class comment", INDEX_NAME);
    }
}
