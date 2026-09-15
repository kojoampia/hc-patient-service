package net.jojoaddison.config.dbmigrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.enumeration.MembershipStatus;
import net.jojoaddison.repository.MembershipRepository;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/**
 * The one-off reduction of stacked {@code PENDING} memberships to one per patient.
 *
 * <p>Written against raw documents for the reason {@code MembershipStatusAsEnumMigrationIT} gives, and with one
 * addition of its own: <b>the production records this repairs carried no {@code created_date} at all</b>, and a
 * fixture built through {@code MembershipRepository} would have had one stamped on it by whatever wrote it. The case
 * the incident actually presented can only be constructed at this level.</p>
 *
 * <p>Ids are real {@link ObjectId}s wherever the ordering is under test. That is not decoration:
 * {@code Membership._id} is an ObjectId in production, its hex spelling sorts in the same order as its bytes, and a
 * fixture using tidy string ids would be testing a tiebreak the data does not have. Where the ordering is <em>not</em>
 * under test the ids are strings, which is what the seeded fixtures carry.</p>
 *
 * <p>Backlog item 40.</p>
 */
@IntegrationTest
class SinglePendingMembershipMigrationIT {

    private static final String MEMBERSHIP = "membership";

    /** Where the fixture's {@code ObjectId} timestamps start. The evening of the incident, for no better reason. */
    private static final Instant ID_TIMELINE = Instant.parse("2026-09-11T20:00:00Z");

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private MembershipRepository membershipRepository;

    private SinglePendingMembershipMigration migration;

    @BeforeEach
    void initTest() {
        mongoTemplate.remove(new Query(), MEMBERSHIP);
        migration = new SinglePendingMembershipMigration(mongoTemplate);
    }

    @Test
    void theIncidentsThreeIdenticalPendingMembershipsBecomeOne() {
        // 2026-09-11, as it was: three memberships, same patient, same plan, same name, NO created_date on any of
        // them. The consumer refused every acknowledgement with MORE_THAN_ONE_PENDING_MEMBERSHIP and the patient's
        // app said "Awaiting confirmation" indefinitely.
        ObjectId first = pending("office", null, 0);
        ObjectId second = pending("office", null, 1);
        ObjectId third = pending("office", null, 2);

        migration.migrate();

        // The newest by the documented tiebreak: with no created_date anywhere the decision falls entirely to _id,
        // and an ObjectId opens with a big-endian creation timestamp. All three were identical in the incident, which
        // is what makes any choice among them harmless there and is why the rule is written down rather than relied on.
        assertThat(status(third)).isEqualTo(MembershipStatus.PENDING);
        assertThat(status(first)).isEqualTo(MembershipStatus.CANCELLED);
        assertThat(status(second)).isEqualTo(MembershipStatus.CANCELLED);

        // Cancelled, not deleted. The manual recovery deleted the spares by hand and very nearly deleted the one
        // being kept; three records go in and three come out.
        assertThat(membershipRepository.findByPatientId("office")).hasSize(3);
    }

    @Test
    void theNewestCreatedDateWinsOverTheIdOrdering() {
        // created_date leads, so a record written later but stamped earlier still loses. Constructed against the
        // ObjectId order deliberately: with the keys the other way round this test passes for the wrong reason.
        // stampedLater deliberately carries the SMALLER id, so the id tiebreak alone would give the other answer.
        ObjectId stampedLater = pending("ama", LocalDate.parse("2026-09-04"), 0);
        ObjectId stampedEarlier = pending("ama", LocalDate.parse("2026-09-01"), 1);

        migration.migrate();

        assertThat(status(stampedLater)).isEqualTo(MembershipStatus.PENDING);
        assertThat(status(stampedEarlier)).isEqualTo(MembershipStatus.CANCELLED);
    }

