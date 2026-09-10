package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Map;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Allergy;
import net.jojoaddison.domain.CareDelegation;
import net.jojoaddison.domain.PlanVerification;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.enumeration.DelegationStatus;
import net.jojoaddison.repository.AllergyRepository;
import net.jojoaddison.repository.CareDelegationRepository;
import net.jojoaddison.repository.PlanVerificationRepository;
import net.jojoaddison.repository.ProfileRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsOperations;

/**
 * The erasure itself, below HTTP — the decisions it makes, one at a time.
 *
 * <p><b>The question of whether the sweep reaches every collection is not asked here.</b> It belongs to
 * {@link PatientErasureOutcomeIT}, which seeds one document in every {@code @Document} collection and asserts nothing
 * keyed to the patient survives anywhere. This file kept a guard that answered the same question by scanning the
 * domain package for a {@code patient_id} field and comparing that set with {@code PATIENT_SCOPED}; it was removed on
 * 2026-09-10 because <b>the predicate it discovered by was the property it guarded</b> — rename the field, take the
 * class out of the list as its own failure message instructed, and it went green over a collection that was never
 * erased again. Verified by mutation on {@code Stat}. See {@code docs/backlog.md} item 31.</p>
 *
 * <p>What is left here is the behaviour that is a decision rather than a sweep: that the delegations this person held
 * over <em>other</em> patients are revoked, that the plan-verification ledger goes with them, that a blank id is
 * refused, that re-running is safe, and that the counts handed back name what was removed.</p>
 */
@IntegrationTest
class PatientErasureServiceIT {

    private static final String PATIENT_ID = "ama-patient";
    private static final String PATIENT_EMAIL = "ama@example.test";
    private static final String OTHER_PATIENT_ID = "yaa-patient";

    @Autowired
    private PatientErasureService patientErasureService;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private AllergyRepository allergyRepository;

    @Autowired
    private CareDelegationRepository careDelegationRepository;

    @Autowired
    private PlanVerificationRepository planVerificationRepository;

    @Autowired
    private GridFsOperations gridFs;

    @BeforeEach
    void initTest() {
        profileRepository.deleteAll();
        allergyRepository.deleteAll();
        careDelegationRepository.deleteAll();
        planVerificationRepository.deleteAll();
        gridFs.delete(new Query());
    }

    /**
     * That item 19's plan-verification ledger goes with the patient it names.
     *
     * <h2>Why this exists when {@link PatientErasureOutcomeIT} sweeps every collection anyway</h2>
     *
     * <p>Because that test asserts the mechanism and this one pins a <em>decision</em>. Keeping the ledger as an audit
     * record — that an administrator approved a plan, and when — is a defensible position, and it was argued and
     * refused in {@code PatientErasureService}'s javadoc on this repository's own precedent that {@code Membership},
     * the commercial record, is erased. A decision reached that way should fail a test with its own name on it if
     * somebody quietly reverses it, rather than only turning up as one entry in a set comparison.</p>
     *
     * <p>The ledger was missed from {@code PATIENT_SCOPED} when the collection was added, and caught for one review
     * round by the scanning guard that has since been removed. What catches it now is the outcome test, which cannot
     * be talked out of the question by renaming a field.</p>
     */
    @Test
    void aPlanVerificationIsErasedWithThePatientItNames() {
        PlanVerification mine = planVerificationRepository.insert(
            new PlanVerification().id("event-mine").patientId(PATIENT_ID).membershipId("membership-1").planCode("PAWPAW")
        );
        PlanVerification theirs = planVerificationRepository.insert(
            new PlanVerification().id("event-theirs").patientId(OTHER_PATIENT_ID).membershipId("membership-2").planCode("MELON")
        );

        Map<String, Long> counts = patientErasureService.erase(PATIENT_ID, PATIENT_EMAIL);

        assertThat(planVerificationRepository.findById(mine.getId()))
            .as("a patient told they were erased still had an approved plan on record, with the date it was approved")
            .isEmpty();
        assertThat(counts).containsEntry("plan_verification", 1L);
        assertThat(planVerificationRepository.findById(theirs.getId())).as("somebody else's is untouched").isPresent();
    }

