package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Allergy;
import net.jojoaddison.domain.CareDelegation;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.enumeration.DelegationStatus;
import net.jojoaddison.repository.AllergyRepository;
import net.jojoaddison.repository.CareDelegationRepository;
import net.jojoaddison.repository.ProfileRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsOperations;

/**
 * The erasure itself, below HTTP.
 *
 * <p>{@link #everyPatientScopedCollectionIsInTheList} is the test this file exists for. The rest of the erasure is
 * ordinary code that either runs or does not; the failure that would go unnoticed is a seventeenth patient-scoped
 * collection added months from now and not added to {@code PATIENT_SCOPED} — nothing breaks, the erasure reports
 * success, and a patient who was told they had been forgotten has not been. Asserting the list against the domain
 * package by reflection is what turns that into a red test at the moment the collection is created.</p>
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
    private GridFsOperations gridFs;

    @BeforeEach
    void initTest() {
        profileRepository.deleteAll();
        allergyRepository.deleteAll();
        careDelegationRepository.deleteAll();
        gridFs.delete(new Query());
    }

    /**
     * The list must be exactly the set of persisted domain classes carrying a {@code patient_id} field.
     *
     * <p>Both directions matter. A missing entry leaves data behind after an erasure that reported success; a spurious
     * one names a collection that has no such field, where the delete would match every document that lacks it.</p>
     */
    @Test
    void everyPatientScopedCollectionIsInTheList() {
        Set<Class<?>> patientScopedInDomain = scanDomainForPatientScopedDocuments();
        Set<Class<?>> listed = Set.copyOf(PatientErasureService.PATIENT_SCOPED);

        assertThat(listed)
            .as(
                "PatientErasureService.PATIENT_SCOPED must name every @Document with a patient_id field. " +
                "Add the new collection to it, or a patient told they were erased will not have been."
            )
            .isEqualTo(patientScopedInDomain);
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
        // erasing one patient and emptying sixteen collections.
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

    /** Every {@code @Document} class in the domain package that declares a {@code patient_id} field. */
    private Set<Class<?>> scanDomainForPatientScopedDocuments() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(org.springframework.data.mongodb.core.mapping.Document.class));

        return scanner
            .findCandidateComponents("net.jojoaddison.domain")
            .stream()
            .map(definition -> {
                try {
                    return Class.forName(definition.getBeanClassName());
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException(e);
                }
            })
            .filter(PatientErasureServiceIT::hasPatientIdField)
            // The DeletionRequest is patient-scoped and deliberately outlives the erasure it commissions: it is the
            // evidence the erasure was asked for, authorised and carried out. Named here rather than filtered by
            // accident, so that removing it from the exception list is a decision somebody has to make on purpose.
            .filter(type -> !type.equals(net.jojoaddison.domain.DeletionRequest.class))
            .collect(Collectors.toSet());
    }

    private static boolean hasPatientIdField(Class<?> type) {
        return java.util.Arrays
            .stream(type.getDeclaredFields())
            .anyMatch(field -> {
                Field annotation = field.getAnnotation(Field.class);
                return annotation != null && "patient_id".equals(annotation.value());
            });
    }
}
