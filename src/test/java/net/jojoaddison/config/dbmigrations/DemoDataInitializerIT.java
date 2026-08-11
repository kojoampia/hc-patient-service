package net.jojoaddison.config.dbmigrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.ClinicalCase;
import net.jojoaddison.domain.DutyRoster;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.Shift;
import net.jojoaddison.domain.enumeration.CaseStatus;
import net.jojoaddison.domain.enumeration.ShiftStatus;
import net.jojoaddison.repository.ActivityLogRepository;
import net.jojoaddison.repository.ClinicalCaseRepository;
import net.jojoaddison.repository.DutyRosterRepository;
import net.jojoaddison.repository.MedicationRepository;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.repository.RecommendationRepository;
import net.jojoaddison.repository.ReportRepository;
import net.jojoaddison.repository.ShiftRepository;
import net.jojoaddison.repository.VisitationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tech.jhipster.config.JHipsterConstants;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration tests for {@link DemoDataInitializer}.
 *
 * <p>The initializer is constructed here rather than autowired: it is gated to the {@code dev} and {@code test}
 * profiles, and this suite must exercise the seeding whether or not the surrounding test context happens to activate
 * one of them. The gate itself is asserted separately, below, because it is the security-relevant part — these are
 * invented patients with invented clinical histories and a real deployment must never carry them.</p>
 */
@IntegrationTest
class DemoDataInitializerIT {

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
    private ClinicalCaseRepository clinicalCaseRepository;

    @Autowired
    private VisitationRepository visitationRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private MedicationRepository medicationRepository;

    @Autowired
    private ReportRepository reportRepository;

    private DemoDataInitializer initializer;

    @BeforeEach
    void setUp() {
        professionalRepository.deleteAll();
        dutyRosterRepository.deleteAll();
        shiftRepository.deleteAll();
        recommendationRepository.deleteAll();
        profileRepository.deleteAll();
        clinicalCaseRepository.deleteAll();
        visitationRepository.deleteAll();
        activityLogRepository.deleteAll();
        medicationRepository.deleteAll();
        reportRepository.deleteAll();

        initializer =
            new DemoDataInitializer(
                objectMapper,
                professionalRepository,
                dutyRosterRepository,
                shiftRepository,
                recommendationRepository,
                profileRepository,
                clinicalCaseRepository,
                visitationRepository,
                activityLogRepository,
                medicationRepository,
                reportRepository
            );
    }

    @Test
    void seedsTheWholeDemoDataset() {
        initializer.run(null);

        assertThat(professionalRepository.findAll()).hasSize(1);
        assertThat(dutyRosterRepository.findAll()).hasSize(2);
        assertThat(shiftRepository.findAll()).hasSize(3);
        assertThat(recommendationRepository.findAll()).hasSize(4);
        assertThat(profileRepository.findAll()).hasSize(7);
        assertThat(clinicalCaseRepository.findAll()).hasSize(7);
        assertThat(visitationRepository.findAll()).hasSize(1);
        assertThat(activityLogRepository.findAll()).hasSize(1);
        assertThat(medicationRepository.findAll()).hasSize(1);
        assertThat(reportRepository.findAll()).hasSize(1);
    }

    @Test
    void mapsTheClinicianOntoTheGatewayAccountThatSignsInAsThem() {
        initializer.run(null);

        Professional doctor = professionalRepository.findById("professional-doctor").orElseThrow();
        assertThat(doctor.getFirstName()).isEqualTo("Ama");
        assertThat(doctor.getLastName()).isEqualTo("Mensah");
        assertThat(doctor.getRole()).isEqualTo("Doctor");
        assertThat(doctor.getInitials()).isEqualTo("AM");
        // The demo file identifies this professional by accountLogin "doctor"; the gateway seeds that account as
        // doctor@localhost, and the email is the only join between the two services.
        assertThat(doctor.getEmail()).isEqualTo("doctor@localhost");
    }

    @Test
    void givesTheRosterIdOnAClinicalCaseSomethingToPointAt() {
        initializer.run(null);

        ClinicalCase urgent = clinicalCaseRepository.findById("case-kojo-urgent").orElseThrow();
        assertThat(urgent.getStatus()).isEqualTo(CaseStatus.URGENT);
        assertThat(urgent.getPatientId()).isEqualTo("patient-kojo");
        assertThat(urgent.getAssignedProfessionalId()).isEqualTo("professional-doctor");
        assertThat(urgent.getAssignedRosterId()).isEqualTo("ward-3-night");

        DutyRoster roster = dutyRosterRepository.findById(urgent.getAssignedRosterId()).orElseThrow();
        assertThat(roster.getName()).isEqualTo("Ward 3 — Night Shift");
        assertThat(roster.getSubscribedProfessionalIds()).containsExactly("professional-doctor");

        // The empty roster is seeded too — a roster with neither subscribers nor shifts is a real state.
        DutyRoster empty = dutyRosterRepository.findById("clinic-a-day").orElseThrow();
        assertThat(empty.getSubscribedProfessionalIds()).isEmpty();
        assertThat(shiftRepository.findByRosterId("clinic-a-day")).isEmpty();
    }

