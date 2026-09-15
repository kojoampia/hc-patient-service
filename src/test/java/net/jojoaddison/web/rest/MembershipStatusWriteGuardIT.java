package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
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
 * inversion sharp: rewriting <em>one call site</em> in {@code PUT} to pass the requested value straight through — the
 * only way to lose the rule for a single field, now that {@code termForUpdate} is the one implementation of it —
 * reddens exactly one of these, while reverting that helper reddens all three. Item 27's closure is the reason for
 * the shape: a test-per-field suite there asserted only what the patient was refused, and would have certified a
 * "fix" that erased what an administrator had assigned. <strong>Assert the preserved value, not the refused
 * one.</strong></p>
 *
 * <p>Separate from {@link MembershipResourceIT}, which runs as {@code ROLE_ADMIN} and therefore cannot see this at
 * all: an administrator is precisely the caller the guard lets through. Mixing the two would mean changing that
 * class's caller and losing the CRUD coverage it exists for. That class does hold the {@code PATCH} half of item 30's
 * regression cover, though, in {@code partialUpdateMembershipWithPatch} — it asserts that an administrator's partial
 * update leaves an unmentioned {@code status}, {@code memberNumber} and {@code renewalDate} at their stored values,
 * which is why {@code PATCH} never had this defect. (Generated with that shape, though not with that status constant:
 * item 19 retyped {@code DEFAULT_STATUS} when {@code status} became an enum.)</p>
 *
 * <h2>And a patient may not stack pending choices, added 2026-09-15</h2>
 *
 * <p>{@code POST} guarded only against a client-supplied id, so every tap of CHOOSE wrote another {@code PENDING}
 * membership — while item 19's verifier applies an email-keyed acknowledgement to the patient's <em>single</em>
 * pending choice and refuses rather than guessing. Permissive creator, strict verifier, and nothing reconciling them:
 * a patient could put their own record into a state the system would then refuse to act on, and be told nothing.
 * Found in production on 2026-09-11; backlog item 40.</p>
 *
 * <p><strong>A second choice replaces the first rather than being refused</strong>, and the superseded membership is
 * moved to {@code CANCELLED} rather than deleted. The tests below assert <em>both</em> — the count of what is pending
 * and the survival of what is not — because a fix that deleted the spares would satisfy the count and lose the record
 * of what the patient asked for, which is the same "assert the preserved value, not the refused one" rule the
 * paragraph above was written for.</p>
 *
 * <p>These belong here rather than in a class of their own because the question they answer is the same one: which
 * caller may put a membership into which state, and whose records a write may reach. The cross-patient case is the
 * point of {@code supersedingOnlyEverTouchesTheSamePatientsMemberships}.</p>
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

    /** Somebody else entirely, so that a cross-patient write has a victim to be observed on. Item 40. */
    private static final String OTHER_PATIENT_EMAIL = "kofi@example.test";
    private static final String OTHER_PATIENT_ID = "patient-kofi";

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

    @Test
    void anAdministratorCreatingAMembershipWithNoStatusGetsPending() throws Exception {
        // The third helper, found by review of item 30 after the other two were fixed. statusOnCreate returned the
        // requested status verbatim for an administrator — null included — off the same blank <option [ngValue]="null">
        // on the same generated component, which posts when the form carries no id. A membership stored with no status
        // is in no MembershipStatus constant and matches nothing hc-admin filters on.
        //
        // The frame is the other half and is worse than the stored value: a creation announces unconditionally, so the
        // event went out with no status key at all. MembershipPlanEventIT.anAdministratorCreatingWithNoStatusStill-
        // AnnouncesOne reads that off a real topic; this asserts what was written here.
        String withNoStatus =
            """
            {"patientId":"%s","plan":"MELON","name":"MELON Plan","memberNumber":"%s"}
            """.formatted(PATIENT_ID, SENT_MEMBER_NUMBER);

        restMockMvc
            .perform(post(ENTITY_API_URL).with(administrator()).contentType(MediaType.APPLICATION_JSON).content(withNoStatus))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            // Still an administrator's creation in every other respect — the default is not a refusal in disguise.
            .andExpect(jsonPath("$.memberNumber").value(SENT_MEMBER_NUMBER));

        Membership stored = membershipRepository
            .findByPatientId(PATIENT_ID)
            .stream()
            .filter(m -> "MELON".equals(m.getPlan()))
            .findFirst()
            .orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(MembershipStatus.PENDING);
        assertThat(stored.getMemberNumber()).isEqualTo(SENT_MEMBER_NUMBER);
    }

    @Test
    void anAdministratorCreatingAnActiveMembershipStillGetsActive() throws Exception {
        // The default must not swallow a decision an administrator actually made. Distinct from
        // anAdministratorMayStillAssignThem, which sends the terms and no status at all.
        restMockMvc
            .perform(
                post(ENTITY_API_URL)
                    .with(administrator())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new Membership().patientId(PATIENT_ID).plan("MELON").name("MELON Plan").status(MembershipStatus.ACTIVE)))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void aSecondChoiceReplacesThePendingOneRatherThanStackingOnIt() throws Exception {
        // Item 40, from a live incident on 2026-09-11. Every tap of CHOOSE used to write another PENDING membership,
        // and item 19's verifier then refused all of them for ever — "who holds 3 PENDING memberships; refusing
        // rather than choosing one" — while the patient's app said "Awaiting confirmation" and nothing else was
        // wrong. Replacing rather than refusing with a 409 is the architect's decision: a patient who picked the
        // wrong tier would otherwise be stuck until an administrator acted.
        restMockMvc
            .perform(
                post(ENTITY_API_URL)
                    .with(patient())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new Membership().plan("MELON").name("MELON Plan")))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"));

        // Exactly one PENDING, and it is the new choice — which is the consumer's whole precondition.
        assertThat(pendingFor(PATIENT_ID)).singleElement().extracting(Membership::getPlan).isEqualTo("MELON");

        // NOT DELETED. Nothing patient-owned is deleted in this service, and the superseded record is the evidence of
        // what the patient asked for and when. Asserting only the PENDING count would certify a fix that erased it.
        Membership superseded = membershipRepository.findById(pending.getId()).orElseThrow();
        assertThat(superseded.getStatus()).isEqualTo(MembershipStatus.CANCELLED);
        assertThat(superseded.getPlan()).as("the superseded record is intact, not blanked").isEqualTo("PAWPAW");
    }

    @Test
    void choosingAPlanDoesNotCancelThePlanTheyAlreadyHold() throws Exception {
        // The compare-and-set, and the reason the sweep is not simply "cancel this patient's other memberships".
        // Drop `status == PENDING` from the criterion and choosing an upgrade cancels the subscription in force and
        // the whole history with it — a year of care taken away by a tap, silently, which is the same class of harm
        // as the self-chosen renewalDate item 18's review stripped. It is also what makes each document's move a
        // conditional write rather than a read-then-save: an administrator deciding one of these in the gap keeps
        // their decision.
        Membership inForce = assigned();
        Membership lapsed = membershipRepository.save(
            new Membership().patientId(PATIENT_ID).plan("KUBE").name("KUBE Plan").status(MembershipStatus.EXPIRED)
        );

        choose("MELON");

        assertThat(membershipRepository.findById(inForce.getId()).orElseThrow().getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(membershipRepository.findById(lapsed.getId()).orElseThrow().getStatus()).isEqualTo(MembershipStatus.EXPIRED);
        // And the pending one it WAS about did move, so the two assertions above are not passing because the sweep
        // never ran.
        assertThat(membershipRepository.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(MembershipStatus.CANCELLED);
    }

    @Test
    void aThirdChoiceStillLeavesExactlyOnePendingMembership() throws Exception {
        // The incident had three, so three is the case. The sweep cancels EVERY other pending membership rather than
        // one, which is also what makes a record that is already stacked repair itself the next time its owner
        // chooses — the reason the migration is a convenience rather than the only way out.
        choose("MELON");
        choose("KUBE");

        assertThat(pendingFor(PATIENT_ID)).singleElement().extracting(Membership::getPlan).isEqualTo("KUBE");
        assertThat(membershipRepository.findByPatientId(PATIENT_ID)).as("three choices, three records, none deleted").hasSize(3);
    }

    @Test
    void supersedingOnlyEverTouchesTheSamePatientsMemberships() throws Exception {
        // PatientScope decides whose record a write touches, and the sweep has to inherit that rather than reason
        // about it separately. An administrator creating a membership for one patient must not cancel another
        // patient's pending choice — which is a cross-patient write, the class of defect this service's whole
        // authorization model exists to make impossible.
        profileRepository.save(new Profile().email(OTHER_PATIENT_EMAIL).patientId(OTHER_PATIENT_ID));
        Membership somebodyElses = membershipRepository.save(
            new Membership().patientId(OTHER_PATIENT_ID).plan("KUBE").name("KUBE Plan").status(MembershipStatus.PENDING)
        );

        restMockMvc
            .perform(
                post(ENTITY_API_URL)
                    .with(administrator())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new Membership().patientId(PATIENT_ID).plan("MELON").name("MELON Plan")))
            )
            .andExpect(status().isCreated());

        assertThat(membershipRepository.findById(somebodyElses.getId()).orElseThrow().getStatus())
            .as("another patient's pending choice was cancelled by a write that had nothing to do with them")
            .isEqualTo(MembershipStatus.PENDING);
        // And the write did do its job for the patient it was about, so the assertion above is not passing because
        // the sweep never ran at all.
        assertThat(membershipRepository.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(MembershipStatus.CANCELLED);
    }

    @Test
    void anAdministratorCreatingAnActiveMembershipLeavesThePendingChoiceAlone() throws Exception {
        // The invariant is "at most one PENDING", not "at most one membership". Whether an approval granted elsewhere
        // moots a request the patient made is a back-office judgement, and the verifier is satisfied either way —
        // it still finds exactly one pending membership.
        restMockMvc
            .perform(
                post(ENTITY_API_URL)
                    .with(administrator())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new Membership().patientId(PATIENT_ID).plan("MELON").name("MELON Plan").status(MembershipStatus.ACTIVE)))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(membershipRepository.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(MembershipStatus.PENDING);
    }

    @Test
    void anAdministratorSendingAMembershipBackToPendingSupersedesTheOtherOne() throws Exception {
        // PUT and PATCH can reach this state and POST is not the only door — an administrator moving an ACTIVE
        // membership back to PENDING is the second way into two pending choices. A patient cannot: their requested
        // status is discarded and the stored one carried over. This is why the rule is written on the persisted
        // status of every write rather than at the one call site that prompted it; that drift is recorded three
        // times in MembershipResource's own javadoc.
        Membership active = assigned();

        restMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, active.getId())
                    .with(administrator())
                    .contentType("application/merge-patch+json")
                    .content(json(new Membership().id(active.getId()).status(MembershipStatus.PENDING)))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(pendingFor(PATIENT_ID)).singleElement().extracting(Membership::getId).isEqualTo(active.getId());
        assertThat(membershipRepository.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(MembershipStatus.CANCELLED);
    }

    @Test
    void aMembershipWithNoOwnerSupersedesNothing() throws Exception {
        // PatientScope.requirePatientIdForWrite deliberately lets an unrestricted caller create a record with no
        // patientId — the reference-shaped entities need it. Without the guard, a sweep keyed on a null owner would
        // match EVERY ownerless pending membership in the collection and cancel the lot as though one patient held
        // them. Two survive here; without the guard the first would be CANCELLED.
        Membership firstOwnerless = membershipRepository.save(
            new Membership().plan("KUBE").name("KUBE Plan").status(MembershipStatus.PENDING)
        );

        restMockMvc
            .perform(
                post(ENTITY_API_URL)
                    .with(administrator())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new Membership().plan("MELON").name("MELON Plan")))
            )
            .andExpect(status().isCreated());

        assertThat(membershipRepository.findById(firstOwnerless.getId()).orElseThrow().getStatus()).isEqualTo(MembershipStatus.PENDING);
        assertThat(membershipRepository.findAll().stream().filter(m -> m.getPatientId() == null).toList())
            .as("both ownerless memberships are still pending — neither was attributed to the other's owner")
            .hasSize(2)
            .allMatch(m -> m.getStatus() == MembershipStatus.PENDING);
    }

    /** One tap of CHOOSE, exactly as both clients send it. */
    private void choose(String planCode) throws Exception {
        restMockMvc
            .perform(
                post(ENTITY_API_URL)
                    .with(patient())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new Membership().plan(planCode).name(planCode + " Plan").status(MembershipStatus.PENDING)))
            )
            .andExpect(status().isCreated());
    }

    /** What the inbound {@code patient-events-plan} consumer counts before it decides anything. */
    private List<Membership> pendingFor(String patientId) {
        return membershipRepository.findByPatientIdAndStatus(patientId, MembershipStatus.PENDING);
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
