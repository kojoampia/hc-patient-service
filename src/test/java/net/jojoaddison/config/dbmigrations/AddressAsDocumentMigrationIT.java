package net.jojoaddison.config.dbmigrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.repository.ProfileRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/**
 * The one-time reshaping of {@code Profile.address} from a string into a document.
 *
 * <p>Exercised against real documents rather than through the mapped type on purpose: the whole difficulty is that the
 * mapped type no longer describes the data being migrated, so a test that went through {@code Profile} would be
 * testing the wrong shape.</p>
 */
@IntegrationTest
class AddressAsDocumentMigrationIT {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ProfileRepository profileRepository;

    private AddressAsDocumentMigration migration;

    @BeforeEach
    void initTest() {
        mongoTemplate.remove(new Query(), "profile");
        mongoTemplate.remove(new Query(), "address");
        migration = new AddressAsDocumentMigration(mongoTemplate);
    }

    @Test
    void aFreeTextAddressBecomesADocumentTheProfilePointsAt() {
        mongoTemplate
            .getCollection("profile")
            .insertOne(
                new Document("_id", "legacy-1")
                    .append("patient_id", "patient-1")
                    .append("email", "ama@example.test")
                    .append("address", "5 Ankobra River Street")
            );

        migration.migrate();

        Profile migrated = profileRepository.findById("legacy-1").orElseThrow();
        assertThat(migrated.getAddress()).as("the reference resolves").isNotNull();
        assertThat(migrated.getAddress().getStreetAddress()).isEqualTo("5 Ankobra River Street");
        // Owned, or PatientScope would hide the patient's own address from them — an unowned record is visible to
        // nobody.
        assertThat(migrated.getAddress().getPatientId()).isEqualTo("patient-1");
    }

    @Test
    void aProfileWrittenBeforePatientIdExistedFallsBackToItsOwnId() {
        mongoTemplate.getCollection("profile").insertOne(new Document("_id", "legacy-2").append("address", "12 Independence Ave"));

        migration.migrate();

        assertThat(profileRepository.findById("legacy-2").orElseThrow().getAddress().getPatientId()).isEqualTo("legacy-2");
    }

    @Test
    void anEmptyAddressLeavesNoDocumentBehind() {
        mongoTemplate
            .getCollection("profile")
            .insertOne(new Document("_id", "legacy-3").append("patient_id", "p3").append("address", "  "));

        migration.migrate();

        assertThat(mongoTemplate.findAll(Document.class, "address")).isEmpty();
        // Cleared rather than left as a String, so no read has to keep coping with the old shape.
        assertThat(mongoTemplate.findById("legacy-3", Document.class, "profile").get("address")).isNull();
    }

    @Test
    void runningItTwiceChangesNothingTheSecondTime() {
        mongoTemplate
            .getCollection("profile")
            .insertOne(new Document("_id", "legacy-4").append("patient_id", "p4").append("address", "9 Ring Road"));

        migration.migrate();
        List<Document> afterFirst = mongoTemplate.findAll(Document.class, "address");
        migration.migrate();

        // Idempotent because the query only matches an address that is still a string. It has to be: two writes with
        // no transaction between them means a partial run is a state this has to be safe to resume from.
        assertThat(mongoTemplate.findAll(Document.class, "address")).hasSize(afterFirst.size()).hasSize(1);
    }

    @Test
    void aProfileThatAlreadyHasADocumentIsLeftAlone() {
        Profile modern = profileRepository.save(new Profile().patientId("p5").email("kofi@example.test"));

        migration.migrate();

        assertThat(mongoTemplate.findAll(Document.class, "address")).isEmpty();
        assertThat(profileRepository.findById(modern.getId()).orElseThrow().getAddress()).isNull();
    }
}
