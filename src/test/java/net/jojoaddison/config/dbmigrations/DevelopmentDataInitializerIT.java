package net.jojoaddison.config.dbmigrations;

import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.CareDelegation;
import net.jojoaddison.domain.ClinicalCase;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.Stat;
import net.jojoaddison.domain.enumeration.CaseStatus;
import net.jojoaddison.domain.enumeration.DelegationStatus;
import net.jojoaddison.domain.enumeration.ShiftStatus;
import net.jojoaddison.domain.enumeration.StatFlag;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.env.MockEnvironment;
import tech.jhipster.config.JHipsterConstants;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration tests for {@link DevelopmentDataInitializer}, the loader for a seed document supplied from outside the
 * image — {@code hc-patient-quality}'s {@code patient-demo-seed.json} in practice.
 *
 * <p>The initializer is constructed here rather than autowired, for the same reason {@link DemoDataInitializerIT}
 * does it: the bean is profile-gated, and this suite has to exercise the seeding whichever profiles the surrounding
 * context happens to activate. The environment is a {@link MockEnvironment} so a test can say which profile blocks are
 * meant to apply, which is the behaviour most likely to be got wrong.</p>
 */
@IntegrationTest
class DevelopmentDataInitializerIT {

    private static final String FIXTURE = "classpath:config/demo-data/seed-document-fixture.json";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private DutyRosterRepository dutyRosterRepository;

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private RecommendationRepository recommendationRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private ConditionRepository conditionRepository;

    @Autowired
    private AllergyRepository allergyRepository;

    @Autowired
    private CarePlanItemRepository carePlanItemRepository;

    @Autowired
    private StatRepository statRepository;

    @Autowired
    private ClinicalCaseRepository clinicalCaseRepository;

    @Autowired
    private VisitationRepository visitationRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private MedicationRepository medicationRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private EmergencyRepository emergencyRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private CareDelegationRepository careDelegationRepository;

    @BeforeEach
    void setUp() {
        professionalRepository.deleteAll();
        dutyRosterRepository.deleteAll();
        shiftRepository.deleteAll();
        recommendationRepository.deleteAll();
        profileRepository.deleteAll();
        statRepository.deleteAll();
        clinicalCaseRepository.deleteAll();
        careDelegationRepository.deleteAll();
    }

