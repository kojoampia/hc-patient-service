package net.jojoaddison.service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.jojoaddison.domain.Membership;
import net.jojoaddison.domain.PlanVerification;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.enumeration.MembershipStatus;
import net.jojoaddison.repository.PlanVerificationRepository;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.service.MembershipService;
import net.jojoaddison.service.event.PlanVerificationRefusedException.Reason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The rules an acknowledgement on {@code patient-events-plan} has to pass, one at a time.
 *
 * <h2>Each guarded thing is mutated separately, and asserted on its reason</h2>
 *
 * <p>"It refused" is one assertion and the refusals are not interchangeable. Two pending memberships and a plan that
 * disagrees are the pair this consumer could most easily confuse — both end in a throw, both mention a plan — so a
 * test matching on message text would pass with either wired to the other. Asserting {@link Reason} is what makes
 * these nine tests nine tests.</p>
 *
 * <p>Written against the handler with the repositories mocked, deliberately. What is under test here is the rule; that
 * the rule is reachable from a real topic at all is {@code PlanVerificationConsumerBindingIT}'s question, and that a
 * refusal does not take the binding with it is {@code PlanVerificationRoundTripIT}'s. Backlog item 19.</p>
 */
class PlanVerificationConsumerTest {

    private static final String EMAIL = "Ama.Plan@Example.Test";
    private static final String LOWERCASED = "ama.plan@example.test";
    private static final String PATIENT_ID = "patient-ama-plan";
    private static final String MEMBERSHIP_ID = "membership-1";
    private static final String PLAN = "PAWPAW";

    private MembershipService membershipService;
    private ProfileRepository profiles;
    private PlanVerificationRepository verifications;
    private PlanVerificationConsumer consumer;

    @BeforeEach
    void setUp() {
        membershipService = mock(MembershipService.class);
        profiles = mock(ProfileRepository.class);
        verifications = mock(PlanVerificationRepository.class);
        consumer = new PlanVerificationConsumer(membershipService, profiles, verifications);

        when(verifications.existsById(anyString())).thenReturn(false);
        when(profiles.findOneByEmailIgnoreCase(LOWERCASED))
            .thenReturn(Optional.of(new Profile().id("profile-1").patientId(PATIENT_ID).email(EMAIL)));
        when(membershipService.pendingFor(PATIENT_ID)).thenReturn(List.of(pending(MEMBERSHIP_ID, PLAN)));
        when(membershipService.activateIfPending(MEMBERSHIP_ID))
            .thenReturn(Optional.of(pending(MEMBERSHIP_ID, PLAN).status(MembershipStatus.ACTIVE)));
    }

    private static Membership pending(String id, String plan) {
        return new Membership().id(id).patientId(PATIENT_ID).plan(plan).name(plan + " Plan").status(MembershipStatus.PENDING);
    }

    /** An acknowledgement in the shape item 19 settled: subject the email, payload one field. */
    private static PatientEvent acknowledgement(Map<String, Object> data) {
        return new PatientEvent(
            UUID.randomUUID().toString(),
            "PlanVerified",
            PatientEvent.VERSION,
            Instant.now(),
            "hcAdminService",
            new PatientEvent.Subject(EMAIL, null, null),
            data
        );
    }

    private static PatientEvent acknowledgement() {
        return acknowledgement(Map.of("plan", PLAN));
    }

    // ---------------------------------------------------------------------------------------------------------
    // The one that must work
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void aValidAcknowledgementActivatesTheSinglePendingMembership() {
        consumer.apply(acknowledgement());

        // Through activateIfPending, not through update: the transition is a compare-and-set, so the held status is
        // the write's own criterion rather than something this consumer read a moment earlier and hoped was still
        // true. MembershipService.activateIfPending is where that argument lives.
        verify(membershipService).activateIfPending(MEMBERSHIP_ID);
    }

    @Test
    void applyingItAnnouncesWithoutTheConsumerPublishingAnything() {
        // The announcement is inherited rather than added — item 27 built the seam so that this consumer would not
        // need a call site of its own, and this asserts the consumer did not grow one. It holds no publisher at all,
        // so the only way a PlanChosen goes out is the write path it shares with PUT, PATCH and POST.
        assertThat(PlanVerificationConsumer.class.getDeclaredFields())
            .as("a publisher on this class would be a second copy of a rule that already has a home")
            .noneMatch(field -> PatientEventPublisher.class.equals(field.getType()));

        consumer.apply(acknowledgement());

        verify(membershipService).activateIfPending(MEMBERSHIP_ID);
    }

