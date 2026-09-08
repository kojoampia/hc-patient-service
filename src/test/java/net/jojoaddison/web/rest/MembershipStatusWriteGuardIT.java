package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Membership;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.repository.MembershipRepository;
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
 * A patient may not decide whether their own membership has been approved.
 *
 * <p>The lifecycle records an administrator's approval by moving a membership from {@code PENDING} to
 * {@code ACTIVE}. Until 2026-09-08 both {@code PUT} and {@code PATCH} copied {@code status} out of the request body
 * with no authority check, so the patient could make that transition themselves by echoing one word back at the
 * endpoint that had just sent it to them — over their own record, which both verbs already let them edit.</p>
 *
 * <p><strong>Every assertion here fails without the guard,</strong> which is the point of the class: it is not
 * asserting that a refusal happens somewhere, it is asserting the stored value after a request that used to work.
 * Delete the three {@code statusOnCreate}/{@code statusForUpdate} calls in {@link MembershipResource} and all five
 * turn red.</p>
 *
 * <p>Separate from {@link MembershipResourceIT}, which runs as {@code ROLE_ADMIN} and therefore cannot see this at
 * all: an administrator is precisely the caller the guard lets through. Mixing the two would mean changing that
 * class's caller and losing the CRUD coverage it exists for.</p>
 *
 * <p>Callers are built with {@code jwt()} rather than {@code @WithMockUser} for the reason {@code PatientScopeIT}
 * gives: the identity under test lives in the token's {@code email} claim, and {@code @WithMockUser} mints no token,
 * so the patient would resolve to nobody and be refused for the wrong reason.</p>
 */
@IntegrationTest
@AutoConfigureMockMvc
class MembershipStatusWriteGuardIT {

    private static final String PATIENT_EMAIL = "ama@example.test";
    private static final String PATIENT_ID = "patient-ama";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String ENTITY_API_URL = "/api/memberships";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private MockMvc restMockMvc;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    private Membership pending;

    @BeforeEach
    void setUp() {
        profileRepository.deleteAll();
        membershipRepository.deleteAll();

        profileRepository.save(new Profile().email(PATIENT_EMAIL).patientId(PATIENT_ID));
        pending = membershipRepository.save(new Membership().patientId(PATIENT_ID).plan("PAWPAW").name("PAWPAW Plan").status("PENDING"));
    }

    @Test
    void aPatientCannotPatchTheirOwnMembershipToActive() throws Exception {
        restMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, pending.getId())
                    .with(patient())
                    .contentType("application/merge-patch+json")
                    .content(json(new Membership().id(pending.getId()).status("ACTIVE")))
            )
            // Not a refusal: the request is honoured for whatever it may legitimately change, and the status it
            // asked for is simply not one of those things. Asserting a 4xx would pin behaviour this does not have.
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(membershipRepository.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void aPatientCannotPutTheirOwnMembershipToActive() throws Exception {
        // PUT replaces the document wholesale, so it is the verb that would slip past a guard written only for the
        // partial update. It carried status off the wire exactly as PATCH did.
        restMockMvc
            .perform(
                put(ENTITY_API_URL_ID, pending.getId())
                    .with(patient())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new Membership().id(pending.getId()).patientId(PATIENT_ID).plan("PAWPAW").status("ACTIVE")))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(membershipRepository.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void aPatientChoosingAPlanGetsAPendingMembershipWhateverTheyAskedFor() throws Exception {
        restMockMvc
            .perform(
                post(ENTITY_API_URL)
                    .with(patient())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new Membership().plan("MELON").name("MELON Plan").status("ACTIVE")))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void aPatientMayStillEditTheRestOfTheirMembership() throws Exception {
        // The guard must cost the patient nothing else. A rule that answered 403 to any payload mentioning status
        // would break the clients, which round-trip the whole document.
        restMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, pending.getId())
                    .with(patient())
                    .contentType("application/merge-patch+json")
                    .content(json(new Membership().id(pending.getId()).description("Renewed after the move").status("ACTIVE")))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("Renewed after the move"))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void anAdministratorApprovesIt() throws Exception {
        // The other half of the rule, and the reason the guard is not simply "status is immutable": approval has to
        // reach the record somehow, and this is the caller it comes from.
        restMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, pending.getId())
                    .with(administrator())
                    .contentType("application/merge-patch+json")
                    .content(json(new Membership().id(pending.getId()).status("ACTIVE")))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(membershipRepository.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo("ACTIVE");
    }

    private static RequestPostProcessor patient() {
        return jwt()
            .jwt(builder -> builder.claim(SecurityUtils.EMAIL_KEY, PATIENT_EMAIL))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.USER), new SimpleGrantedAuthority(AuthoritiesConstants.PATIENT));
    }

    private static RequestPostProcessor administrator() {
        return jwt()
            .jwt(builder -> builder.claim(SecurityUtils.EMAIL_KEY, "admin@example.test"))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN));
    }

    private static String json(Object value) throws Exception {
        return MAPPER.writeValueAsString(value);
    }
}
