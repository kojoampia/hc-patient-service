package net.jojoaddison.config.dbmigrations;

import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Membership;
import net.jojoaddison.domain.enumeration.MembershipStatus;
import net.jojoaddison.repository.MembershipRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/**
 * The one-time rewriting of {@code membership.status} onto {@link MembershipStatus}.
 *
 * <p>Written against raw documents for the reason {@code AddressAsDocumentMigrationIT} gives: the whole difficulty is
 * that the mapped type no longer describes the data being migrated. Inserting through {@code MembershipRepository}
 * could not produce a lower-case {@code "active"} at all, so a test that went through {@code Membership} would be
 * asserting against data the defect cannot occur in.</p>
 *
 * <p>The reads afterwards <em>do</em> go through the repository, and that is the point of them — proving the value
 * reaches the mapped enum is proving the thing that would otherwise throw.</p>
 */
@IntegrationTest
class MembershipStatusAsEnumMigrationIT {

    private static final String MEMBERSHIP = "membership";

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private MembershipRepository membershipRepository;

    private MembershipStatusAsEnumMigration migration;

    @BeforeEach
    void initTest() {
        mongoTemplate.remove(new Query(), MEMBERSHIP);
        migration = new MembershipStatusAsEnumMigration(mongoTemplate);
    }

    @Test
    void theQualitySeedsLowerCaseActiveBecomesTheConstant() {
        // Exactly what quality/patient-demo-seed.json carried before it was hand-edited, and the value that made
        // this migration necessary.
        mongoTemplate
            .getCollection(MEMBERSHIP)
            .insertOne(new Document("_id", "membership-kojo").append("patient_id", "patient-kojo").append("status", "active"));

        migration.migrate();

        Membership migrated = membershipRepository.findById("membership-kojo").orElseThrow();
        assertThat(migrated.getStatus()).as("reads back through the mapped enum").isEqualTo(MembershipStatus.ACTIVE);
        assertThat(mongoTemplate.findById("membership-kojo", Document.class, MEMBERSHIP).getString("status")).isEqualTo("ACTIVE");
    }

    @Test
    void everyCasingResolvesToItsConstant() {
        insert("m-mixed", "Pending");
        insert("m-lower", "cancelled");
        insert("m-padded", "  EXPIRED  ");
        insert("m-upper", "SUSPENDED");

        migration.migrate();

        assertThat(status("m-mixed")).isEqualTo(MembershipStatus.PENDING);
        assertThat(status("m-lower")).isEqualTo(MembershipStatus.CANCELLED);
        assertThat(status("m-padded")).isEqualTo(MembershipStatus.EXPIRED);
        // Already canonical, so it never matched the query. Asserted anyway: "left alone" and "rewritten to the same
        // value" are indistinguishable afterwards, and only one of them is idempotent.
        assertThat(status("m-upper")).isEqualTo(MembershipStatus.SUSPENDED);
    }

    @Test
    void runningItTwiceChangesNothingTheSecondTime() {
        insert("m-1", "active");
        insert("m-2", "PENDING");

        migration.migrate();
        Document afterFirst = mongoTemplate.findById("m-1", Document.class, MEMBERSHIP);
        migration.migrate();
        Document afterSecond = mongoTemplate.findById("m-1", Document.class, MEMBERSHIP);

        // It has to be idempotent for the reason 001 was: two collections, no transaction, so a partial run is a
        // state this must be resumable from. The criterion that makes it so is a value test, not a $type test —
        // an enum is stored as a string, so $type alone would rewrite every document on every run for ever.
        assertThat(afterSecond).isEqualTo(afterFirst);
        assertThat(status("m-1")).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(status("m-2")).isEqualTo(MembershipStatus.PENDING);
    }

    @Test
    void anUnrecognisedValueIsSetAsideRatherThanGuessedAt() {
        // "Gold" is not invented: quality/patient-demo-seed.json named a plan tier Abofonsa does not sell (item 17),
        // and a status nobody wrote deliberately would look much like it.
        insert("m-odd", "Gold");

        migration.migrate();

        // Unset, so the record still reads through the mapped type instead of throwing on a value the enum has no
        // constant for. Guessing would either sell a subscription or revoke one.
        assertThat(membershipRepository.findById("m-odd").orElseThrow().getStatus()).isNull();
        Document stored = mongoTemplate.findById("m-odd", Document.class, MEMBERSHIP);
        assertThat(stored.get("status")).isNull();
        assertThat(stored.getString("status_unrecognised")).isEqualTo("Gold");
    }

    @Test
    void aMembershipWithNoStatusIsLeftAlone() {
        mongoTemplate.getCollection(MEMBERSHIP).insertOne(new Document("_id", "m-none").append("patient_id", "p"));

        migration.migrate();

        Document stored = mongoTemplate.findById("m-none", Document.class, MEMBERSHIP);
        assertThat(stored.containsKey("status")).isFalse();
        assertThat(stored.containsKey("status_unrecognised")).isFalse();
    }

    private void insert(String id, String status) {
        mongoTemplate.getCollection(MEMBERSHIP).insertOne(new Document("_id", id).append("patient_id", "p").append("status", status));
    }

    private MembershipStatus status(String id) {
        return membershipRepository.findById(id).orElseThrow().getStatus();
    }
}