    @Test
    void applyingItRecordsTheVerification() {
        consumer.apply(acknowledgement());

        // The audit record item 19 left unplaced — VERIFIED is "who approved and when", never a live status — and the
        // idempotency ledger, which is the same document because the event id is the key of both questions.
        verify(verifications).insert(any(PlanVerification.class));
    }

    // ---------------------------------------------------------------------------------------------------------
    // Idempotency
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void aReplayOfTheSameEventIdWritesNothingAndAnnouncesNothing() {
        PatientEvent event = acknowledgement();
        when(verifications.existsById(event.eventId())).thenReturn(true);

        consumer.apply(event);

        // Neither again, which is the whole requirement: delivery is at least once, so this is the normal case rather
        // than the exceptional one.
        verify(membershipService, never()).activateIfPending(anyString());
        verify(verifications, never()).insert(any(PlanVerification.class));
    }

    @Test
    void aReplayIsIgnoredRatherThanRefused() {
        PatientEvent event = acknowledgement();
        when(verifications.existsById(event.eventId())).thenReturn(true);

        // Not a throw: dead-lettering a redelivery would fill the DLQ with successes, and an operator reading it
        // would be reading a queue of things that worked.
        consumer.apply(event);
    }

    // ---------------------------------------------------------------------------------------------------------
    // The refusals, one apiece
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void anAcknowledgementWithNoEventIdIsRefused() {
        PatientEvent event = new PatientEvent(
            null,
            "PlanVerified",
            PatientEvent.VERSION,
            Instant.now(),
            "hcAdminService",
            new PatientEvent.Subject(EMAIL, null, null),
            Map.of("plan", PLAN)
        );

        assertRefusedWith(Reason.NO_EVENT_ID, event);
    }

    @Test
    void anAcknowledgementWithNoSubjectEmailIsRefused() {
        PatientEvent event = new PatientEvent(
            UUID.randomUUID().toString(),
            "PlanVerified",
            PatientEvent.VERSION,
            Instant.now(),
            "hcAdminService",
            new PatientEvent.Subject("  ", null, null),
            Map.of("plan", PLAN)
        );

        // The shape PatientEventPublisher refuses to send, refused on the way in for the same reason: a frame keyed
        // on nothing names nobody.
        assertRefusedWith(Reason.NO_SUBJECT_KEY, event);
    }

    @Test
    void anAcknowledgementNamingNoPlanIsRefused() {
        // The payload is one field. A frame without it is not a smaller version of the contract, it is a frame whose
        // consistency check cannot be made at all.
        assertRefusedWith(Reason.NO_PLAN_NAMED, acknowledgement(Map.of("membershipId", MEMBERSHIP_ID)));
    }

    @Test
    void anUnknownEmailIsRefused() {
        when(profiles.findOneByEmailIgnoreCase(LOWERCASED)).thenReturn(Optional.empty());

        assertRefusedWith(Reason.UNKNOWN_PATIENT, acknowledgement());
        verify(membershipService, never()).activateIfPending(anyString());
    }

    @Test
    void aPatientWithNoPendingMembershipIsRefused() {
        when(membershipService.pendingFor(PATIENT_ID)).thenReturn(List.of());

        assertRefusedWith(Reason.NO_PENDING_MEMBERSHIP, acknowledgement());
    }

    @Test
    void aPatientWithTwoPendingMembershipsIsRefusedRatherThanGuessedAt() {
        when(membershipService.pendingFor(PATIENT_ID)).thenReturn(List.of(pending(MEMBERSHIP_ID, PLAN), pending("membership-2", PLAN)));

        // Item 19's decision, and the reason for it: silently picking one of two pending memberships activates a year
        // of care nobody sold. Note both are on the same plan, so the consistency check cannot be what saves this —
        // the uniqueness rule has to.
        assertRefusedWith(Reason.MORE_THAN_ONE_PENDING_MEMBERSHIP, acknowledgement());
        verify(membershipService, never()).activateIfPending(anyString());
    }

    @Test
    void anAcknowledgementNamingADifferentPlanIsRefused() {
        when(membershipService.pendingFor(PATIENT_ID)).thenReturn(List.of(pending(MEMBERSHIP_ID, "MELON")));

        // The whole reason `plan` is in the payload: it carries no new information, and its use is turning a silent
        // mismatch into a stop. Delete the check and this test is what fails.
        assertRefusedWith(Reason.PLAN_DISAGREES, acknowledgement());
        verify(membershipService, never()).activateIfPending(anyString());
    }

