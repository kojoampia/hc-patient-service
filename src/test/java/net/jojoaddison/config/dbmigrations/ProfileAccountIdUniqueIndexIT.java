package net.jojoaddison.config.dbmigrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.repository.ProfileRepository;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * The partial unique index on {@code account_id} — change unit {@code 003.5}.
 *
 * <h2>What each group of tests is for</h2>
 *
 * <p><strong>The premise.</strong> The index is partial on <code>{account_id: {$exists: true}}</code>, and that is
 * only correct if the writers <em>omit</em> the field rather than storing an explicit null — otherwise every unlinked
 * profile would share the key {@code null} and the second one ever written would be refused. That is asserted here
 * against the raw documents rather than assumed from what Spring Data is believed to do.</p>
 *
 * <p><strong>The positive control.</strong> An index that exists and refuses nothing is indistinguishable from no
 * index at all, and this repository has a catalogue of guards that reported success without having been applied. So a
 * genuine second link to an already-held account id is attempted, and watched being refused.</p>
 *
 * <p><strong>The failure this change unit must not swallow.</strong> A unit that tries to create a unique index, hits
 * a conflict and carries on leaves the collection unindexed while reporting success — the same shape one layer up.
 * The last test plants a duplicate, runs the unit, and asserts both halves: that it throws naming the offending
 * account id, and that <em>no index was created</em>.</p>
 */
@IntegrationTest
class ProfileAccountIdUniqueIndexIT {

    private static final String PROFILE = "profile";

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ProfileRepository profileRepository;

    private ProfileAccountIdUniqueIndex indexUnit;

    @BeforeEach
    void initTest() {
        profileRepository.deleteAll();
        indexUnit = new ProfileAccountIdUniqueIndex(mongoTemplate);
    }

    @AfterEach
    void leaveTheCollectionIndexedForWhoeverRunsNext() {
        // The Mongo container is shared by every integration class in this JVM, and one test below deliberately drops
        // the index. Put it back rather than relying on the next context's startup to notice.
        profileRepository.deleteAll();
        indexUnit.migrate();
    }

    // --- it is really there, created by the running application ---------------------------------------------------

    @Test
    void theApplicationCreatedItAtStartupWithoutThisTestAskingItTo() {
        // Nothing in initTest creates the index: this is change unit 003.5 having run through Mongock when the
        // context came up, which is also what pins that its dependencies resolve and that it runs before 004.
        assertThat(indexNames()).contains(ProfileAccountIdUniqueIndex.INDEX_NAME);
    }

    @Test
    void itIsUniqueAndPartialOnAccountId() {
        IndexInfo index = indexNamed(ProfileAccountIdUniqueIndex.INDEX_NAME).orElseThrow();

        assertThat(index.isUnique()).as("a non-unique index would refuse nothing").isTrue();
        assertThat(index.getPartialFilterExpression()).contains("account_id").contains("$exists");
        assertThat(index.getIndexFields()).singleElement().satisfies(field -> assertThat(field.getKey()).isEqualTo("account_id"));
    }

    // --- the premise the partial filter rests on ------------------------------------------------------------------

    @Test
    void aProfileWithNoAccountIdHasNoSuchKeyRatherThanANullOne() {
        profileRepository.save(new Profile().patientId("ama").email("ama@example.test").firstName("Ama"));

        assertThat(raw()).singleElement().satisfies(document -> assertThat(document).doesNotContainKey("account_id"));
    }

    @Test
    void clearingAnAccountIdRemovesTheKeyRatherThanNullingIt() {
        // The replace path, which is the one that could plausibly differ from the insert path: PUT /api/profiles/{id}
        // goes through save() on a document that already exists.
        Profile linked = profileRepository.save(new Profile().patientId("ama").accountId("u-1").firstName("Ama"));
        assertThat(raw()).singleElement().satisfies(document -> assertThat(document).containsEntry("account_id", "u-1"));

        linked.setAccountId(null);
        profileRepository.save(linked);

        assertThat(raw()).singleElement().satisfies(document -> assertThat(document).doesNotContainKey("account_id"));
    }

    @Test
    void anyNumberOfUnlinkedProfilesCoexistWhichIsWhatTheFilterBuys() {
        // Under a plain unique index the second of these would be refused, because they would all share the key null.
        // This is the test that fails if the partial filter is ever dropped.
        assertThatCode(() -> {
                profileRepository.save(new Profile().patientId("a").firstName("A"));
                profileRepository.save(new Profile().patientId("b").firstName("B"));
                profileRepository.save(new Profile().patientId("c").firstName("C"));
            })
            .doesNotThrowAnyException();

        assertThat(profileRepository.count()).isEqualTo(3);
    }

    // --- the positive control -------------------------------------------------------------------------------------

