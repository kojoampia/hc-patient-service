package net.jojoaddison.config.dbmigrations;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.jojoaddison.domain.ActivityLog;
import net.jojoaddison.domain.Address;
import net.jojoaddison.domain.Allergy;
import net.jojoaddison.domain.CareDelegation;
import net.jojoaddison.domain.CarePlanItem;
import net.jojoaddison.domain.ClinicalCase;
import net.jojoaddison.domain.Condition;
import net.jojoaddison.domain.DutyRoster;
import net.jojoaddison.domain.Emergency;
import net.jojoaddison.domain.Medication;
import net.jojoaddison.domain.Membership;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Recommendation;
import net.jojoaddison.domain.Report;
import net.jojoaddison.domain.Shift;
import net.jojoaddison.domain.Stat;
import net.jojoaddison.domain.Task;
import net.jojoaddison.domain.Visitation;
import net.jojoaddison.repository.ActivityLogRepository;
import net.jojoaddison.repository.AddressRepository;
import net.jojoaddison.repository.AllergyRepository;
import net.jojoaddison.repository.CareDelegationRepository;
import net.jojoaddison.repository.CarePlanItemRepository;
import net.jojoaddison.repository.ClinicalCaseRepository;
import net.jojoaddison.repository.ConditionRepository;
import net.jojoaddison.repository.DutyRosterRepository;
import net.jojoaddison.repository.EmergencyRepository;
import net.jojoaddison.repository.MedicationRepository;
import net.jojoaddison.repository.MembershipRepository;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.repository.RecommendationRepository;
import net.jojoaddison.repository.ReportRepository;
import net.jojoaddison.repository.ShiftRepository;
import net.jojoaddison.repository.StatRepository;
import net.jojoaddison.repository.TaskRepository;
import net.jojoaddison.repository.VisitationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Component;
import tech.jhipster.config.JHipsterConstants;
import tools.jackson.databind.ObjectMapper;

/**
 * Seeds a patient record from a seed document supplied from outside the image.
 *
 * <p>Shaped after {@code hc-admin}'s initializer of the same name: the document is keyed by Spring profile at the
 * root, and each profile holds plain arrays of domain objects, one per collection. Jackson deserializes straight into
 * the domain classes, so a field this service does not have is a field the document cannot set — the mapping is the
 * domain, not a translation layer that can drift from it.</p>
 *
 * <p><strong>Nothing happens unless {@code hc.seed.location} names a document</strong>, and it is unset in this
 * repository. The stack that sets it is {@code hc-patient-quality}, which mounts
 * {@code quality/patient-demo-seed.json} into the container and points this at it — the file is extracted from
 * {@code hc-patient-dashboard}'s {@code patient-web-demo.html}, so what the quality stack shows is what the mockup
 * shows. The location is a Spring resource string, so {@code classpath:} works as well as {@code file:}.</p>
 *
 * <p><strong>Runs under {@code dev} and {@code test} only</strong>, like everything else that seeds people. The
 * accounts these records belong to are seeded by the gateway's {@code DevSeedDataInitializer} from the same document,
 * and the two meet on {@code <login>@localhost}.</p>
 *
 * <p>Two deliberate departures from the {@code hc-admin} original, both because this service already promises
 * something different:</p>
 *
 * <ul>
 *   <li><strong>Every active profile's block is applied</strong>, {@code dev} then {@code test}, rather than only the
 *       most specific one. The quality stack runs with {@code dev,test} active; on the original's rule that
 *       {@code test} wins, a document that carries its record under {@code dev} would seed nothing at all, and the
 *       symptom is an empty dashboard with a healthy stack behind it.</li>
 *   <li><strong>Seeding is additive</strong> — a record whose id is already stored is left exactly as it is, rather
 *       than saved over. {@link DemoDataInitializer} makes the same promise, {@code DemoDataInitializerIT} holds it to
 *       it, and the quality stack's CI restarts over an existing volume specifically to falsify it. Edits made through
 *       the API survive a restart; to take a fresh copy, drop the documents.</li>
 * </ul>
 */
