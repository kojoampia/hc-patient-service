package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.security.AuthoritiesConstants;
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
 * {@code GET /api/profile/{accountId}} — backlog item 44's cross-product read.
 *
 * <h2>The authorization cases are the point of this class</h2>
 *
 * <p>An admin-gated endpoint that is not actually gated is the defect this item most plausibly ships, and it ships
 * green: the happy path and the 404 pass either way. The three refusals below are the tests that can tell the
 * difference, and each was watched failing with the {@code @PreAuthorize} removed before being watched pass with it
 * back — a 200 for a patient, a 200 for a clinician, and a 200 for a patient reading their <em>own</em> account id.</p>
 *
 * <p>The last of those is the one that would be argued about. There is deliberately no self-carve-out: a patient
 * reading their own record uses {@code /api/profiles/email/{email}}, which resolves the subject from the token and
 * can name nobody else. An endpoint whose path names a subject is administrative whoever the subject turns out to
 * be — comparing caller against path is the shape that gets it wrong.</p>
 *
 * <p>This matters more here than the authority name suggests. The three gateways share one signing key, so
 * "authenticated" over here is every account in hc-patient, hc-admin and hc-professional — which is exactly how a
 * carer came to be able to read a doctor's full profile in the sibling product.</p>
 */
@IntegrationTest
@AutoConfigureMockMvc
class ProfileByAccountResourceIT {

    private static final String API = "/api/profile";
    private static final String ACCOUNT_ID = "u-5f2c9a41";
    private static final String EMAIL = "ama@example.test";

    @Autowired
    private MockMvc restMockMvc;

    @Autowired
    private ProfileRepository profileRepository;

    private Profile stored;

    @BeforeEach
    void initTest() {
        profileRepository.deleteAll();
        stored =
            profileRepository.save(
                new Profile().patientId("patient-ama").accountId(ACCOUNT_ID).firstName("Ama").lastName("Mensah").email(EMAIL)
            );
    }

    // --- the read -------------------------------------------------------------------------------------------------

    @Test
    void anAdministratorReadsThePlainProfileBehindAnAccountId() throws Exception {
        restMockMvc
            .perform(get(API + "/{accountId}", ACCOUNT_ID).with(administrator()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(stored.getId()))
            .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID))
            // The plain profile, not a projection composed for one consumer — item 44 is explicit about that.
            .andExpect(jsonPath("$.firstName").value("Ama"))
            .andExpect(jsonPath("$.lastName").value("Mensah"))
            .andExpect(jsonPath("$.patientId").value("patient-ama"));
    }

    @Test
    void anAccountThisSubsystemHoldsNoProfileForIs404() throws Exception {
        // An ordinary answer rather than a failure: every clinician signing in through a sibling product is one.
        restMockMvc.perform(get(API + "/{accountId}", "u-nobody").with(administrator())).andExpect(status().isNotFound());
    }

    @Test
    void theProfilesOwnIdIsNotAnAccountIdAndDoesNotResolveHere() throws Exception {
        // Two identifier spaces, one path each. /api/profiles/{id} keeps meaning the document's own id, which is why
        // this path is singular and could not have been added to the plural one.
        restMockMvc.perform(get(API + "/{accountId}", stored.getId()).with(administrator())).andExpect(status().isNotFound());
    }

    // --- the gate -------------------------------------------------------------------------------------------------

    @Test
    void aPatientMayNotReadAnybodyByAccountId() throws Exception {
        restMockMvc.perform(get(API + "/{accountId}", ACCOUNT_ID).with(patient())).andExpect(status().isForbidden());
    }

    @Test
    void aPatientMayNotReadTheirOwnAccountIdEitherAndThatIsDeliberate() throws Exception {
        // Same caller, same record, their own account id. Refused, because the gate is on the shape of the path and
        // not on who happens to be behind it. /api/profiles/email/{email} is the read this caller has.
        restMockMvc.perform(get(API + "/{accountId}", ACCOUNT_ID).with(patientWhoOwnsIt())).andExpect(status().isForbidden());

        restMockMvc.perform(get("/api/profiles/email/{email}", EMAIL).with(patientWhoOwnsIt())).andExpect(status().isOk());
    }

    @Test
    void aClinicianMayNotReadByAccountIdEitherThoughPatientScopeCallsThemUnrestricted() throws Exception {
        // ROLE_DOCTOR is unrestricted in PatientScope — it reads every patient's clinical record — and is still not
        // ROLE_ADMIN. The two questions are different and this endpoint asks the second one.
        restMockMvc.perform(get(API + "/{accountId}", ACCOUNT_ID).with(clinician())).andExpect(status().isForbidden());
    }

    @Test
    void anUnauthenticatedCallerIsRefusedBeforeTheAuthorityIsEvenConsidered() throws Exception {
        restMockMvc.perform(get(API + "/{accountId}", ACCOUNT_ID)).andExpect(status().isUnauthorized());
    }

    // --- the field is not writable over HTTP ----------------------------------------------------------------------

    @Test
    void accountIdCannotBeSetByCreatingAProfile() throws Exception {
        restMockMvc
            .perform(
                post("/api/profiles")
                    .with(administrator())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"firstName\":\"Kofi\",\"lastName\":\"Boateng\",\"accountId\":\"u-stolen\"}")
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accountId").doesNotExist());

        // And nothing was quietly written under the covers either.
        assertThat(profileRepository.findOneByAccountId("u-stolen")).isEmpty();
    }

    @Test
    void aWholeDocumentReplaceCannotStealAnAccountIdAndCannotClearOne() throws Exception {
        restMockMvc
            .perform(
                put("/api/profiles/{id}", stored.getId())
                    .with(patientWhoOwnsIt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"id\":\"" +
                        stored.getId() +
                        "\",\"firstName\":\"Ama\",\"lastName\":\"Mensah\"," +
                        "\"email\":\"" +
                        EMAIL +
                        "\",\"accountId\":\"u-somebody-else\"}"
                    )
            )
            .andExpect(status().isOk());

        // PUT replaces the document wholesale and the payload can never carry this field, so without the carry-over
        // in ProfileResource an ordinary profile edit would unlink the patient — silently, and from the estate.
        assertThat(profileRepository.findById(stored.getId()).orElseThrow().getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(profileRepository.findOneByAccountId("u-somebody-else")).isEmpty();
    }

    // --- callers --------------------------------------------------------------------------------------------------

    private static RequestPostProcessor administrator() {
        return jwt()
            .jwt(builder -> builder.claim(SecurityUtils.EMAIL_KEY, "admin@example.test"))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.USER), new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN));
    }

    private static RequestPostProcessor patient() {
        return jwt()
            .jwt(builder -> builder.claim(SecurityUtils.EMAIL_KEY, "kofi@example.test"))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.USER), new SimpleGrantedAuthority(AuthoritiesConstants.PATIENT));
    }

    private static RequestPostProcessor patientWhoOwnsIt() {
        return jwt()
            .jwt(builder -> builder.claim(SecurityUtils.EMAIL_KEY, EMAIL))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.USER), new SimpleGrantedAuthority(AuthoritiesConstants.PATIENT));
    }

    private static RequestPostProcessor clinician() {
        return jwt()
            .jwt(builder -> builder.claim(SecurityUtils.EMAIL_KEY, "doctor@example.test"))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.USER), new SimpleGrantedAuthority(AuthoritiesConstants.DOCTOR));
    }
}
