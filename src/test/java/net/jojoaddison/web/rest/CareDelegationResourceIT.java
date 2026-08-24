package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.CareDelegation;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.enumeration.DelegationParty;
import net.jojoaddison.domain.enumeration.DelegationStatus;
import net.jojoaddison.repository.CareDelegationRepository;
import net.jojoaddison.repository.ProfileRepository;
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
 * The care-delegation lifecycle over HTTP.
 *
 * <p>Focused on the transitions rather than on CRUD, because there is no CRUD: every endpoint is one move in a state
 * machine, and the reason for that shape is that a generic update would let an angel make themselves active.</p>
 *
 * <p>Identities are built with {@code jwt()} rather than {@code @WithMockUser} for the same reason the
 * {@code PatientScope} tests are — the rules turn on the token's email claim, which a mock user does not carry.</p>
 */
@IntegrationTest
@AutoConfigureMockMvc
class CareDelegationResourceIT {

    private static final String PATIENT_EMAIL = "ama@example.test";
    private static final String PATIENT_ID = "ama-patient";
    private static final String ANGEL_EMAIL = "kofi@example.test";
    private static final String OTHER_EMAIL = "stranger@example.test";

    private static final String API = "/api/care-delegations";

    @Autowired
    private MockMvc restMockMvc;

    @Autowired
    private CareDelegationRepository careDelegationRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @BeforeEach
    void initTest() {
        careDelegationRepository.deleteAll();
        profileRepository.deleteAll();
        Profile patient = new Profile().patientId(PATIENT_ID).email(PATIENT_EMAIL).firstName("Ama");
        patient.setId("ama-profile");
        profileRepository.save(patient);
    }

    // --- the ordinary path ----------------------------------------------------------------------------------------