    @Test
    void aRecordWithNoCreatedDateIsTreatedAsTheOlderOne() {
        // The reading the class javadoc argues for: created_date is stamped on every creation through the resource,
        // so its absence means the record predates that stamping. It is a guess and it is written down as one.
        // The unstamped record carries the GREATER id, so the id tiebreak alone would keep it. Only the nulls-first
        // reading of created_date puts it behind, which is what makes this test observe the rule it names.
        ObjectId unstamped = pending("kofi", null, 1);
        ObjectId stamped = pending("kofi", LocalDate.parse("2026-09-01"), 0);

        migration.migrate();

        assertThat(status(stamped)).isEqualTo(MembershipStatus.PENDING);
        assertThat(status(unstamped)).isEqualTo(MembershipStatus.CANCELLED);
    }

    @Test
    void aPatientHoldingOnePendingMembershipIsNotTouched() {
        // The correctness rule, and the reason a re-run is free. This is almost every patient in the collection.
        mongoTemplate
            .getCollection(MEMBERSHIP)
            .insertOne(new Document("_id", "m-one").append("patient_id", "esi").append("status", "PENDING"));

        migration.migrate();

        assertThat(membershipRepository.findById("m-one").orElseThrow().getStatus()).isEqualTo(MembershipStatus.PENDING);
        assertThat(migration.selectStackedPending()).isEmpty();
    }

