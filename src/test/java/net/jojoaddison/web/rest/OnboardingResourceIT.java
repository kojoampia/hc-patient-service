package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.CareDelegation;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.enumeration.ActivitySource;
import net.jojoaddison.domain.enumeration.DelegationStatus;
import net.jojoaddison.domain.enumeration.OnboardingStatus;
import net.jojoaddison.domain.enumeration.StatSource;
import net.jojoaddison.repository.AddressRepository;
import net.jojoaddison.repository.AllergyRepository;
import net.jojoaddison.repository.CareDelegationRepository;
import net.jojoaddison.repository.ConditionRepository;
import net.jojoaddison.repository.MedicationRepository;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.repository.StatRepository;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.PatientScope;
import net.jojoaddison.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The onboarding journey end to end, and the ways in which it must refuse.
 *
 * <p>The cases worth reading first are the ones about identity: that the payload cannot name the patient, that the
 * bootstrap succeeds exactly once, and that a care angel cannot run it for somebody else. Those are what stop the one
 * endpoint that works without an existing profile from being the one endpoint that can create a profile for anyone.</p>
 */
@IntegrationTest
@AutoConfigureMockMvc
class OnboardingResourceIT {

    private static final String API = "/api/onboarding";
    private static final String EMAIL = "ama@example.test";

    @Autowired
    private MockMvc restMockMvc;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private StatRepository statRepository;

    @Autowired
    private ConditionRepository conditionRepository;

    @Autowired
    private AllergyRepository allergyRepository;

    @Autowired
    private MedicationRepository medicationRepository;

    @Autowired
    private CareDelegationRepository careDelegationRepository;

    @BeforeEach
    void initTest() {
        profileRepository.deleteAll();
        addressRepository.deleteAll();
        statRepository.deleteAll();
        conditionRepository.deleteAll();
        allergyRepository.deleteAll();
        medicationRepository.deleteAll();
        careDelegationRepository.deleteAll();
    }

    // --- the journey ----------------------------------------------------------------------------------------------