    @Test
    void anAngelAcceptsAndTheProfileCacheFollows() throws Exception {
        CareDelegation pending = save(DelegationStatus.PENDING);

        restMockMvc
            .perform(post(API + "/{id}/accept", pending.getId()).with(angel()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(reload(pending).getAcceptedAt()).isNotNull();
        assertThat(profileRepository.findOneByEmailIgnoreCase(PATIENT_EMAIL).orElseThrow().getCareAngelEmail())
            .as("the display cache follows the delegation")
            .isEqualTo(ANGEL_EMAIL);
    }

    @Test
    void onlyTheNominatedAngelMayAccept() throws Exception {
        CareDelegation pending = save(DelegationStatus.PENDING);

        restMockMvc.perform(post(API + "/{id}/accept", pending.getId()).with(patient(OTHER_EMAIL))).andExpect(status().isForbidden());
        // Not even the patient who nominated them. Accepting is the nominee agreeing to take on someone's medical
        // decisions, and nobody can agree to that on their behalf.
        restMockMvc.perform(post(API + "/{id}/accept", pending.getId()).with(patient(PATIENT_EMAIL))).andExpect(status().isForbidden());

        assertThat(reload(pending).getStatus()).isEqualTo(DelegationStatus.PENDING);
    }

    @Test
    void theNomineeMayDecline() throws Exception {
        CareDelegation pending = save(DelegationStatus.PENDING);

        restMockMvc.perform(post(API + "/{id}/decline", pending.getId()).with(angel())).andExpect(status().isOk());

        assertThat(reload(pending).getStatus()).isEqualTo(DelegationStatus.DECLINED);
    }

    @Test
    void eitherPartyMayRevokeAndTheRecordSaysWhich() throws Exception {
        CareDelegation byPatient = save(DelegationStatus.ACTIVE);
        restMockMvc.perform(post(API + "/{id}/revoke", byPatient.getId()).with(patient(PATIENT_EMAIL))).andExpect(status().isOk());
        assertThat(reload(byPatient).getRevokedBy()).isEqualTo(DelegationParty.PATIENT);
        assertThat(profileRepository.findOneByEmailIgnoreCase(PATIENT_EMAIL).orElseThrow().getCareAngelEmail())
            .as("the cache is cleared when an active delegation ends")
            .isNull();

        CareDelegation byAngel = save(DelegationStatus.ACTIVE);
        restMockMvc.perform(post(API + "/{id}/revoke", byAngel.getId()).with(angel())).andExpect(status().isOk());
        assertThat(reload(byAngel).getRevokedBy()).isEqualTo(DelegationParty.ANGEL);
    }

    @Test
    void someoneWhoIsNeitherPartyCannotRevoke() throws Exception {
        CareDelegation active = save(DelegationStatus.ACTIVE);

        restMockMvc.perform(post(API + "/{id}/revoke", active.getId()).with(patient(OTHER_EMAIL))).andExpect(status().isForbidden());

        assertThat(reload(active).getStatus()).isEqualTo(DelegationStatus.ACTIVE);
    }

    @Test
    void aTerminalDelegationCannotBeReopened() throws Exception {
        CareDelegation revoked = save(DelegationStatus.REVOKED);

        restMockMvc.perform(post(API + "/{id}/revoke", revoked.getId()).with(patient(PATIENT_EMAIL))).andExpect(status().isBadRequest());
        restMockMvc.perform(post(API + "/{id}/accept", revoked.getId()).with(angel())).andExpect(status().isBadRequest());

        assertThat(reload(revoked).getStatus()).isEqualTo(DelegationStatus.REVOKED);
    }

    // --- the standby path -----------------------------------------------------------------------------------------

    @Test
    void aStandbyRowGrantsNothing() throws Exception {
        CareDelegation standby = standby(true);

        // The angel names the patient they are standby for. Dormant is not active, so this is refused exactly as if no
        // delegation existed — which is the property the whole standby design rests on.
        restMockMvc
            .perform(get("/api/allergies").header(PatientScope.ACTING_AS_HEADER, PATIENT_ID).with(angel()))
            .andExpect(status().isForbidden());

        assertThat(reload(standby).getStatus()).isEqualTo(DelegationStatus.STANDBY);
    }

    @Test
    void activationNeedsAProfessionalAReasonAndAdvanceConsent() throws Exception {
        CareDelegation standby = standby(true);

        // Not the patient, not the angel, and not an administrator either: countersigning an incapacity is a clinical
        // judgement, and ROLE_ADMIN is an operational role.
        restMockMvc.perform(activate(standby, "unwell").with(patient(PATIENT_EMAIL))).andExpect(status().isForbidden());
        restMockMvc.perform(activate(standby, "unwell").with(angel())).andExpect(status().isForbidden());
        restMockMvc.perform(activate(standby, "unwell").with(admin())).andExpect(status().isForbidden());

        // A professional, but with no declaration to record.
        restMockMvc.perform(activate(standby, "  ").with(professional("dr-one"))).andExpect(status().isBadRequest());

        assertThat(reload(standby).getStatus()).isEqualTo(DelegationStatus.STANDBY);
    }

    @Test
    void aStandbyRecordedWithoutConsentCannotBeActivated() throws Exception {
        CareDelegation withoutConsent = standby(false);

        restMockMvc.perform(activate(withoutConsent, "unresponsive").with(professional("dr-one"))).andExpect(status().isBadRequest());

        assertThat(reload(withoutConsent).getStatus()).isEqualTo(DelegationStatus.STANDBY);
    }

    /**
     * The two-signature control, and the single check the whole standby path rests on.
     *
     * <p>If this ever passes with the same professional twice, one clinician can ripen a delegation alone and the
     * second signature is decorative.</p>
     */
    @Test
    void countersignRejectedWhenSameProfessionalAsRequester() throws Exception {
        CareDelegation standby = standby(true);
        restMockMvc.perform(activate(standby, "unresponsive on admission").with(professional("dr-one"))).andExpect(status().isOk());

        restMockMvc
            .perform(post(API + "/{id}/countersign", standby.getId()).with(professional("dr-one")))
            .andExpect(status().isBadRequest());

        assertThat(reload(standby).getStatus())
            .as("still waiting for a second signature")
            .isEqualTo(DelegationStatus.AWAITING_COUNTERSIGNATURE);
        assertThat(reload(standby).getCountersignedById()).isNull();
    }

    @Test
    void standbyReachesActiveOnlyThroughTwoProfessionalsAndTheNomineesConsent() throws Exception {
        CareDelegation standby = standby(true);

        restMockMvc.perform(activate(standby, "unresponsive on admission").with(professional("dr-one"))).andExpect(status().isOk());
        assertThat(reload(standby).getStatus()).isEqualTo(DelegationStatus.AWAITING_COUNTERSIGNATURE);
        assertThat(reload(standby).getActivationReason()).isEqualTo("unresponsive on admission");

        restMockMvc.perform(post(API + "/{id}/countersign", standby.getId()).with(professional("dr-two"))).andExpect(status().isOk());
        // PENDING, not ACTIVE. Two clinicians can make a nomination real; only the nominee can take on the role.
        assertThat(reload(standby).getStatus()).isEqualTo(DelegationStatus.PENDING);

        restMockMvc.perform(post(API + "/{id}/accept", standby.getId()).with(angel())).andExpect(status().isOk());
        assertThat(reload(standby).getStatus()).isEqualTo(DelegationStatus.ACTIVE);
    }

    @Test
    void aPatientMayRevokeWhileACountersignatureIsOutstanding() throws Exception {
        CareDelegation standby = standby(true);
        restMockMvc.perform(activate(standby, "unresponsive").with(professional("dr-one"))).andExpect(status().isOk());

        // A patient lucid enough to object is lucid enough not to need a standby ripened for them.
        restMockMvc.perform(post(API + "/{id}/revoke", standby.getId()).with(patient(PATIENT_EMAIL))).andExpect(status().isOk());

        assertThat(reload(standby).getStatus()).isEqualTo(DelegationStatus.REVOKED);
    }

    // --- the sign-in picker ---------------------------------------------------------------------------------------

    @Test
    void mineAnswersForACallerWithNoProfileOfTheirOwn() throws Exception {
        save(DelegationStatus.ACTIVE);

        // An angel who is not themselves a patient has no Profile at all, and this is the one call they make before
        // anything else. Resolving it through PatientScope would have nothing to resolve.
        restMockMvc
            .perform(get(API + "/mine").with(angel()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(ANGEL_EMAIL))
            .andExpect(jsonPath("$.self").isEmpty())
            .andExpect(jsonPath("$.delegations.length()").value(1))
            .andExpect(jsonPath("$.delegations[0].patientId").value(PATIENT_ID));
    }

    @Test
    void mineShowsAPatientTheirOwnIdentity() throws Exception {
        restMockMvc
            .perform(get(API + "/mine").with(patient(PATIENT_EMAIL)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.self.patientId").value(PATIENT_ID))
            .andExpect(jsonPath("$.delegations.length()").value(0));
    }

    // --- helpers --------------------------------------------------------------------------------------------------

    private CareDelegation save(DelegationStatus status) {
        return careDelegationRepository.save(
            new CareDelegation().patientId(PATIENT_ID).angelEmail(ANGEL_EMAIL).angelName("Kofi").status(status)
        );
    }

    private CareDelegation standby(boolean advanceConsent) {
        return careDelegationRepository.save(
            new CareDelegation()
                .patientId(PATIENT_ID)
                .angelEmail(ANGEL_EMAIL)
                .angelName("Kofi")
                .status(DelegationStatus.STANDBY)
                .advanceConsent(advanceConsent)
        );
    }

    private CareDelegation reload(CareDelegation delegation) {
        return careDelegationRepository.findById(delegation.getId()).orElseThrow();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder activate(
        CareDelegation delegation,
        String reason
    ) {
        return post(API + "/{id}/activate", delegation.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"" + reason + "\"}");
    }

    private static RequestPostProcessor angel() {
        return jwt()
            .jwt(builder -> builder.claim(SecurityUtils.EMAIL_KEY, ANGEL_EMAIL))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.USER), new SimpleGrantedAuthority(AuthoritiesConstants.ANGEL));
    }

    private static RequestPostProcessor patient(String email) {
        return jwt()
            .jwt(builder -> builder.claim(SecurityUtils.EMAIL_KEY, email))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.USER), new SimpleGrantedAuthority(AuthoritiesConstants.PATIENT));
    }

    private static RequestPostProcessor professional(String login) {
        return jwt()
            .jwt(builder -> builder.subject(login).claim(SecurityUtils.EMAIL_KEY, login + "@clinic.test"))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.DOCTOR));
    }

    private static RequestPostProcessor admin() {
        return jwt()
            .jwt(builder -> builder.claim(SecurityUtils.EMAIL_KEY, "admin@example.test"))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN));
    }
}