    @Test
    void membershipsThatAreNotPendingAreNeverCounted() {
        // A patient holding one PENDING and three finished memberships is not a stacked patient. Counting by
        // patient rather than by status would cancel a pending choice that was the only one there was.
        mongoTemplate
            .getCollection(MEMBERSHIP)
            .insertOne(new Document("_id", "m-live").append("patient_id", "adjoa").append("status", "PENDING"));
        mongoTemplate
            .getCollection(MEMBERSHIP)
            .insertOne(new Document("_id", "m-old").append("patient_id", "adjoa").append("status", "EXPIRED"));
        mongoTemplate
            .getCollection(MEMBERSHIP)
            .insertOne(new Document("_id", "m-gone").append("patient_id", "adjoa").append("status", "CANCELLED"));
        mongoTemplate
            .getCollection(MEMBERSHIP)
            .insertOne(new Document("_id", "m-held").append("patient_id", "adjoa").append("status", "ACTIVE"));

        migration.migrate();

        assertThat(migration.selectStackedPending()).isEmpty();
        assertThat(membershipRepository.findById("m-live").orElseThrow().getStatus()).isEqualTo(MembershipStatus.PENDING);
        assertThat(membershipRepository.findById("m-old").orElseThrow().getStatus()).isEqualTo(MembershipStatus.EXPIRED);
        assertThat(membershipRepository.findById("m-held").orElseThrow().getStatus()).isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    void onePatientsStackDoesNotReachAnother() {
        ObjectId amasOlder = pending("ama", null, 0);
        ObjectId amasNewer = pending("ama", null, 1);
        mongoTemplate
            .getCollection(MEMBERSHIP)
            .insertOne(new Document("_id", "m-kofi").append("patient_id", "kofi").append("status", "PENDING"));

        migration.migrate();

        assertThat(status(amasNewer)).isEqualTo(MembershipStatus.PENDING);
        assertThat(status(amasOlder)).isEqualTo(MembershipStatus.CANCELLED);
        assertThat(membershipRepository.findById("m-kofi").orElseThrow().getStatus()).isEqualTo(MembershipStatus.PENDING);
    }

    @Test
    void ownerlessPendingMembershipsAreLeftAlone() {
        // A patient_id that is absent or blank names nobody, so grouping these together would assert that they belong
        // to one person and cancel all but one on the strength of it.
        //
        // TWO OF EACH, deliberately. One absent and one blank would each group alone, so dropping either half of the
        // guard would leave every set at size one and this test would pass with the guard gone — the vacuous shape
        // this repository has had to undo several times. A pair of each is what makes both halves observable.
        mongoTemplate.getCollection(MEMBERSHIP).insertOne(new Document("_id", "m-nobody-1").append("status", "PENDING"));
        mongoTemplate.getCollection(MEMBERSHIP).insertOne(new Document("_id", "m-nobody-2").append("status", "PENDING"));
        mongoTemplate
            .getCollection(MEMBERSHIP)
            .insertOne(new Document("_id", "m-blank-1").append("patient_id", "   ").append("status", "PENDING"));
        mongoTemplate
            .getCollection(MEMBERSHIP)
            .insertOne(new Document("_id", "m-blank-2").append("patient_id", "   ").append("status", "PENDING"));

        migration.migrate();

        assertThat(migration.selectStackedPending()).isEmpty();
        assertThat(membershipRepository.findById("m-nobody-1").orElseThrow().getStatus()).isEqualTo(MembershipStatus.PENDING);
        assertThat(membershipRepository.findById("m-nobody-2").orElseThrow().getStatus()).isEqualTo(MembershipStatus.PENDING);
        assertThat(membershipRepository.findById("m-blank-1").orElseThrow().getStatus()).isEqualTo(MembershipStatus.PENDING);
        assertThat(membershipRepository.findById("m-blank-2").orElseThrow().getStatus()).isEqualTo(MembershipStatus.PENDING);
    }

    @Test
    void runningItTwiceChangesNothingTheSecondTime() {
        ObjectId older = pending("office", null, 0);
        ObjectId newer = pending("office", null, 1);

        migration.migrate();
        Document afterFirst = mongoTemplate.findById(older, Document.class, MEMBERSHIP);
        migration.migrate();
        Document afterSecond = mongoTemplate.findById(older, Document.class, MEMBERSHIP);

        // THE SELECTION, NOT THE MUTATION, for the reason 002's own idempotence test spells out: a criterion that
        // re-selected the same records every run and set CANCELLED over CANCELLED would leave them byte-identical and
        // churn the collection on every application start for ever. An equality assertion cannot tell the two apart.
        // It has to be idempotent because there is no transaction, so a partial run is a state this must resume from.
        assertThat(migration.selectStackedPending()).isEmpty();

        assertThat(afterSecond).isEqualTo(afterFirst);
        assertThat(status(newer)).isEqualTo(MembershipStatus.PENDING);
        assertThat(status(older)).isEqualTo(MembershipStatus.CANCELLED);
    }

    /**
     * A pending membership as the incident's were: written straight into the collection with a real
     * {@link ObjectId}, so that the {@code _id} tiebreak is exercised against the form production carries.
     *
     * <p><strong>The id's timestamp is set explicitly rather than taken from the clock</strong>, so that the ordering
     * these tests turn on is a property of the fixture rather than of how fast the machine got through it. A bare
     * {@code new ObjectId()} is ordered by an in-process counter within a second, which is true but invisible — and a
     * test whose expected answer depends on something invisible is one nobody can debug when it flickers.</p>
     *
     * @param patientId the owner.
     * @param createdDate what to stamp, or null for the case the incident actually presented.
     * @param minutesApart how far into the fixture's own timeline this record's id sits. Greater means a greater id.
     */
    private ObjectId pending(String patientId, LocalDate createdDate, int minutesApart) {
        ObjectId id = new ObjectId(Date.from(ID_TIMELINE.plusSeconds(60L * minutesApart)));
        Document membership = new Document("_id", id)
            .append("patient_id", patientId)
            .append("status", "PENDING")
            .append("plan", "PAWPAW")
            .append("name", "PAWPAW Plan");
        if (createdDate != null) {
            // How Spring Data stores a LocalDate: a BSON date at UTC midnight.
            membership.append("created_date", Date.from(createdDate.atStartOfDay(ZoneOffset.UTC).toInstant()));
        }
        mongoTemplate.getCollection(MEMBERSHIP).insertOne(membership);
        return id;
    }

    /** Read back through the mapped type, which is what a stored value has to survive. */
    private MembershipStatus status(ObjectId id) {
        return membershipRepository.findById(id.toHexString()).orElseThrow().getStatus();
    }
}