    @Test
    void aStatusThisServiceHasNoConstantForIsRefusedWithoutKillingTheBinding() {
        // THE LIVE HAZARD. hc-admin's item 54 still records this repo's enum as six values including VERIFIED; five
        // shipped. A console built from their entry sends a decision that means nothing here.
        PatientEvent event = acknowledgement(Map.of("plan", PLAN, "status", "VERIFIED"));

        PlanVerificationRefusedException refusal = assertRefusedWith(Reason.STATUS_NOT_IN_THIS_SERVICES_VOCABULARY, event);
        assertThat(refusal).hasMessageContaining("VERIFIED").hasMessageContaining("ACTIVE");

        // And the binding survives it: the very next frame is applied. A refusal is an exception the binder retries
        // and dead-letters, never a subscription that stops.
        consumer.apply(acknowledgement());
        verify(membershipService).activateIfPending(MEMBERSHIP_ID);
    }

    @Test
    void aStatusThatIsRecognisedButIsNotAnActivationIsRefused() {
        // Distinct from the test above on purpose: CANCELLED maps perfectly well and is still not what this exchange
        // carries. Applying an arbitrary status because a sibling asked would be a larger grant than the contract and
        // would route around the HTTP write guard entirely.
        assertRefusedWith(Reason.STATUS_NOT_AN_ACTIVATION, acknowledgement(Map.of("plan", PLAN, "status", "CANCELLED")));
        verify(membershipService, never()).activateIfPending(anyString());
    }

    @Test
    void aMembershipThatStoppedBeingPendingUnderneathIsRefusedRatherThanOverwritten() {
        // The conditional update matched nothing, so somebody else moved it — an administrator cancelling it, most
        // likely. Their decision is real and this one is stale. Overwriting was item 30's defect with a sibling
        // product holding the pen.
        when(membershipService.activateIfPending(MEMBERSHIP_ID)).thenReturn(Optional.empty());

        assertRefusedWith(Reason.RACED_BY_ANOTHER_WRITER, acknowledgement());
        verify(verifications, never()).insert(any(PlanVerification.class));
    }

    @Test
    void aRefusedAcknowledgementIsNotRecordedAsApplied() {
        when(membershipService.pendingFor(PATIENT_ID)).thenReturn(List.of());

        assertRefusedWith(Reason.NO_PENDING_MEMBERSHIP, acknowledgement());

        // Load-bearing rather than incidental. Were a refusal written to the ledger, the first attempt would swallow
        // its own retries — the second delivery would read as a replay and be ignored — and the frame would never
        // reach the dead-letter queue at all.
        verify(verifications, never()).insert(any(PlanVerification.class));
    }

    // ---------------------------------------------------------------------------------------------------------
    // The payload shapes the record describes and hc-admin has not built
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void thePlanMayArriveAsAnObjectWithACodeOrAsAFlatString() {
        // Item 18 defines Plan as a commercial object with a code; item 19 says what comes back is "the planCode this
        // repo sent", which is flat. hc-admin has written neither, so both are read. This test is the reminder to
        // collapse it to whichever they ship.
        consumer.apply(acknowledgement(Map.of("plan", Map.of("code", PLAN, "name", "PAWPAW Plan"))));
        verify(membershipService).activateIfPending(MEMBERSHIP_ID);
    }

    @Test
    void thePlanMayArriveUnderThePlanCodeSpellingThisRepoPublishes() {
        consumer.apply(acknowledgement(Map.of("planCode", PLAN)));
        verify(membershipService).activateIfPending(MEMBERSHIP_ID);
    }

    @Test
    void aPlanThatDiffersOnlyInCasingIsNotAMismatch() {
        // A difference of spelling rather than of meaning. Refusing on it would be a refusal that taught nobody
        // anything, and the refusal exists to catch a plan that is genuinely not the one held.
        consumer.apply(acknowledgement(Map.of("plan", "pawpaw")));
        verify(membershipService).activateIfPending(MEMBERSHIP_ID);
    }

    /**
     * That this frame was refused, and refused for the stated reason.
     *
     * <p>Written as a try/catch rather than through {@code assertThatThrownBy} because the reason has to be read off
     * the exception itself: "it threw" is the assertion these tests must <em>not</em> settle for, since every one of
     * them throws and the point is which.</p>
     */
    private PlanVerificationRefusedException assertRefusedWith(Reason expected, PatientEvent event) {
        PlanVerificationRefusedException refusal = null;
        try {
            consumer.apply(event);
        } catch (PlanVerificationRefusedException caught) {
            refusal = caught;
        }

        assertThat(refusal).as("expected a refusal for %s and the acknowledgement was applied instead", expected).isNotNull();
        assertThat(refusal.getReason()).as("refused, but for the wrong reason — the log would name the wrong thing").isEqualTo(expected);
        return refusal;
    }
}