    @Test
    void seedsTheThreeShiftStatesTheDashboardRenders() {
        initializer.run(null);

        List<Shift> shifts = shiftRepository.findByRosterId("ward-3-night");
        assertThat(shifts)
            .hasSize(3)
            .extracting(Shift::getStatus)
            .containsExactlyInAnyOrder(ShiftStatus.ACTIVE, ShiftStatus.UPCOMING, ShiftStatus.COMPLETED);
        assertThat(shifts)
            .allSatisfy(shift -> {
                assertThat(shift.getProfessionalId()).isEqualTo("professional-doctor");
                assertThat(shift.getStartsAt()).isNotNull();
                assertThat(shift.getEndsAt()).isNotNull();
            });
    }

    @Test
    void mapsAPatientOntoAProfileIncludingTheEmergencyContact() {
        initializer.run(null);

        Profile kojo = profileRepository.findById("patient-kojo").orElseThrow();
        assertThat(kojo.getPatientId()).isEqualTo("patient-kojo");
        assertThat(kojo.getFirstName()).isEqualTo("Kojo");
        assertThat(kojo.getLastName()).isEqualTo("Ampia-Addison");
        assertThat(kojo.getSex()).isEqualTo("male");
        assertThat(kojo.getBirthDate()).isEqualTo(java.time.LocalDate.of(1976, 4, 19));
        assertThat(kojo.getMobilePhone()).isEqualTo("0242286304");
        assertThat(kojo.getEmail()).isEqualTo("kojo@jac.net");
        // emergencyContact is the demo file's name for what the domain calls the care angel.
        assertThat(kojo.getCareAngelName()).isEqualTo("Ophelia Gaisie");
        assertThat(kojo.getCareAngelPhone()).isEqualTo("0502286304");
    }

    @Test
    void storesAnEmptyStringAsNothingRatherThanAnEmptyValue() {
        initializer.run(null);

        // symptoms and diagnosis are "" throughout the demo file. Stored as empty strings the portal renders them as
        // present-but-blank fields, which reads as "assessed, nothing found" rather than "not assessed".
        ClinicalCase urgent = clinicalCaseRepository.findById("case-kojo-urgent").orElseThrow();
        assertThat(urgent.getSymptoms()).isNull();
        assertThat(urgent.getDiagnosis()).isNull();
    }

    @Test
    void isIdempotentAndNeverOverwritesAnEditedRecord() {
        initializer.run(null);
        long professionals = professionalRepository.count();
        long cases = clinicalCaseRepository.count();

        // A developer edits a seeded record through the API.
        Professional edited = professionalRepository.findById("professional-doctor").orElseThrow();
        edited.setRole("Consultant");
        professionalRepository.save(edited);

        initializer.run(null);

        assertThat(professionalRepository.count()).isEqualTo(professionals);
        assertThat(clinicalCaseRepository.count()).isEqualTo(cases);
        assertThat(professionalRepository.findById("professional-doctor").orElseThrow().getRole()).isEqualTo("Consultant");
    }

    @Test
    void restoresOnlyWhatWasRemoved() {
        initializer.run(null);
        shiftRepository.deleteById("ward-3-active");
        assertThat(shiftRepository.count()).isEqualTo(2);

        initializer.run(null);

        assertThat(shiftRepository.count()).isEqualTo(3);
        assertThat(shiftRepository.findById("ward-3-active")).isPresent();
    }

    @Test
    void isGatedToDevelopmentAndTest() {
        // The whole point of this class. Seeding invented patients in every profile is the mistake the gateway already
        // made once with its development accounts; if this annotation goes, so does the only thing keeping seven
        // fictional clinical histories out of a real database.
        // Fully qualified: this class also uses the Profile *entity*, and the two would collide on import.
        org.springframework.context.annotation.Profile gate =
            DemoDataInitializer.class.getAnnotation(org.springframework.context.annotation.Profile.class);

        assertThat(gate).isNotNull();
        assertThat(gate.value())
            .containsExactlyInAnyOrder(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT, JHipsterConstants.SPRING_PROFILE_TEST);
    }
}
