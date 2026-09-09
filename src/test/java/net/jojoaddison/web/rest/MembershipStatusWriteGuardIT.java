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
 * <p><strong>The patient cases fail without the guard,</strong> which is the point of the class: it is not asserting
 * that a refusal happens somewhere, it is asserting the stored value after a request that used to work. Revert
 * {@link MembershipResource} to its pre-guard state and they turn red with
 * {@code JSON path "$.status" expected:<PENDING> but was:<ACTIVE>} — measured, not assumed.</p>
 *
 * <p>The administrator cases pass either way <b>by design</b>: they are the path the guard exists to let through.
 * Said explicitly because an earlier version of this comment claimed every test turns red, and a doc comment that
 * overstates its own coverage is the same defect as a test that reports success without having looked — the next
 * reader counts the red ones, comes up short, and doubts the guard rather than the sentence. For the same reason this
 * paragraph no longer gives a number: it named one, tests were added, and the number went stale within the week.</p>
 *
 * <h2>The subscription terms, added 2026-09-09</h2>
 *
 * <p>{@code memberNumber} and {@code renewalDate} are the same rule and were guarded on {@code POST} alone until
 * review of backlog item 27 — so a patient could not issue themselves a membership number at creation and could
 * issue themselves one a second later. That is the third time in this repo a rule has been written for one verb and
 * missed the next; it is the reason {@link net.jojoaddison.service.MembershipService} exists as a seam.</p>
 *
 * <p>They are <b>carried over from the stored document rather than nulled</b>, unlike on {@code POST}, and
 * {@code aPatientEditingTheirMembershipDoesNotEraseTheTermsAnAdministratorAssigned} is the test that pins the
 * difference: {@code PUT} replaces wholesale, so nulling would trade a privilege escalation for silent data loss.</p>
 *
 * <h2>And the administrator cannot erase any of the three, added 2026-09-09</h2>
 *
 * <p>Both helpers returned the requested value verbatim for an administrator, and {@code PUT} replaces the document
 * wholesale — so a body that did not name {@code status}, {@code memberNumber} or {@code renewalDate} wrote a null
 * over each. One click on the generated admin screen did it: {@code membership-update.component.html} gives the
 * status {@code <select>} a blank {@code <option [ngValue]="null">} and the component {@code PUT}s the whole form.
 * Backlog item 30. All three are carried over from the stored document now when the request does not name them,
 * whoever the caller is.</p>
 *
 * <p><strong>Each field is pinned by its own test, and each test names the other two.</strong> A body omitting all
 * three would make one assertion three times over and could not tell which field the guard actually covers — so the
 * test for each field sends real values for the other two and asserts those landed. That is also what makes the
 * inversion sharp: reverting the carry-over at one call site reddens exactly one of these. Item 27's closure is the
 * reason for the shape — a test-per-field suite there asserted only what the patient was refused, and would have
 * certified a "fix" that erased what an administrator had assigned. <strong>Assert the preserved value, not the
 * refused one.</strong></p>
 *
 * <p>Separate from {@link MembershipResourceIT}, which runs as {@code ROLE_ADMIN} and therefore cannot see this at
 * all: an administrator is precisely the caller the guard lets through. Mixing the two would mean changing that
 * class's caller and losing the CRUD coverage it exists for. That class does hold the {@code PATCH} half of item 30's
 * regression cover, though, in {@code partialUpdateMembershipWithPatch} — it has asserted since it was generated that
 * an administrator's partial update leaves an unmentioned {@code status}, {@code memberNumber} and
 * {@code renewalDate} at their stored values, which is why {@code PATCH} never had this defect.</p>
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

    /** What the back office has already decided about the membership in {@link #assigned()}. */
    private static final String ASSIGNED_MEMBER_NUMBER = "MBR-00042";
    private static final LocalDate ASSIGNED_RENEWAL_DATE = LocalDate.parse("2027-01-31");

    /** What an administrator sends instead, so that a preserved value is never also the requested one. */
    private static final String SENT_MEMBER_NUMBER = "MBR-00099";
    private static final LocalDate SENT_RENEWAL_DATE = LocalDate.parse("2028-03-31");

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
    void aPatientCannotPutThemselvesAMemberNumberOrARenewalDate() throws Exception {
        // POST has stripped these since 2026-09-08 and PUT did not, so the guard was true of one verb and false of
        // the other for a day — the same drift this repo has now hit three times, and the one PatientEventType's
        // javadoc was asserting had been closed. Found by review of item 27.
        restMockMvc
            .perform(
                put(ENTITY_API_URL_ID, pending.getId())
                    .with(patient())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            new Membership()
                                .id(pending.getId())
                                .patientId(PATIENT_ID)
                                .plan("PAWPAW")
                                .memberNumber("MBR-00001")
                                .renewalDate(LocalDate.parse("2099-12-31"))
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.memberNumber").doesNotExist())
            .andExpect(jsonPath("$.renewalDate").doesNotExist());

        Membership stored = membershipRepository.findById(pending.getId()).orElseThrow();
        assertThat(stored.getMemberNumber()).isNull();
        assertThat(stored.getRenewalDate()).isNull();
    }

    @Test
    void aPatientCannotPatchThemselvesAMemberNumberOrARenewalDate() throws Exception {
        restMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, pending.getId())
                    .with(patient())
                    .contentType("application/merge-patch+json")
                    .content(
                        json(new Membership().id(pending.getId()).memberNumber("MBR-00001").renewalDate(LocalDate.parse("2099-12-31")))
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.memberNumber").doesNotExist())
            .andExpect(jsonPath("$.renewalDate").doesNotExist());
    }

    @Test
    void aPatientEditingTheirMembershipDoesNotEraseTheTermsAnAdministratorAssigned() throws Exception {
        // The reason the guard carries the stored value over instead of nulling it, which is what POST does. PUT
        // replaces the document wholesale, so a guard copied from POST would turn a privilege escalation into silent
        // data loss: the patient renames their membership and the number and renewal date they were sold vanish.
        Membership assigned = membershipRepository.save(
            new Membership()
                .patientId(PATIENT_ID)
                .plan("PAWPAW")
                .name("PAWPAW Plan")
                .status(MembershipStatus.ACTIVE)
                .memberNumber("MBR-00042")
                .renewalDate(LocalDate.parse("2027-01-31"))
        );

        restMockMvc
            .perform(
                put(ENTITY_API_URL_ID, assigned.getId())
                    .with(patient())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new Membership().id(assigned.getId()).patientId(PATIENT_ID).plan("PAWPAW").name("Our family plan")))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Our family plan"))
            .andExpect(jsonPath("$.memberNumber").value("MBR-00042"))
            .andExpect(jsonPath("$.renewalDate").value("2027-01-31"));

        Membership stored = membershipRepository.findById(assigned.getId()).orElseThrow();
        assertThat(stored.getMemberNumber()).isEqualTo("MBR-00042");
        assertThat(stored.getRenewalDate()).isEqualTo(LocalDate.parse("2027-01-31"));
    }

    @Test
    void anAdministratorMayStillAssignThemOnAnUpdate() throws Exception {
        // The other half, for the same reason anAdministratorMayStillAssignThem exists on POST: assigning a member
        // number IS the back-office action item 18's event exists to prompt, and it must stay possible after
        // creation — item 17 records that nothing assigns one at creation time on the path a patient uses.
        restMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, pending.getId())
                    .with(administrator())
                    .contentType("application/merge-patch+json")
                    .content(
                        json(new Membership().id(pending.getId()).memberNumber("MBR-00007").renewalDate(LocalDate.parse("2027-06-30")))
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.memberNumber").value("MBR-00007"))
            .andExpect(jsonPath("$.renewalDate").value("2027-06-30"));
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

    @Test
    void anAdministratorPuttingNoStatusKeepsTheStoredStatus() throws Exception {
        Membership stored = assigned();

        // Written out rather than serialised from a fixture, for two reasons. TestUtil's mapper is NON_EMPTY and
        // would drop the key entirely; and an explicit null is what the generated admin screen actually sends, which
        // is the exact request this guard exists for. Both spellings bind to the same null, so the sibling tests
        // below use the fixture and omit their field.
        String clearingTheStatus =
            """
            {"id":"%s","patientId":"%s","plan":"PAWPAW","name":"PAWPAW Plan","status":null,
             "memberNumber":"%s","renewalDate":"%s"}
            """.formatted(stored.getId(), PATIENT_ID, SENT_MEMBER_NUMBER, SENT_RENEWAL_DATE);

        restMockMvc
            .perform(
                put(ENTITY_API_URL_ID, stored.getId())
                    .with(administrator())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(clearingTheStatus)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            // The rest of the request was honoured, so ACTIVE above is a value that survived rather than a request
            // that was thrown away.
            .andExpect(jsonPath("$.memberNumber").value(SENT_MEMBER_NUMBER));

        Membership after = membershipRepository.findById(stored.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(after.getMemberNumber()).isEqualTo(SENT_MEMBER_NUMBER);
        assertThat(after.getRenewalDate()).isEqualTo(SENT_RENEWAL_DATE);
    }

    @Test
    void anAdministratorPuttingNoMemberNumberKeepsTheStoredMemberNumber() throws Exception {
        Membership stored = assigned();

        restMockMvc
            .perform(
                put(ENTITY_API_URL_ID, stored.getId())
                    .with(administrator())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            new Membership()
                                .id(stored.getId())
                                .patientId(PATIENT_ID)
                                .plan("PAWPAW")
                                .name("PAWPAW Plan")
                                .status(MembershipStatus.SUSPENDED)
                                .renewalDate(SENT_RENEWAL_DATE)
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.memberNumber").value(ASSIGNED_MEMBER_NUMBER))
            .andExpect(jsonPath("$.status").value("SUSPENDED"));

        Membership after = membershipRepository.findById(stored.getId()).orElseThrow();
        assertThat(after.getMemberNumber()).isEqualTo(ASSIGNED_MEMBER_NUMBER);
        assertThat(after.getStatus()).isEqualTo(MembershipStatus.SUSPENDED);
        assertThat(after.getRenewalDate()).isEqualTo(SENT_RENEWAL_DATE);
    }

    @Test
    void anAdministratorPuttingNoRenewalDateKeepsTheStoredRenewalDate() throws Exception {
        // The quietest of the three and the one with no way back. A cleared status announces itself by the plan
        // disappearing from the patient's own screen; item 17 records that a real membership already shows "—" for
        // both terms, because choosePlan never assigns them — so an erased renewal date is invisible against the
        // normal case, and there is nothing in this service to restore it from.
        Membership stored = assigned();

        restMockMvc
            .perform(
                put(ENTITY_API_URL_ID, stored.getId())
                    .with(administrator())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            new Membership()
                                .id(stored.getId())
                                .patientId(PATIENT_ID)
                                .plan("PAWPAW")
                                .name("PAWPAW Plan")
                                .status(MembershipStatus.SUSPENDED)
                                .memberNumber(SENT_MEMBER_NUMBER)
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.renewalDate").value(ASSIGNED_RENEWAL_DATE.toString()))
            .andExpect(jsonPath("$.memberNumber").value(SENT_MEMBER_NUMBER));

        Membership after = membershipRepository.findById(stored.getId()).orElseThrow();
        assertThat(after.getRenewalDate()).isEqualTo(ASSIGNED_RENEWAL_DATE);
        assertThat(after.getMemberNumber()).isEqualTo(SENT_MEMBER_NUMBER);
        assertThat(after.getStatus()).isEqualTo(MembershipStatus.SUSPENDED);
    }

    @Test
    void anAdministratorMayStillDecideAllThreeOnAPut() throws Exception {
        // The other half, and the reason the guard turns on the value rather than on the caller: carrying a stored
        // value over must not become a lock. Deciding these IS the back-office action item 18's event exists to
        // prompt, and it happens on this verb as well as on PATCH.
        Membership stored = assigned();

        restMockMvc
            .perform(
                put(ENTITY_API_URL_ID, stored.getId())
                    .with(administrator())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            new Membership()
                                .id(stored.getId())
                                .patientId(PATIENT_ID)
                                .plan("PAWPAW")
                                .name("PAWPAW Plan")
                                .status(MembershipStatus.CANCELLED)
                                .memberNumber(SENT_MEMBER_NUMBER)
                                .renewalDate(SENT_RENEWAL_DATE)
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.memberNumber").value(SENT_MEMBER_NUMBER))
            .andExpect(jsonPath("$.renewalDate").value(SENT_RENEWAL_DATE.toString()));

        Membership after = membershipRepository.findById(stored.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(MembershipStatus.CANCELLED);
        assertThat(after.getMemberNumber()).isEqualTo(SENT_MEMBER_NUMBER);
        assertThat(after.getRenewalDate()).isEqualTo(SENT_RENEWAL_DATE);
    }

    @Test
    void anAdministratorsPatchStillLeavesTheThreeAloneWhenItDoesNotMentionThem() throws Exception {
        // PATCH never had the defect — its merge copies non-null fields only — and the carry-over must not have
        // changed that. It cannot: handing the stored value back to a merge that skips nulls writes it over itself.
        // Asserted here rather than only in MembershipResourceIT so that the two verbs' behaviour is readable in one
        // place, which is what the drift between them has cost this file three times.
        Membership stored = assigned();

        restMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, stored.getId())
                    .with(administrator())
                    .contentType("application/merge-patch+json")
                    .content(json(new Membership().id(stored.getId()).description("Moved to the family address")))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("Moved to the family address"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.memberNumber").value(ASSIGNED_MEMBER_NUMBER))
            .andExpect(jsonPath("$.renewalDate").value(ASSIGNED_RENEWAL_DATE.toString()));

        Membership after = membershipRepository.findById(stored.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(after.getMemberNumber()).isEqualTo(ASSIGNED_MEMBER_NUMBER);
        assertThat(after.getRenewalDate()).isEqualTo(ASSIGNED_RENEWAL_DATE);
    }

    @Test
    void anAdministratorClearsAMemberNumberBySayingSo() throws Exception {
        // What the carry-over leaves an administrator who really does want a wrongly-assigned number gone: send an
        // empty string, which is not null and so does not carry over. A body that says "" is a statement; a body
        // missing the key is an accident, and this endpoint can no longer tell one from the other any other way.
        // Note it stores "" rather than null — web renders that as an empty cell where it renders a true null as "—".
        //
        // renewalDate has no such escape: it is a LocalDate and there is no empty spelling of one, so after this
        // change nothing in the API can blank a renewal date. Correcting a wrong one still works; see termForUpdate.
        Membership stored = assigned();

        String clearingTheNumber =
            """
            {"id":"%s","patientId":"%s","plan":"PAWPAW","name":"PAWPAW Plan","status":"ACTIVE",
             "memberNumber":"","renewalDate":"%s"}
            """.formatted(stored.getId(), PATIENT_ID, ASSIGNED_RENEWAL_DATE);

        restMockMvc
            .perform(
                put(ENTITY_API_URL_ID, stored.getId())
                    .with(administrator())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(clearingTheNumber)
            )
            .andExpect(status().isOk());

        assertThat(membershipRepository.findById(stored.getId()).orElseThrow().getMemberNumber()).isEmpty();
    }

    /**
     * A membership the back office has already decided everything about, which is what an administrator's {@code PUT}
     * could erase. Deliberately not the {@code pending} fixture: a guard that preserves a null is not observable.
     */
    private Membership assigned() {
        return membershipRepository.save(
            new Membership()
                .patientId(PATIENT_ID)
                .plan("PAWPAW")
                .name("PAWPAW Plan")
                .status(MembershipStatus.ACTIVE)
                .memberNumber(ASSIGNED_MEMBER_NUMBER)
                .renewalDate(ASSIGNED_RENEWAL_DATE)
        );
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
