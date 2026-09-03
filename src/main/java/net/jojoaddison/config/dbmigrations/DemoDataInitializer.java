package net.jojoaddison.config.dbmigrations;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import net.jojoaddison.domain.ActivityLog;
import net.jojoaddison.domain.ClinicalCase;
import net.jojoaddison.domain.Medication;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.Recommendation;
import net.jojoaddison.domain.Report;
import net.jojoaddison.domain.Visitation;
import net.jojoaddison.domain.enumeration.CaseStatus;
import net.jojoaddison.repository.ActivityLogRepository;
import net.jojoaddison.repository.ClinicalCaseRepository;
import net.jojoaddison.repository.MedicationRepository;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.repository.RecommendationRepository;
import net.jojoaddison.repository.ReportRepository;
import net.jojoaddison.repository.VisitationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import tech.jhipster.config.JHipsterConstants;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Seeds the professional-dashboard demo dataset — one clinician, their duty roster, and seven patients with cases,
 * visits, activity, medication and reports.
 *
 * <p><strong>Runs under {@code dev} and {@code test} only.</strong> These are invented people with invented clinical
 * histories, and a real deployment must never carry them; the profile gate is the whole point of this class. It is
 * the same arrangement as {@link net.jojoaddison.config.dbmigrations} on the gateway side, where
 * {@code DevSeedDataInitializer} seeds the matching development accounts — including {@code doctor}, which is the
 * login behind the {@code professional-doctor} seeded here.</p>
 *
 * <p>An {@link ApplicationRunner} rather than a Mongock change unit for two reasons. A change unit has no notion of a
 * Spring profile, so it cannot be restricted to development — seeding accounts in one is exactly how the gateway came
 * to ship publicly known credentials to production. And Mongock records a change unit as executed and never runs it
 * again, so a developer who cleared a collection could not get the demo data back without editing the changelog.</p>
 *
 * <p>Seeding is <strong>additive and idempotent</strong>: every record carries a fixed id from the demo file and is
 * written only when no document with that id exists, so edits made through the API survive a restart. Nothing is ever
 * deleted or overwritten. To take a fresh copy of the data, drop the documents and restart.</p>
 *
 * <p>Two fields in the demo file have no home in the domain and are deliberately not seeded: a patient's
 * {@code lastActivityAt}, which {@link ActivityLog} is the real source of, and {@code isChild}, which is derivable
 * from {@code dateOfBirth}. Nothing is invented to fill a gap — a case with no {@code caseNumber} in the file is
 * stored without one.</p>
 */
