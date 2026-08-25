package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Allergy;
import net.jojoaddison.domain.CareDelegation;
import net.jojoaddison.domain.DeletionRequest;
import net.jojoaddison.domain.Medication;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.enumeration.DelegationStatus;
import net.jojoaddison.domain.enumeration.DeletionRequestStatus;
import net.jojoaddison.repository.AllergyRepository;
import net.jojoaddison.repository.CareDelegationRepository;
import net.jojoaddison.repository.DeletionRequestRepository;
import net.jojoaddison.repository.MedicationRepository;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.PatientScope;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.DeletionRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Asking to be erased, and who may answer.
 *
 * <p>Two thirds of this file is about refusals, and that is the right proportion. The endpoint that erases is
 * irreversible and the endpoint that asks for it is a tap on a phone, so what matters is not that the happy path
 * works — it is that a care angel, an administrator wearing a patient's scope, and the patient themselves are each
 * stopped at exactly the right point.</p>
 *
 * <p>Identities are built with {@code jwt()} rather than {@code @WithMockUser}, as in
 * {@link CareDelegationResourceIT}: every rule here turns on the token's email claim, which a mock user does not
 * carry.</p>
 */
@IntegrationTest
@AutoConfigureMockMvc
class DeletionRequestResourceIT {

    private static final String PATIENT_EMAIL = "ama@example.test";
    private static final String PATIENT_ID = "ama-patient";
    private static final String ANGEL_EMAIL = "kofi@example.test";
    private static final String OTHER_EMAIL = "yaa@example.test";
    private static final String OTHER_PATIENT_ID = "yaa-patient";

    private static final String API = "/api/deletion-requests";

    @Autowired
    private MockMvc restMockMvc;

    @Autowired
    private DeletionRequestRepository deletionRequestRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private CareDelegationRepository careDelegationRepository;

    @Autowired
    private AllergyRepository allergyRepository;

    @Autowired
    private MedicationRepository medicationRepository;

    @BeforeEach
    void initTest() {
        deletionRequestRepository.deleteAll();
        careDelegationRepository.deleteAll();
        allergyRepository.deleteAll();
        medicationRepository.deleteAll();
        profileRepository.deleteAll();

        Profile ama = new Profile().patientId(PATIENT_ID).email(PATIENT_EMAIL).firstName("Ama");
        ama.setId("ama-profile");
        profileRepository.save(ama);

        Profile yaa = new Profile().patientId(OTHER_PATIENT_ID).email(OTHER_EMAIL).firstName("Yaa");
        yaa.setId("yaa-profile");
        profileRepository.save(yaa);
    }

    // --- the patient's side ---------------------------------------------------------------------------------------

    @Test
    void aPatientRaisesOneAndIsGivenADate() throws Exception {
        Instant before = Instant.now();

        restMockMvc
            .perform(post(API).with(patient(PATIENT_EMAIL)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"moving on\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.patientId").value(PATIENT_ID))
            .andExpect(jsonPath("$.requestedByEmail").value(PATIENT_EMAIL))
            .andExpect(jsonPath("$.reason").value("moving on"));

        DeletionRequest stored = deletionRequestRepository.findAll().get(0);
        assertThat(Duration.between(stored.getRequestedAt(), stored.getDueAt()))
            .as("the fourteen days the privacy policy promises")
            .isEqualTo(DeletionRequestService.WINDOW);
        assertThat(stored.getDueAt()).isAfter(before.plus(13, ChronoUnit.DAYS));
    }

    @Test
    void theReasonIsOptional() throws Exception {
        restMockMvc.perform(post(API).with(patient(PATIENT_EMAIL))).andExpect(status().isCreated());

        assertThat(deletionRequestRepository.findAll().get(0).getReason()).isNull();
    }

    @Test
    void onlyOneMayBePendingAtATime() throws Exception {
        restMockMvc.perform(post(API).with(patient(PATIENT_EMAIL))).andExpect(status().isCreated());
        restMockMvc.perform(post(API).with(patient(PATIENT_EMAIL))).andExpect(status().isBadRequest());

        assertThat(deletionRequestRepository.findAll()).hasSize(1);
    }