@Component
// Fully qualified: this class also names the Profile *entity* repository, and Spring's @Profile would collide.
@org.springframework.context.annotation.Profile({ JHipsterConstants.SPRING_PROFILE_DEVELOPMENT, JHipsterConstants.SPRING_PROFILE_TEST })
public class DevelopmentDataInitializer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DevelopmentDataInitializer.class);

    private final String location;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final Environment environment;

    private final ProfessionalRepository professionalRepository;
    private final DutyRosterRepository dutyRosterRepository;
    private final ShiftRepository shiftRepository;
    private final RecommendationRepository recommendationRepository;
    private final ProfileRepository profileRepository;
    private final AddressRepository addressRepository;
    private final MembershipRepository membershipRepository;
    private final CareDelegationRepository careDelegationRepository;
    private final ConditionRepository conditionRepository;
    private final AllergyRepository allergyRepository;
    private final CarePlanItemRepository carePlanItemRepository;
    private final StatRepository statRepository;
    private final ClinicalCaseRepository clinicalCaseRepository;
    private final VisitationRepository visitationRepository;
    private final ActivityLogRepository activityLogRepository;
    private final MedicationRepository medicationRepository;
    private final ReportRepository reportRepository;
    private final EmergencyRepository emergencyRepository;
    private final TaskRepository taskRepository;

    public DevelopmentDataInitializer(
        @Value("${hc.seed.location:}") String location,
        ObjectMapper objectMapper,
        ResourceLoader resourceLoader,
        Environment environment,
        ProfessionalRepository professionalRepository,
        DutyRosterRepository dutyRosterRepository,
        ShiftRepository shiftRepository,
        RecommendationRepository recommendationRepository,
        ProfileRepository profileRepository,
        AddressRepository addressRepository,
        MembershipRepository membershipRepository,
        CareDelegationRepository careDelegationRepository,
        ConditionRepository conditionRepository,
        AllergyRepository allergyRepository,
        CarePlanItemRepository carePlanItemRepository,
        StatRepository statRepository,
        ClinicalCaseRepository clinicalCaseRepository,
        VisitationRepository visitationRepository,
        ActivityLogRepository activityLogRepository,
        MedicationRepository medicationRepository,
        ReportRepository reportRepository,
        EmergencyRepository emergencyRepository,
        TaskRepository taskRepository
    ) {
        this.location = location;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.environment = environment;
        this.professionalRepository = professionalRepository;
        this.dutyRosterRepository = dutyRosterRepository;
        this.shiftRepository = shiftRepository;
        this.recommendationRepository = recommendationRepository;
        this.profileRepository = profileRepository;
        this.addressRepository = addressRepository;
        this.membershipRepository = membershipRepository;
        this.careDelegationRepository = careDelegationRepository;
        this.conditionRepository = conditionRepository;
        this.allergyRepository = allergyRepository;
        this.carePlanItemRepository = carePlanItemRepository;
        this.statRepository = statRepository;
        this.clinicalCaseRepository = clinicalCaseRepository;
        this.visitationRepository = visitationRepository;
        this.activityLogRepository = activityLogRepository;
        this.medicationRepository = medicationRepository;
        this.reportRepository = reportRepository;
        this.emergencyRepository = emergencyRepository;
        this.taskRepository = taskRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (location == null || location.isBlank()) {
            LOG.debug("hc.seed.location is not set; no external seed data will be loaded");
            return;
        }

        SeedDocument document = read();
        if (document == null) {
            return;
        }

        // dev first, then test, so a document that puts its record under one and its edge cases under the other
        // arrives in that order. Both are applied when both are active — see the class javadoc.
        if (environment.acceptsProfiles(Profiles.of(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT))) {
            seed(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT, document.dev);
        }
        if (environment.acceptsProfiles(Profiles.of(JHipsterConstants.SPRING_PROFILE_TEST))) {
            seed(JHipsterConstants.SPRING_PROFILE_TEST, document.test);
        }
    }

    /**
     * @return the parsed document, or {@code null} when it is missing or unreadable — which is logged and otherwise
     *     ignored, because seed data is a development convenience and a broken file must not stop the application from
     *     starting.
     */
    private SeedDocument read() {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            LOG.warn("hc.seed.location points at {}, which does not exist; no seed data will be loaded", location);
            return null;
        }
        try (InputStream source = resource.getInputStream()) {
            return objectMapper.readValue(source, SeedDocument.class);
        } catch (IOException | RuntimeException e) {
            LOG.error("Failed to read seed data from {}; no seed data will be loaded", location, e);
            return null;
        }
    }

    private void seed(String profile, ProfileData data) {
        if (data == null) {
            LOG.warn("The seed document carries no '{}' block", profile);
            return;
        }
        LOG.info("Seeding the '{}' block of {}", profile, location);

        // Staff and reference data first. Recommendations in particular: a ClinicalCase holds them by @DBRef, so the
        // documents have to exist before a case referring to one is written.
        save("professionals", professionalRepository, data.professionals, Professional::getId);
        save("dutyRosters", dutyRosterRepository, data.dutyRosters, DutyRoster::getId);
        save("shifts", shiftRepository, data.shifts, Shift::getId);
        save("recommendations", recommendationRepository, data.recommendations, Recommendation::getId);

        // Then the patient, and then everything that refers to one.
        save("profiles", profileRepository, data.profiles, net.jojoaddison.domain.Profile::getId);
        save("addresses", addressRepository, data.addresses, Address::getId);
        save("memberships", membershipRepository, data.memberships, Membership::getId);
        // Delegations after profiles: a delegation names a patientId, and seeding one for a patient who is not there
        // yet would leave a row that grants access to nothing.
        //
        // Note what is NOT done here: a profile's careAngelName/careAngelPhone are a *contact*, and are never turned
        // into a delegation. Converting them would grant somebody standing access to a medical record that nobody
        // consented to — the seed has carried such a contact since long before delegation existed.
        save("careDelegations", careDelegationRepository, data.careDelegations, CareDelegation::getId);
        save("conditions", conditionRepository, data.conditions, Condition::getId);
        save("allergies", allergyRepository, data.allergies, Allergy::getId);
        save("carePlanItems", carePlanItemRepository, data.carePlanItems, CarePlanItem::getId);
        save("stats", statRepository, data.stats, Stat::getId);
        save("clinicalCases", clinicalCaseRepository, data.clinicalCases, ClinicalCase::getId);
        save("visitations", visitationRepository, data.visitations, Visitation::getId);
        save("activityLogs", activityLogRepository, data.activityLogs, ActivityLog::getId);
        save("medications", medicationRepository, data.medications, Medication::getId);
        save("reports", reportRepository, data.reports, Report::getId);
        save("emergencies", emergencyRepository, data.emergencies, Emergency::getId);
        save("tasks", taskRepository, data.tasks, Task::getId);
    }

    /**
     * Writes the records of one collection that are not already stored.
     *
     * @param collection the collection's name in the seed document, for logging.
     * @param repository the repository to write through.
     * @param records the records as read from the document.
     * @param id reads a record's id. A record without one is skipped: the id is what makes this repeatable.
     * @param <T> the entity type.
     */
    private <T> void save(String collection, MongoRepository<T, String> repository, List<T> records, Function<T, String> id) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<T> missing = new ArrayList<>();
        for (T record : records) {
            String recordId = id.apply(record);
            if (recordId == null || recordId.isBlank()) {
                LOG.warn("Skipping a {} record with no id", collection);
                continue;
            }
            if (!repository.existsById(recordId)) {
                missing.add(record);
            }
        }
        if (missing.isEmpty()) {
            LOG.debug("All {} {} record(s) are already present", records.size(), collection);
            return;
        }
        repository.saveAll(missing);
        LOG.info("Seeded {} of {} {} record(s)", missing.size(), records.size(), collection);
    }

    /** Root of the seed document: one block per profile. Must stay {@code static} — Jackson cannot instantiate a
     * non-static inner class. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SeedDocument {

        public ProfileData dev;
        public ProfileData test;
    }

    /**
     * One profile's worth of seed data.
     *
     * <p>Public fields rather than the usual accessor pairs: there are eighteen collections here and nothing to do on
     * the way in or out of any of them, so getters would be a hundred and fifty lines that say nothing. Unknown keys
     * are ignored, which is what lets one document serve both services — the gateway's {@code users} block lands here
     * as a key this class does not have, and it is skipped rather than fatal.</p>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ProfileData {

        public List<Professional> professionals = new ArrayList<>();
        public List<DutyRoster> dutyRosters = new ArrayList<>();
        public List<Shift> shifts = new ArrayList<>();
        public List<Recommendation> recommendations = new ArrayList<>();
        // Fully qualified: Spring's @Profile is on this file's class, and the domain type would shadow it.
        public List<net.jojoaddison.domain.Profile> profiles = new ArrayList<>();
        public List<Address> addresses = new ArrayList<>();
        public List<Membership> memberships = new ArrayList<>();
        public List<CareDelegation> careDelegations = new ArrayList<>();
        public List<Condition> conditions = new ArrayList<>();
        public List<Allergy> allergies = new ArrayList<>();
        public List<CarePlanItem> carePlanItems = new ArrayList<>();
        public List<Stat> stats = new ArrayList<>();
        public List<ClinicalCase> clinicalCases = new ArrayList<>();
        public List<Visitation> visitations = new ArrayList<>();
        public List<ActivityLog> activityLogs = new ArrayList<>();
        public List<Medication> medications = new ArrayList<>();
        public List<Report> reports = new ArrayList<>();
        public List<Emergency> emergencies = new ArrayList<>();
        public List<Task> tasks = new ArrayList<>();
    }
}