@Component
// Fully qualified: this class also uses the Profile *entity*, and the two would collide on import.
@org.springframework.context.annotation.Profile({ JHipsterConstants.SPRING_PROFILE_DEVELOPMENT, JHipsterConstants.SPRING_PROFILE_TEST })
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DemoDataInitializer.class);

    private static final String DEMO_DATA = "config/demo-data/professional-dashboard-demo-data.json";

    private final String externalSeedLocation;
    private final ObjectMapper objectMapper;
    private final ProfessionalRepository professionalRepository;
    private final RecommendationRepository recommendationRepository;
    private final ProfileRepository profileRepository;
    private final ClinicalCaseRepository clinicalCaseRepository;
    private final VisitationRepository visitationRepository;
    private final ActivityLogRepository activityLogRepository;
    private final MedicationRepository medicationRepository;
    private final ReportRepository reportRepository;

    public DemoDataInitializer(
        @Value("${hc.seed.location:}") String externalSeedLocation,
        ObjectMapper objectMapper,
        ProfessionalRepository professionalRepository,
        RecommendationRepository recommendationRepository,
        ProfileRepository profileRepository,
        ClinicalCaseRepository clinicalCaseRepository,
        VisitationRepository visitationRepository,
        ActivityLogRepository activityLogRepository,
        MedicationRepository medicationRepository,
        ReportRepository reportRepository
    ) {
        this.externalSeedLocation = externalSeedLocation;
        this.objectMapper = objectMapper;
        this.professionalRepository = professionalRepository;
        this.recommendationRepository = recommendationRepository;
        this.profileRepository = profileRepository;
        this.clinicalCaseRepository = clinicalCaseRepository;
        this.visitationRepository = visitationRepository;
        this.activityLogRepository = activityLogRepository;
        this.medicationRepository = medicationRepository;
        this.reportRepository = reportRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        // A seed document supplied from outside the image replaces this one rather than joining it.
        // {@link DevelopmentDataInitializer} reads that document, and the two datasets describe the same subsystem
        // with different people in it: run both and the dashboard lists this file's seven invented patients
        // alongside the record the other one seeds, which reads as a bug in the data rather than a choice about it.
        if (externalSeedLocation != null && !externalSeedLocation.isBlank()) {
            LOG.info("hc.seed.location is set, so the built-in demo dataset stands down in favour of it");
            return;
        }

        JsonNode root = read();
        if (root == null) {
            return;
        }

        // Recommendations first: a ClinicalCase holds them by @DBRef, so they have to exist before a case can
        // reference one.
        DemoData.array(root, "recommendations").forEach(this::seedRecommendation);
        DemoData.array(root, "professionals").forEach(this::seedProfessional);
        DemoData.array(root, "patientRecords").forEach(this::seedPatientRecord);

        LOG.debug("Professional dashboard demo data is present");
    }

    /**
     * Reads the demo document from the classpath.
     *
     * @return the parsed document, or {@code null} when it is missing or unreadable — which is logged and otherwise
     *     ignored, because a broken demo file must not stop a developer's application from starting.
     */
    private JsonNode read() {
        try (InputStream source = new ClassPathResource(DEMO_DATA).getInputStream()) {
            return objectMapper.readTree(source);
        } catch (IOException | RuntimeException e) {
            LOG.warn("Could not read the demo dataset at {}; no demo data will be seeded", DEMO_DATA, e);
            return null;
        }
    }

    private void seedRecommendation(JsonNode node) {
        saveIfMissing(
            recommendationRepository,
            DemoData.text(node, "id"),
            id -> new Recommendation().id(id).label(DemoData.text(node, "label")).category(DemoData.text(node, "category"))
        );
    }

    private void seedProfessional(JsonNode node) {
        String displayName = DemoData.text(node, "name");
        String[] name = DemoData.splitName(displayName);
        String login = DemoData.text(node, "accountLogin");
        saveIfMissing(
            professionalRepository,
            DemoData.text(node, "id"),
            id ->
                new Professional()
                    .id(id)
                    .firstName(name[0])
                    .lastName(name[1])
                    .role(DemoData.text(node, "role"))
                    .initials(DemoData.initials(displayName))
                    // The demo file identifies a professional by the login they sign in with, which Professional has no
                    // field for. The gateway seeds that account as <login>@localhost, so the email is the join.
                    .email(login == null ? null : login + "@localhost")
        );
    }

    private void seedPatientRecord(JsonNode record) {
        JsonNode patient = record.get("patient");
        if (patient == null) {
            return;
        }
        String patientId = DemoData.text(patient, "id");
        seedProfile(patient, patientId);

        DemoData.array(record, "cases").forEach(node -> seedCase(node, patientId));
        DemoData.array(record, "visitations").forEach(node -> seedVisitation(node, patientId));
        DemoData.array(record, "activities").forEach(node -> seedActivity(node, patientId));
        DemoData.array(record, "medications").forEach(node -> seedMedication(node, patientId));
        DemoData.array(record, "reports").forEach(node -> seedReport(node, patientId));
    }

    private void seedProfile(JsonNode patient, String patientId) {
        String[] name = DemoData.splitName(DemoData.text(patient, "patientName"));
        JsonNode emergencyContact = patient.get("emergencyContact");
        saveIfMissing(
            profileRepository,
            patientId,
            id -> {
                Profile profile = new Profile()
                    .id(id)
                    // patientId is the id: everything else in the domain refers to a patient by it, and the demo file
                    // uses the profile's own id as that reference.
                    .patientId(id)
                    .firstName(name[0])
                    .lastName(name[1])
                    .birthDate(DemoData.date(patient, "dateOfBirth"))
                    .sex(DemoData.text(patient, "sex"))
                    .mobilePhone(DemoData.text(patient, "phone"))
                    .email(DemoData.text(patient, "email"));
                if (emergencyContact != null) {
                    profile.careAngelName(DemoData.text(emergencyContact, "name")).careAngelPhone(DemoData.text(emergencyContact, "phone"));
                }
                return profile;
            }
        );
    }

    private void seedCase(JsonNode node, String patientId) {
        Set<String> recommendationIds = DemoData.stringSet(node, "recommendationIds");
        saveIfMissing(
            clinicalCaseRepository,
            DemoData.text(node, "id"),
            id -> {
                ClinicalCase clinicalCase = new ClinicalCase()
                    .id(id)
                    .patientId(patientId)
                    .openedAt(DemoData.instant(node, "openedAt"))
                    .brief(DemoData.text(node, "brief"))
                    .status(caseStatus(DemoData.text(node, "status")))
                    .symptoms(blankToNull(DemoData.text(node, "symptoms")))
                    .diagnosis(blankToNull(DemoData.text(node, "diagnosis")))
                    .assignedProfessionalId(DemoData.text(node, "assignedProfessionalId"))
                    .assignedRosterId(DemoData.text(node, "assignedRosterId"));
                recommendationIds.forEach(recommendationId ->
                    recommendationRepository.findById(recommendationId).ifPresent(clinicalCase::addRecommendation)
                );
                return clinicalCase;
            }
        );
    }

    private void seedVisitation(JsonNode node, String patientId) {
        saveIfMissing(
            visitationRepository,
            DemoData.text(node, "id"),
            id ->
                new Visitation()
                    .id(id)
                    .patientId(patientId)
                    .visitedAt(DemoData.instant(node, "occurredAt"))
                    .purpose(DemoData.text(node, "label"))
        );
    }

    private void seedActivity(JsonNode node, String patientId) {
        saveIfMissing(
            activityLogRepository,
            DemoData.text(node, "id"),
            id ->
                new ActivityLog()
                    .id(id)
                    .patientId(patientId)
                    .loggedAt(DemoData.instant(node, "occurredAt"))
                    .summary(DemoData.text(node, "title"))
                    .detail(DemoData.text(node, "description"))
                    .createdDate(DemoData.date(node, "createdAt"))
        );
    }

    private void seedMedication(JsonNode node, String patientId) {
        saveIfMissing(
            medicationRepository,
            DemoData.text(node, "id"),
            id ->
                new Medication().id(id).patientId(patientId).name(DemoData.text(node, "label")).startedOn(DemoData.date(node, "occurredAt"))
        );
    }

    private void seedReport(JsonNode node, String patientId) {
        saveIfMissing(
            reportRepository,
            DemoData.text(node, "id"),
            id ->
                new Report()
                    .id(id)
                    .patientId(patientId)
                    .name(DemoData.text(node, "label"))
                    .category(DemoData.text(node, "reportType"))
                    .reportDate(DemoData.date(node, "occurredAt"))
        );
    }

    /**
     * Writes one record unless a document with that id is already stored.
     *
     * @param repository the repository to write through.
     * @param id the fixed id from the demo file; a record without one is skipped.
     * @param build builds the entity, given its id. Called only when the record is actually missing.
     * @param <T> the entity type.
     */
    private <T> void saveIfMissing(CrudRepository<T, String> repository, String id, Function<String, T> build) {
        if (id == null || id.isBlank()) {
            LOG.warn("Skipping a demo record with no id");
            return;
        }
        if (repository.existsById(id)) {
            return;
        }
        repository.save(build.apply(id));
        LOG.debug("Seeded demo record {}", id);
    }

    /**
     * Maps the demo file's lower-case case status onto {@link CaseStatus}.
     *
     * @param status the status as written in the demo file.
     * @return the enum constant, or {@code null} when it is absent or unrecognised.
     */
    private static CaseStatus caseStatus(String status) {
        return parse(status, CaseStatus::valueOf);
    }

    private static <E> E parse(String status, Function<String, E> valueOf) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return valueOf.apply(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            LOG.warn("Demo data carries the unrecognised status '{}'; leaving it unset", status);
            return null;
        }
    }

    /**
     * @param value a text value from the demo file.
     * @return {@code null} when the value is blank, so an empty string in the file does not become an empty string in
     *     the database, which the portal would render as a present-but-empty field.
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