    private DevelopmentDataInitializer initializer(String location, String... activeProfiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);
        return build(location, environment);
    }

    private DevelopmentDataInitializer build(String location, Environment environment) {
        return new DevelopmentDataInitializer(
            location,
            objectMapper,
            new DefaultResourceLoader(),
            environment,
            professionalRepository,
            dutyRosterRepository,
            shiftRepository,
            recommendationRepository,
            profileRepository,
            addressRepository,
            membershipRepository,
            careDelegationRepository,
            conditionRepository,
            allergyRepository,
            carePlanItemRepository,
            statRepository,
            clinicalCaseRepository,
            visitationRepository,
            activityLogRepository,
            medicationRepository,
            reportRepository,
            emergencyRepository,
            taskRepository
        );
    }

    @Test
    void seedsTheDocumentOntoTheDomain() {
        initializer(FIXTURE, JHipsterConstants.SPRING_PROFILE_DEVELOPMENT).run(null);

        assertThat(professionalRepository.findAll()).hasSize(1);
        assertThat(profileRepository.findAll()).hasSize(2);

        // Every kind of field the document has to carry across: an enum, a date, an instant, a number.
        Profile patient = profileRepository.findById("patient-kojo").orElseThrow();
        assertThat(patient.getBirthDate()).hasToString("1976-04-19");

        Stat sugar = statRepository.findById("stat-sugar-2026-07-24").orElseThrow();
        assertThat(sugar.getFlag()).isEqualTo(StatFlag.WARN);
        assertThat(sugar.getValue()).isEqualTo(9.0);
        assertThat(sugar.getRecordedAt()).isNotNull();

        assertThat(shiftRepository.findById("clinic-osu-active").orElseThrow().getStatus()).isEqualTo(ShiftStatus.ACTIVE);
    }

    @Test
    void linksACaseToTheRecommendationsItCarries() {
        // ClinicalCase holds these by @DBRef, so the referenced documents have to be written before the case is. Get
        // the order wrong and the case saves with a reference to nothing, which reads as an empty screen.
        initializer(FIXTURE, JHipsterConstants.SPRING_PROFILE_DEVELOPMENT).run(null);

        ClinicalCase open = clinicalCaseRepository.findById("case-12").orElseThrow();
        assertThat(open.getStatus()).isEqualTo(CaseStatus.OPEN);
        assertThat(open.getRecommendations()).extracting("id").containsExactly("recommendation-hba1c-blood-test");
        assertThat(recommendationRepository.findById("recommendation-hba1c-blood-test")).isPresent();
    }

    @Test
    void appliesEveryActiveProfileBlockRatherThanTheMostSpecificOne() {
        // The quality stack runs with dev,test active. On hc-admin's rule that test wins, a document carrying its
        // record under dev would seed nothing at all — a healthy stack in front of an empty dashboard.
        initializer(FIXTURE, JHipsterConstants.SPRING_PROFILE_DEVELOPMENT, JHipsterConstants.SPRING_PROFILE_TEST).run(null);

        assertThat(profileRepository.findById("patient-kojo")).isPresent();
        assertThat(profileRepository.findById("patient-test-only")).isPresent();
    }

    @Test
    void appliesOnlyTheBlocksThatAreActive() {
        initializer(FIXTURE, JHipsterConstants.SPRING_PROFILE_TEST).run(null);

        assertThat(profileRepository.findById("patient-test-only")).isPresent();
        assertThat(profileRepository.findById("patient-kojo")).isEmpty();
    }

    @Test
    void isAdditiveAndNeverOverwritesAnEditedRecord() {
        DevelopmentDataInitializer initializer = initializer(FIXTURE, JHipsterConstants.SPRING_PROFILE_DEVELOPMENT);
        initializer.run(null);

        Profile edited = profileRepository.findById("patient-kojo").orElseThrow();
        edited.setSex("Female");
        profileRepository.save(edited);

        initializer.run(null);

        assertThat(profileRepository.count()).isEqualTo(2);
        assertThat(profileRepository.findById("patient-kojo").orElseThrow().getSex()).isEqualTo("Female");
    }

    @Test
    void restoresOnlyWhatWasRemoved() {
        DevelopmentDataInitializer initializer = initializer(FIXTURE, JHipsterConstants.SPRING_PROFILE_DEVELOPMENT);
        initializer.run(null);
        profileRepository.deleteById("patient-kojo");

        initializer.run(null);

        assertThat(profileRepository.findById("patient-kojo")).isPresent();
    }

    @Test
    void doesNothingWhenNoDocumentIsConfigured() {
        // The default in this repository. The dev loop and every deployed environment leave it unset, and this class
        // must be inert there rather than reaching for a file that is not meant to exist.
        initializer("", JHipsterConstants.SPRING_PROFILE_DEVELOPMENT).run(null);

        assertThat(profileRepository.count()).isZero();
        assertThat(professionalRepository.count()).isZero();
    }

    @Test
    void startsAnywayWhenTheDocumentIsMissing() {
        // Seed data is a convenience. A pointing-at-nothing location is worth a warning, not a service that will not
        // start — in the gateway's case that would take the whole subsystem's login down with it.
        initializer("file:/does/not/exist.json", JHipsterConstants.SPRING_PROFILE_DEVELOPMENT).run(null);

        assertThat(profileRepository.count()).isZero();
    }

    @Test
    void isGatedToDevelopmentAndTest() {
        // Same reasoning as DemoDataInitializer: this seeds people with clinical histories, and the gate is the only
        // thing keeping them out of a real database.
        // Fully qualified: this class also uses the Profile *entity*, and the two would collide on import.
        org.springframework.context.annotation.Profile gate =
            DevelopmentDataInitializer.class.getAnnotation(org.springframework.context.annotation.Profile.class);

        assertThat(gate).isNotNull();
        assertThat(gate.value())
            .containsExactlyInAnyOrder(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT, JHipsterConstants.SPRING_PROFILE_TEST);
    }

    @Test
    void seedsCareDelegations() {
        initializer(FIXTURE, JHipsterConstants.SPRING_PROFILE_DEVELOPMENT).run(null);

        CareDelegation delegation = careDelegationRepository.findById("delegation-active-demo").orElseThrow();
        assertThat(delegation.getStatus()).isEqualTo(DelegationStatus.ACTIVE);
        assertThat(delegation.getAngelEmail()).isEqualTo("angel.demo@localhost");
    }

    @Test
    void aSeededCareAngelContactIsNeverTurnedIntoADelegation() {
        // The real seed has carried careAngelName and careAngelPhone since long before delegation existed. They are a
        // contact — somebody to ring — and converting them would hand a named person standing access to a medical
        // record that nobody consented to. Access is only ever an explicit CareDelegation row.
        initializer(FIXTURE, JHipsterConstants.SPRING_PROFILE_DEVELOPMENT).run(null);

        Profile contactOnly = profileRepository.findById("profile-contact-only").orElseThrow();
        assertThat(contactOnly.getCareAngelName()).isEqualTo("Somebody Named");
        assertThat(contactOnly.getCareAngelEmail()).as("a contact is not an identity to authorize against").isNull();

        assertThat(careDelegationRepository.findAll())
            .as("only the delegation the document states, never one inferred from a contact")
            .hasSize(1);
    }

    @Test
    void seededProfilesReadAsOnboarded() {
        // Null onboardingStatus means COMPLETE. If that ever inverts, every seeded account lands in the wizard and
        // the quality stack shows an onboarding form instead of the demo data it exists to demonstrate.
        initializer(FIXTURE, JHipsterConstants.SPRING_PROFILE_DEVELOPMENT).run(null);

        assertThat(profileRepository.findAll()).allSatisfy(profile -> assertThat(profile.getOnboardingStatus()).isNull());
    }
}