    @Test
    void mineIsNoContentUntilThereIsOne() throws Exception {
        restMockMvc.perform(get(API + "/mine").with(patient(PATIENT_EMAIL))).andExpect(status().isNoContent());

        restMockMvc.perform(post(API).with(patient(PATIENT_EMAIL))).andExpect(status().isCreated());

        restMockMvc
            .perform(get(API + "/mine").with(patient(PATIENT_EMAIL)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void aPatientWithdrawsTheirOwnDuringTheWindow() throws Exception {
        DeletionRequest pending = pendingFor(PATIENT_ID, PATIENT_EMAIL);

        restMockMvc
            .perform(post(API + "/{id}/cancel", pending.getId()).with(patient(PATIENT_EMAIL)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(reload(pending).getCancelledAt()).isNotNull();
        // And the banner goes away.
        restMockMvc.perform(get(API + "/mine").with(patient(PATIENT_EMAIL))).andExpect(status().isNoContent());
    }

    @Test
    void aPatientCannotWithdrawSomebodyElsesAndCannotTellItExists() throws Exception {
        DeletionRequest pending = pendingFor(PATIENT_ID, PATIENT_EMAIL);

        // 400/idnotfound rather than 403: "not yours" must read exactly like "no such id", or this is a way to probe
        // for other patients' request ids.
        restMockMvc.perform(post(API + "/{id}/cancel", pending.getId()).with(patient(OTHER_EMAIL))).andExpect(status().isBadRequest());

        assertThat(reload(pending).getStatus()).isEqualTo(DeletionRequestStatus.PENDING);
    }

    @Test
    void aClosedRequestCannotBeWithdrawnAgain() throws Exception {
        DeletionRequest pending = pendingFor(PATIENT_ID, PATIENT_EMAIL);
        restMockMvc.perform(post(API + "/{id}/cancel", pending.getId()).with(patient(PATIENT_EMAIL))).andExpect(status().isOk());

        restMockMvc.perform(post(API + "/{id}/cancel", pending.getId()).with(patient(PATIENT_EMAIL))).andExpect(status().isBadRequest());
    }

    // --- who may not raise one ------------------------------------------------------------------------------------

    @Test
    void aCareAngelActingForThePatientMayNotAskForTheRecordToBeErased() throws Exception {
        careDelegationRepository.save(new CareDelegation().patientId(PATIENT_ID).angelEmail(ANGEL_EMAIL).status(DelegationStatus.ACTIVE));

        // The delegation is real and grants full read and write over this record — the angel could edit every
        // document in it. It still does not grant this. A delegation exists so decisions can be made when the patient
        // cannot make them, not so the record can be ended on their behalf.
        restMockMvc.perform(post(API).with(angel()).header(PatientScope.ACTING_AS_HEADER, PATIENT_ID)).andExpect(status().isForbidden());

        assertThat(deletionRequestRepository.findAll()).isEmpty();
    }

    @Test
    void anAngelIsNotEvenToldWhetherThePatientHasAsked() throws Exception {
        careDelegationRepository.save(new CareDelegation().patientId(PATIENT_ID).angelEmail(ANGEL_EMAIL).status(DelegationStatus.ACTIVE));
        pendingFor(PATIENT_ID, PATIENT_EMAIL);

        restMockMvc
            .perform(get(API + "/mine").with(angel()).header(PatientScope.ACTING_AS_HEADER, PATIENT_ID))
            .andExpect(status().isNoContent());
    }

    @Test
    void anAdministratorWithAPatientOpenMayNotRaiseOneAsThem() throws Exception {
        // Without this an administrator viewing a record could manufacture the patient's own consent for erasing it.
        // They can complete a request; they cannot invent one.
        restMockMvc.perform(post(API).with(admin()).header(PatientScope.ACTING_AS_HEADER, PATIENT_ID)).andExpect(status().isForbidden());

        assertThat(deletionRequestRepository.findAll()).isEmpty();
    }

    @Test
    void aClinicianMayNotRaiseOneEither() throws Exception {
        restMockMvc.perform(post(API).with(doctor()).header(PatientScope.ACTING_AS_HEADER, PATIENT_ID)).andExpect(status().isForbidden());
    }

    @Test
    void anAccountWithNoProfileHasNothingToDelete() throws Exception {
        restMockMvc.perform(post(API).with(patient("nobody@example.test"))).andExpect(status().isForbidden());
    }

    // --- the delete action is the administrator's, and only theirs ------------------------------------------------

    @Test
    void aPatientMayNotCompleteTheirOwnRequest() throws Exception {
        DeletionRequest pending = pendingFor(PATIENT_ID, PATIENT_EMAIL);
        seedRecordFor(PATIENT_ID);

        restMockMvc.perform(post(API + "/{id}/complete", pending.getId()).with(patient(PATIENT_EMAIL))).andExpect(status().isForbidden());

        assertThat(reload(pending).getStatus()).isEqualTo(DeletionRequestStatus.PENDING);
        assertThat(allergyRepository.findByPatientId(PATIENT_ID)).as("nothing was erased").hasSize(1);
    }

    @Test
    void aClinicianMayNotCompleteOne() throws Exception {
        DeletionRequest pending = pendingFor(PATIENT_ID, PATIENT_EMAIL);

        restMockMvc.perform(post(API + "/{id}/complete", pending.getId()).with(doctor())).andExpect(status().isForbidden());

        assertThat(reload(pending).getStatus()).isEqualTo(DeletionRequestStatus.PENDING);
    }

    @Test
    void anAdministratorCompletesItAndTheRecordIsGone() throws Exception {
        DeletionRequest pending = pendingFor(PATIENT_ID, PATIENT_EMAIL);
        seedRecordFor(PATIENT_ID);
        seedRecordFor(OTHER_PATIENT_ID);

        restMockMvc
            .perform(post(API + "/{id}/complete", pending.getId()).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.completedByLogin").value("root"));

        assertThat(profileRepository.findByPatientId(PATIENT_ID)).isEmpty();
        assertThat(allergyRepository.findByPatientId(PATIENT_ID)).isEmpty();
        assertThat(medicationRepository.findByPatientId(PATIENT_ID)).isEmpty();

        // The one assertion that would still pass if the erasure query were wrong in the worst possible way.
        assertThat(profileRepository.findByPatientId(OTHER_PATIENT_ID)).as("the other patient is untouched").hasSize(1);
        assertThat(allergyRepository.findByPatientId(OTHER_PATIENT_ID)).hasSize(1);

        DeletionRequest completed = reload(pending);
        assertThat(completed.getCompletedAt()).isNotNull();
        assertThat(completed.getErasedCounts()).containsEntry("allergy", 1L).containsEntry("medication", 1L);
    }

    @Test
    void theRequestItselfSurvivesTheErasureItCommissioned() throws Exception {
        DeletionRequest pending = pendingFor(PATIENT_ID, PATIENT_EMAIL);
        seedRecordFor(PATIENT_ID);

        restMockMvc.perform(post(API + "/{id}/complete", pending.getId()).with(admin())).andExpect(status().isOk());

        // Erasing the evidence that the erasure was asked for and authorised would leave nothing to answer a
        // regulator with. It keeps counts and dates, never clinical content.
        assertThat(deletionRequestRepository.findById(pending.getId())).isPresent();
    }

    @Test
    void completingTwiceIsRefusedRatherThanRepeated() throws Exception {
        DeletionRequest pending = pendingFor(PATIENT_ID, PATIENT_EMAIL);
        restMockMvc.perform(post(API + "/{id}/complete", pending.getId()).with(admin())).andExpect(status().isOk());

        restMockMvc.perform(post(API + "/{id}/complete", pending.getId()).with(admin())).andExpect(status().isBadRequest());
    }

    @Test
    void onceCompletedTheAccountResolvesToNobody() throws Exception {
        DeletionRequest pending = pendingFor(PATIENT_ID, PATIENT_EMAIL);
        seedRecordFor(PATIENT_ID);
        restMockMvc.perform(post(API + "/{id}/complete", pending.getId()).with(admin())).andExpect(status().isOk());

        // 403 rather than the 400 a closed request would otherwise give, and the difference is the point: the erasure
        // took the Profile with it, so PatientScope can no longer resolve this token to any patient at all. The
        // account still exists in the gateway — closing it is a separate step — but from here it is nobody, and every
        // patient-scoped endpoint refuses it identically. Failing closed is what makes that safe.
        restMockMvc.perform(post(API + "/{id}/cancel", pending.getId()).with(patient(PATIENT_EMAIL))).andExpect(status().isForbidden());
        restMockMvc.perform(get(API + "/mine").with(patient(PATIENT_EMAIL))).andExpect(status().isNoContent());

        assertThat(reload(pending).getStatus()).isEqualTo(DeletionRequestStatus.COMPLETED);
    }

    // --- refusing one ---------------------------------------------------------------------------------------------

    @Test
    void anAdministratorMayRefuseButMustSayWhy() throws Exception {
        DeletionRequest pending = pendingFor(PATIENT_ID, PATIENT_EMAIL);

        restMockMvc
            .perform(post(API + "/{id}/reject", pending.getId()).with(admin()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());

        restMockMvc
            .perform(
                post(API + "/{id}/reject", pending.getId())
                    .with(admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"decisionReason\":\"records are under a legal hold until March\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.decisionReason").value("records are under a legal hold until March"));
    }

    @Test
    void rejectingErasesNothing() throws Exception {
        DeletionRequest pending = pendingFor(PATIENT_ID, PATIENT_EMAIL);
        seedRecordFor(PATIENT_ID);

        restMockMvc
            .perform(
                post(API + "/{id}/reject", pending.getId())
                    .with(admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"decisionReason\":\"open investigation\"}")
            )
            .andExpect(status().isOk());

        assertThat(allergyRepository.findByPatientId(PATIENT_ID)).hasSize(1);
    }

    // --- the queue ------------------------------------------------------------------------------------------------

    @Test
    void theQueueIsAdministratorsOnly() throws Exception {
        pendingFor(PATIENT_ID, PATIENT_EMAIL);

        restMockMvc.perform(get(API).with(patient(PATIENT_EMAIL))).andExpect(status().isForbidden());
        restMockMvc.perform(get(API).with(doctor())).andExpect(status().isForbidden());
        restMockMvc.perform(get(API).with(admin())).andExpect(status().isOk()).andExpect(jsonPath("$.[0].patientId").value(PATIENT_ID));
    }

    @Test
    void theQueueDefaultsToWhatIsStillOwed() throws Exception {
        DeletionRequest cancelled = pendingFor(OTHER_PATIENT_ID, OTHER_EMAIL);
        restMockMvc.perform(post(API + "/{id}/cancel", cancelled.getId()).with(patient(OTHER_EMAIL))).andExpect(status().isOk());
        pendingFor(PATIENT_ID, PATIENT_EMAIL);

        restMockMvc
            .perform(get(API).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$.[0].patientId").value(PATIENT_ID));

        restMockMvc
            .perform(get(API).param("status", "CANCELLED").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$.[0].patientId").value(OTHER_PATIENT_ID));
    }

    // --- fixtures -------------------------------------------------------------------------------------------------

    private DeletionRequest pendingFor(String patientId, String email) {
        Instant now = Instant.now();
        return deletionRequestRepository.save(
            new DeletionRequest()
                .patientId(patientId)
                .requestedByEmail(email)
                .status(DeletionRequestStatus.PENDING)
                .requestedAt(now)
                .dueAt(now.plus(DeletionRequestService.WINDOW))
        );
    }

    /** Two documents in two collections, enough to tell an erasure that ran from one that only said so. */
    private void seedRecordFor(String patientId) {
        allergyRepository.save(new Allergy().patientId(patientId).name("penicillin"));
        medicationRepository.save(new Medication().patientId(patientId).name("amoxicillin"));
    }

    private DeletionRequest reload(DeletionRequest request) {
        return deletionRequestRepository.findById(request.getId()).orElseThrow();
    }

    private static RequestPostProcessor patient(String email) {
        return jwt()
            .jwt(builder -> builder.claim(SecurityUtils.EMAIL_KEY, email))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.USER), new SimpleGrantedAuthority(AuthoritiesConstants.PATIENT));
    }

    private static RequestPostProcessor angel() {
        return jwt()
            .jwt(builder -> builder.claim(SecurityUtils.EMAIL_KEY, ANGEL_EMAIL))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.USER), new SimpleGrantedAuthority(AuthoritiesConstants.ANGEL));
    }

    private static RequestPostProcessor doctor() {
        return jwt()
            .jwt(builder -> builder.subject("dr-mensah").claim(SecurityUtils.EMAIL_KEY, "mensah@clinic.test"))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.DOCTOR));
    }

    private static RequestPostProcessor admin() {
        return jwt()
            .jwt(builder -> builder.subject("root").claim(SecurityUtils.EMAIL_KEY, "admin@example.test"))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN));
    }
}