    @Test
    void itRemovesTheRecordAndReportsWhatItRemoved() {
        seed(PATIENT_ID);
        seed(OTHER_PATIENT_ID);

        Map<String, Long> counts = patientErasureService.erase(PATIENT_ID, PATIENT_EMAIL);

        assertThat(profileRepository.findByPatientId(PATIENT_ID)).isEmpty();
        assertThat(allergyRepository.findByPatientId(PATIENT_ID)).isEmpty();
        assertThat(counts).containsEntry("profile", 1L).containsEntry("allergy", 2L);

        assertThat(profileRepository.findByPatientId(OTHER_PATIENT_ID)).as("the neighbouring record is untouched").hasSize(1);
        assertThat(allergyRepository.findByPatientId(OTHER_PATIENT_ID)).hasSize(2);
    }

    @Test
    void itTakesTheReportFilesWithIt() {
        gridFs.store(
            new ByteArrayInputStream("%PDF-1.4 a scan".getBytes()),
            "results.pdf",
            "application/pdf",
            new Document(Map.of("reportId", "r1", "patientId", PATIENT_ID))
        );
        gridFs.store(
            new ByteArrayInputStream("%PDF-1.4 somebody else".getBytes()),
            "other.pdf",
            "application/pdf",
            new Document(Map.of("reportId", "r2", "patientId", OTHER_PATIENT_ID))
        );

        Map<String, Long> counts = patientErasureService.erase(PATIENT_ID, PATIENT_EMAIL);

        assertThat(counts).containsEntry("reportFiles", 1L);
        assertThat(filesFor(PATIENT_ID)).isEmpty();
        assertThat(filesFor(OTHER_PATIENT_ID)).as("a file is not orphaned by its neighbour's erasure").hasSize(1);
    }

    @Test
    void itRevokesTheDelegationsThisPersonHeldOverOthers() {
        // Keyed by angel_email rather than patient_id, so the by-patient sweep cannot see them — and PatientScope
        // reads this collection on every acting-as request, so a row left behind is access held by an account that
        // no longer exists.
        careDelegationRepository.save(
            new CareDelegation().patientId(OTHER_PATIENT_ID).angelEmail(PATIENT_EMAIL).status(DelegationStatus.ACTIVE)
        );

        Map<String, Long> counts = patientErasureService.erase(PATIENT_ID, PATIENT_EMAIL);

        assertThat(counts).containsEntry("careDelegationAsAngel", 1L);
        assertThat(careDelegationRepository.findByAngelEmailIgnoreCase(PATIENT_EMAIL)).isEmpty();
    }

    @Test
    void itRefusesToRunWithoutAPatient() {
        seed(PATIENT_ID);

        // {patient_id: null} matches every document that has no patient_id — this guard is the difference between
        // erasing one patient and emptying seventeen collections.
        assertThatThrownBy(() -> patientErasureService.erase(null, PATIENT_EMAIL)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> patientErasureService.erase("  ", PATIENT_EMAIL)).isInstanceOf(IllegalArgumentException.class);

        assertThat(profileRepository.findByPatientId(PATIENT_ID)).hasSize(1);
    }

    @Test
    void runningItTwiceIsSafe() {
        seed(PATIENT_ID);

        patientErasureService.erase(PATIENT_ID, PATIENT_EMAIL);
        Map<String, Long> second = patientErasureService.erase(PATIENT_ID, PATIENT_EMAIL);

        // The reason DeletionRequestService can leave a failed erasure PENDING and let an administrator retry it.
        assertThat(second.values()).allMatch(count -> count == 0L);
    }

    // --- fixtures -------------------------------------------------------------------------------------------------

    private void seed(String patientId) {
        Profile profile = new Profile().patientId(patientId).email(patientId + "@example.test");
        profile.setId(patientId + "-profile");
        profileRepository.save(profile);
        allergyRepository.save(new Allergy().patientId(patientId).name("penicillin"));
        allergyRepository.save(new Allergy().patientId(patientId).name("latex"));
    }

    private java.util.List<com.mongodb.client.gridfs.model.GridFSFile> filesFor(String patientId) {
        return gridFs.find(Query.query(Criteria.where("metadata.patientId").is(patientId))).into(new ArrayList<>());
    }
}
