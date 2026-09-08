package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Membership;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.enumeration.MembershipStatus;
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
 * <p><strong>Four of the five fail without the guard,</strong> which is the point of the class: it is not asserting
 * that a refusal happens somewhere, it is asserting the stored value after a request that used to work. Revert
 * {@link MembershipResource} to its pre-guard state and the four patient cases turn red with
 * {@code JSON path "$.status" expected:<PENDING> but was:<ACTIVE>} — measured, not assumed.</p>
 *
 * <p>The fifth, {@code anAdministratorApprovesIt}, passes either way <b>by design</b>: it is the path the guard
 * exists to let through. Said explicitly because an earlier version of this comment claimed all five turn red, and a
 * doc comment that overstates its own coverage is the same defect as a test that reports success without having
 * looked — the next reader counts five red, sees four, and doubts the guard rather than the sentence.</p>
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
        pending =
            membershipRepository.save(
                new Membership().patientId(PATIENT_ID).plan("PAWPAW").name("PAWPAW Plan").status(MembershipStatus.PENDING)
            );
    }

    @Test
    void aPatientCannotPatchTheirOwnMembershipToActive() throws Exception {
        restMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, pending.getId())
                    .with(patient())
                    .contentType("application/merge-patch+json")
                    .content(json(new Membership().id(pending.getId()).status(MembershipStatus.ACTIVE)))
            )
            // Not a refusal: the request is honoured for whatever it may legitimately change, and the status it
            // asked for is simply not one of those things. Asserting a 4xx would pin behaviour this does not have.
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(membershipRepository.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(MembershipStatus.PENDING);
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
                    .content(
                        json(new Membership().id(pending.getId()).patientId(PATIENT_ID).plan("PAWPAW").status(MembershipStatus.ACTIVE))
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(membershipRepository.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(MembershipStatus.PENDING);
    }

    @Test
    void aPatientChoosingAPlanGetsAPendingMembershipWhateverTheyAskedFor() throws Exception {
        restMockMvc
            .perform(
                post(ENTITY_API_URL)
                    .with(patient())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new Membership().plan("MELON").name("MELON Plan").status(MembershipStatus.ACTIVE)))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void aPatientCannotIssueThemselvesAMemberNumberOrARenewalDate() throws Exception {
        // The same rule as the status, found later: a value a client may choose is a claim, not a record. Both are
        // back-office assignments and neither client's choosePlan sends them, so a patient posting them is asserting
        // a subscription term nobody granted — a self-chosen renewal date is a year of care they were not sold.
        // Until 2026-09-08 both were persisted exactly as posted, and a javadoc claimed the guard already existed.
        restMockMvc
            .perform(
                post(ENTITY_API_URL)
                    .with(patient())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            new Membership()
                                .plan("MELON")
                                .name("MELON Plan")
                                .memberNumber("MBR-00001")
                                .renewalDate(LocalDate.parse("2099-12-31"))
                        )
                    )
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.memberNumber").doesNotExist())
            .andExpect(jsonPath("$.renewalDate").doesNotExist());
    }

    @Test
    void anAdministratorMayStillAssignThem() throws Exception {
        // The other half, and the reason the guard is on the authority rather than on the field: assigning a member
        // number IS the back-office action item 18's event exists to prompt, so it must stay possible.
        restMockMvc
            .perform(
                post(ENTITY_API_URL)
                    .with(administrator())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            new Membership()
                                .patientId(PATIENT_ID)
                                .plan("MELON")
                                .name("MELON Plan")
                                .memberNumber("MBR-00001")
                                .renewalDate(LocalDate.parse("2099-12-31"))
                        )
                    )
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.memberNumber").value("MBR-00001"))
            .andExpect(jsonPath("$.renewalDate").value("2099-12-31"));
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
                    .content(
                        json(new Membership().id(pending.getId()).description("Renewed after the move").status(MembershipStatus.ACTIVE))
                    )
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
                    .content(json(new Membership().id(pending.getId()).status(MembershipStatus.ACTIVE)))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(membershipRepository.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(MembershipStatus.ACTIVE);
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

    /**
     * Serialises a fixture to the request body.
     *
     * <p>Uses {@link TestUtil}'s mapper rather than a bare {@code new ObjectMapper()}: the plain one has no JSR-310
     * module, so the moment a fixture carried a {@code renewalDate} it failed with "Java 8 date/time type not
     * supported" — a serialisation error in the test, nothing to do with the endpoint under test.</p>
     */
    private static String json(Object value) throws Exception {
        return new String(TestUtil.convertObjectToJsonBytes(value), java.nio.charset.StandardCharsets.UTF_8);
    }
}