    @Test
    void aSecondProfileCannotTakeAnAccountIdAnotherAlreadyHolds() {
        profileRepository.save(new Profile().patientId("ama").accountId("u-shared").firstName("Ama"));

        assertThatThrownBy(() -> profileRepository.save(new Profile().patientId("kofi").accountId("u-shared").firstName("Kofi")))
            .isInstanceOf(DuplicateKeyException.class);

        // And the refusal was of the write, not of the reader: the first profile still holds it and is still found.
        assertThat(profileRepository.findOneByAccountId("u-shared")).map(Profile::getPatientId).contains("ama");
        assertThat(profileRepository.count()).isEqualTo(1);
    }

    @Test
    void aProfileMayBeSavedAgainWithTheAccountIdItAlreadyHolds() {
        // The carry-over ProfileResource.updateProfile does writes the stored value back on the same document. A
        // unique index must not refuse that, or every edit of a linked profile would fail.
        Profile linked = profileRepository.save(new Profile().patientId("ama").accountId("u-1").firstName("Ama"));

        linked.setFirstName("Ama Serwaa");
        assertThatCode(() -> profileRepository.save(linked)).doesNotThrowAnyException();

        assertThat(profileRepository.findOneByAccountId("u-1")).map(Profile::getFirstName).contains("Ama Serwaa");
    }

    // --- creating it over a collection that already holds a duplicate ---------------------------------------------

    @Test
    void overAnExistingDuplicateItFailsLoudlyAndLeavesNoIndexBehind() {
        mongoTemplate.indexOps(PROFILE).dropIndex(ProfileAccountIdUniqueIndex.INDEX_NAME);
        assertThat(indexNames()).as("the drop is the precondition, not the test").doesNotContain(ProfileAccountIdUniqueIndex.INDEX_NAME);

        // Written as raw documents, which is the only way to reach this state at all now: the mapped writers cannot
        // produce it while the index is up, which is the whole point of the index.
        mongoTemplate.insert(new Document("patient_id", "ama").append("account_id", "u-dup"), PROFILE);
        mongoTemplate.insert(new Document("patient_id", "kofi").append("account_id", "u-dup"), PROFILE);
        mongoTemplate.insert(new Document("patient_id", "esi").append("account_id", "u-fine"), PROFILE);

        assertThatThrownBy(() -> indexUnit.migrate())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("account_id is not unique")
            // Naming the offender is what makes the refusal actionable; "1 account id" plus the value is the whole
            // instruction an operator needs.
            .hasMessageContaining("u-dup")
            .hasMessageNotContaining("u-fine");

        // The half that matters most: it did NOT report success having left the collection unindexed.
        assertThat(indexNames()).doesNotContain(ProfileAccountIdUniqueIndex.INDEX_NAME);
        assertThat(indexUnit.duplicateAccountIds()).containsExactly("u-dup");
    }

    @Test
    void onceTheDuplicateIsResolvedTheNextRunCreatesIt() {
        // The recovery, which is what makes the refusal above survivable: this unit runs on every start, so an
        // operator fixes the data and restarts rather than editing mongockChangeLog.
        mongoTemplate.indexOps(PROFILE).dropIndex(ProfileAccountIdUniqueIndex.INDEX_NAME);
        mongoTemplate.insert(new Document("patient_id", "ama").append("account_id", "u-dup"), PROFILE);
        mongoTemplate.insert(new Document("patient_id", "kofi").append("account_id", "u-dup"), PROFILE);
        assertThatThrownBy(() -> indexUnit.migrate()).isInstanceOf(IllegalStateException.class);

        mongoTemplate.remove(Query.query(Criteria.where("patient_id").is("kofi")), PROFILE);

        assertThatCode(() -> indexUnit.migrate()).doesNotThrowAnyException();
        assertThat(indexNames()).contains(ProfileAccountIdUniqueIndex.INDEX_NAME);
    }

    @Test
    void runningItAgainOverAHealthyCollectionIsANoOp() {
        assertThatCode(() -> {
                indexUnit.migrate();
                indexUnit.migrate();
            })
            .doesNotThrowAnyException();

        assertThat(indexNames().stream().filter(ProfileAccountIdUniqueIndex.INDEX_NAME::equals)).hasSize(1);
    }

    // --- fixture --------------------------------------------------------------------------------------------------

    private List<Document> raw() {
        return mongoTemplate.findAll(Document.class, PROFILE);
    }

    private List<String> indexNames() {
        return mongoTemplate.indexOps(PROFILE).getIndexInfo().stream().map(IndexInfo::getName).toList();
    }

    private Optional<IndexInfo> indexNamed(String name) {
        return mongoTemplate.indexOps(PROFILE).getIndexInfo().stream().filter(info -> name.equals(info.getName())).findFirst();
    }
}