    @Test
    void aPatientWithNoRecordIsNotOnboarded() throws Exception {
        restMockMvc
            .perform(get(API + "/status").with(patient()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.onboarded").value(false))
            .andExpect(jsonPath("$.profileId").doesNotExist());
    }

    @Test
    void theWholeJourney() throws Exception {
        startOnboarding().andExpect(status().isCreated()).andExpect(jsonPath("$.email").value(EMAIL));

        Profile created = profileRepository.findOneByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(created.getPatientId()).as("patientId is minted, never left to the fallback").isEqualTo(created.getId());
        assertThat(created.getAddress()).as("the address document is written and referenced").isNotNull();
        assertThat(created.getAddress().getPatientId()).as("and is owned, so PatientScope can see it").isEqualTo(created.getPatientId());

        restMockMvc.perform(step("/care-angel", angelJson())).andExpect(status().isOk());
        restMockMvc.perform(step("/baseline", baselineJson())).andExpect(status().isOk());
        restMockMvc.perform(step("/current-state", currentStateJson())).andExpect(status().isOk());
        restMockMvc.perform(step("/identification", "{\"cardType\":\"Ghana Card\",\"cardNumber\":\"GHA-123\"}")).andExpect(status().isOk());

        restMockMvc.perform(post(API + "/complete").with(patient())).andExpect(status().isOk());

        Profile done = profileRepository.findOneByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(done.getOnboardingStatus()).isEqualTo(OnboardingStatus.COMPLETE);
        assertThat(done.getOnboardingCompletedAt()).isNotNull();

        // The portal is not empty afterwards, which is the entire point of writing into the real collections.
        assertThat(statRepository.findAll()).hasSize(4);
        assertThat(conditionRepository.findAll()).hasSize(1);
        assertThat(allergyRepository.findAll()).hasSize(1);
        assertThat(medicationRepository.findAll()).isEmpty();
        assertThat(careDelegationRepository.findAll()).hasSize(2);
    }

    @Test
    void everythingWrittenIsMarkedPatientReported() throws Exception {
        startOnboarding();
        restMockMvc.perform(step("/baseline", baselineJson())).andExpect(status().isOk());
        restMockMvc.perform(step("/current-state", currentStateJson())).andExpect(status().isOk());

        // A self-reported allergy and a clinician-attested one must not be the same document. notedById stays null;
        // source says who it came from.
        assertThat(allergyRepository.findAll())
            .allSatisfy(allergy -> {
                assertThat(allergy.getSource()).isEqualTo(ActivitySource.PATIENT);
                assertThat(allergy.getNotedById()).isNull();
            });
        assertThat(conditionRepository.findAll())
            .allSatisfy(condition -> assertThat(condition.getSource()).isEqualTo(ActivitySource.PATIENT));
        assertThat(statRepository.findAll())
            .allSatisfy(stat -> {
                assertThat(stat.getSource()).isEqualTo(StatSource.PATIENT);
                assertThat(stat.getFlag()).as("judging a reading against a band is a clinical act").isNull();
            });
    }

    @Test
    void aStandbyNomineeIsRecordedDormantAndNoticeIsSentToNobody() throws Exception {
        startOnboarding();
        restMockMvc.perform(step("/care-angel", angelJson())).andExpect(status().isOk());

        CareDelegation standby = careDelegationRepository
            .findAll()
            .stream()
            .filter(d -> d.getStatus() == DelegationStatus.STANDBY)
            .findFirst()
            .orElseThrow();
        assertThat(standby.getAdvanceConsent()).isTrue();
        assertThat(standby.getAcceptedAt()).isNull();
    }

    // --- the refusals ---------------------------------------------------------------------------------------------

    @Test
    void theBootstrapSucceedsExactlyOnce() throws Exception {
        startOnboarding().andExpect(status().isCreated());

        // Without this the endpoint that needs no existing profile would be one that can be called forever.
        startOnboarding().andExpect(status().isBadRequest());

        assertThat(profileRepository.findAll()).hasSize(1);
    }

    @Test
    void thePayloadCannotNameThePatient() throws Exception {
        // email, patientId and id are simply not fields of the step DTO, so a client that sends them is ignored rather
        // than obeyed. This is the property that stops onboarding being an account-takeover endpoint.
        restMockMvc
            .perform(
                post(API)
                    .with(patient())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"firstName\":\"Ama\",\"lastName\":\"Mensah\",\"email\":\"someone.else@example.test\"," +
                        "\"patientId\":\"somebody-elses-id\",\"id\":\"forced-id\"}"
                    )
            )
            .andExpect(status().isCreated());

        Profile created = profileRepository.findAll().get(0);
        assertThat(created.getEmail()).as("taken from the token").isEqualTo(EMAIL);
        assertThat(created.getPatientId()).as("minted by the server").isEqualTo(created.getId());
        assertThat(created.getId()).isNotEqualTo("forced-id");
    }

    @Test
    void anAccountWithNoEmailClaimCannotOnboard() throws Exception {
        // The gateway gives such an account an unscoped token and this service already reads that as "no records at
        // all". It has to mean "cannot onboard" too, or the one identity that cannot be pinned down is the one that
        // gets to create a patient.
        RequestPostProcessor noEmail = jwt().authorities(new SimpleGrantedAuthority(AuthoritiesConstants.USER));

        restMockMvc
            .perform(post(API).with(noEmail).contentType(MediaType.APPLICATION_JSON).content(identityJson()))
            .andExpect(status().isForbidden());
    }

    @Test
    void anAngelCannotOnboardForTheirPatient() throws Exception {
        startOnboarding();
        Profile patient = profileRepository.findOneByEmailIgnoreCase(EMAIL).orElseThrow();
        careDelegationRepository.save(
            new CareDelegation().patientId(patient.getPatientId()).angelEmail("kofi@example.test").status(DelegationStatus.ACTIVE)
        );

        // Onboarding is the patient answering about themselves, and the consent it collects cannot be given by the
        // person who benefits from it.
        restMockMvc
            .perform(
                patch(API + "/identification")
                    .with(angel())
                    .header(PatientScope.ACTING_AS_HEADER, patient.getPatientId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"cardType\":\"Ghana Card\",\"cardNumber\":\"GHA-999\"}")
            )
            .andExpect(status().isForbidden());

        assertThat(profileRepository.findOneByEmailIgnoreCase(EMAIL).orElseThrow().getCardNumber()).isNull();
    }

    @Test
    void identificationIsRequiredToFinish() throws Exception {
        startOnboarding();
        restMockMvc.perform(step("/care-angel", angelJson())).andExpect(status().isOk());
        restMockMvc.perform(step("/baseline", baselineJson())).andExpect(status().isOk());
        restMockMvc.perform(step("/current-state", currentStateJson())).andExpect(status().isOk());

        restMockMvc.perform(post(API + "/complete").with(patient())).andExpect(status().isBadRequest());

        // And it cannot be satisfied by an empty answer either — this step has no "none".
        restMockMvc.perform(step("/identification", "{\"cardType\":\"\",\"cardNumber\":\"\"}")).andExpect(status().isBadRequest());

        assertThat(profileRepository.findOneByEmailIgnoreCase(EMAIL).orElseThrow().getOnboardingStatus())
            .isEqualTo(OnboardingStatus.IN_PROGRESS);
    }

    @Test
    void aRepeatableGroupMustBeAnsweredOrDeclaredEmpty() throws Exception {
        startOnboarding();

        // Neither entries nor a "none" flag: "I have no allergies" and "I have not answered yet" are different
        // clinical statements and an empty list cannot tell them apart.
        restMockMvc
            .perform(step("/current-state", "{\"bloodGroup\":\"O+\",\"noConditions\":true,\"noMedications\":true}"))
            .andExpect(status().isBadRequest());

        // And a group cannot be both empty and populated.
        restMockMvc
            .perform(
                step(
                    "/current-state",
                    "{\"noConditions\":true,\"noAllergies\":true,\"noMedications\":true," + "\"conditions\":[{\"name\":\"Asthma\"}]}"
                )
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void stepsBeforeTheBootstrapAreRefused() throws Exception {
        restMockMvc.perform(step("/baseline", baselineJson())).andExpect(status().isBadRequest());
    }

    @Test
    void stepsNeverGoBackwards() throws Exception {
        startOnboarding();
        restMockMvc.perform(step("/care-angel", angelJson())).andExpect(status().isOk());
        restMockMvc.perform(step("/baseline", baselineJson())).andExpect(status().isOk());
        restMockMvc.perform(step("/current-state", currentStateJson())).andExpect(status().isOk());

        // Revisiting step 2 does not un-answer steps 3 and 4.
        restMockMvc.perform(step("/care-angel", angelJson())).andExpect(status().isOk());

        assertThat(profileRepository.findOneByEmailIgnoreCase(EMAIL).orElseThrow().getOnboardingStep()).isEqualTo(4);
    }

    // --- helpers --------------------------------------------------------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions startOnboarding() throws Exception {
        return restMockMvc.perform(post(API).with(patient()).contentType(MediaType.APPLICATION_JSON).content(identityJson()));
    }

    private org.springframework.test.web.servlet.RequestBuilder step(String path, String body) {
        return patch(API + path).with(patient()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String identityJson() {
        return (
            "{\"firstName\":\"Ama\",\"lastName\":\"Mensah\",\"birthDate\":\"1990-04-02\",\"sex\":\"F\"," +
            "\"mobilePhone\":\"0244000000\"," +
            "\"address\":{\"streetAddress\":\"5 Ankobra River Street\",\"town\":\"Accra\",\"region\":\"Greater Accra\"," +
            "\"digitalAddress\":\"GA-123-4567\",\"country\":\"Ghana\"}}"
        );
    }

    private static String angelJson() {
        return (
            "{\"firstName\":\"Kofi\",\"lastName\":\"Boateng\",\"fullName\":\"Kofi Boateng\",\"phone\":\"0244111111\"," +
            "\"email\":\"kofi@example.test\",\"contacts\":\"Sister, 0244222222\",\"advanceConsent\":true," +
            "\"standby\":{\"firstName\":\"Esi\",\"lastName\":\"Owusu\",\"fullName\":\"Esi Owusu\"," +
            "\"phone\":\"0244333333\",\"email\":\"esi@example.test\"}}"
        );
    }

    private static String baselineJson() {
        return "{\"heightCm\":168,\"weightKg\":64,\"systolic\":118,\"diastolic\":76,\"heartRateBpm\":72}";
    }

    private static String currentStateJson() {
        return (
            "{\"bloodGroup\":\"O+\",\"conditions\":[{\"name\":\"Asthma\",\"description\":\"Since childhood\"}]," +
            "\"allergies\":[{\"name\":\"Penicillin\",\"category\":\"MEDICATION\",\"severity\":\"SEVERE\"," +
            "\"reaction\":\"Rash\"}],\"noMedications\":true}"
        );
    }

    private static RequestPostProcessor patient() {
        return jwt()
            .jwt(builder -> builder.claim(SecurityUtils.EMAIL_KEY, EMAIL))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.USER), new SimpleGrantedAuthority(AuthoritiesConstants.PATIENT));
    }

    private static RequestPostProcessor angel() {
        return jwt()
            .jwt(builder -> builder.claim(SecurityUtils.EMAIL_KEY, "kofi@example.test"))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.USER), new SimpleGrantedAuthority(AuthoritiesConstants.ANGEL));
    }
}
