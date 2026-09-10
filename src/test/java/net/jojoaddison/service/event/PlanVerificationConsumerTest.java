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
import java.util.stream.Stream;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * The rules an acknowledgement on {@code patient-events-plan} has to pass, one at a time.
 *
 * <h2>Each guarded thing is mutated separately, and asserted on its reason</h2>
 *
 * <p>"It refused" is one assertion and the refusals are not interchangeable. Two pending memberships and a plan that
 * disagrees are the pair this consumer could most easily confuse — both end in a throw, both mention a plan — so a
 * test matching on message text would pass with either wired to the other. Asserting {@link Reason} is what keeps
 * one test per reason honest; there are twelve reasons and each is mutated separately.</p>
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

    /**
     * An acknowledgement in the shape hc-admin actually sends — read from their {@code PlanVerificationEvent} at
     * {@code ceb9eae}, not from item 19: subject the lowercased email, payload one field, type {@code PlanVerified}.
     */
    private static PatientEvent acknowledgement(Map<String, Object> data) {
        return new PatientEvent(
            UUID.randomUUID().toString(),
            PlanVerificationConsumer.PLAN_VERIFIED,
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

    /**
     * That a ledger write which fails does not dead-letter a frame that has already been applied.
     *
     * <h2>This test exists because the fix it guards was, for one commit, guarded by a paragraph</h2>
     *
     * <p>{@code record()} caught {@code DuplicateKeyException} and was documented as the safety net against this
     * half-apply. It was broadened to {@code RuntimeException} — and <b>nothing failed when it was narrowed
     * back</b>, so the correction was protected by prose in a method whose own javadoc tells the next reader that
     * nothing reaches it. Reverting the fix, or deleting the catch as dead code, was a silent green change.</p>
     *
     * <p><b>⚠ The reason originally given for the broadening was a misattribution, and it is corrected in
     * {@code PlanVerificationConsumer.record()} rather than here.</b> The short version: the exception seen escaping
     * under mutation came from the profile lookup in {@code apply()}, not from this insert. Read that javadoc before
     * citing this test as evidence of what {@code insert} throws — it does not show that, and nothing does.</p>
     *
     * <p><b>What this test does establish is the width of the catch, which is the part that matters.</b> Neither
     * exception below is a {@code DuplicateKeyException}, so both fail under the narrow catch; and they are on
     * either side of {@code DataAccessException}, so the obvious tidy-up — narrowing to
     * {@code catch (DataAccessException)}, which every exception named in the javadoc satisfies — fails on the
     * second. Without that second case the test pins "wider than {@code DuplicateKeyException}" and leaves the rest
     * of the width open.</p>
     *
     * <p>"Announced" is asserted as {@code activateIfPending} having been called: the announcement is that method's,
     * not this class's, and {@code MembershipStatusAnnouncementTest} is what pins it. This consumer holds no
     * publisher — {@link #applyingItAnnouncesWithoutTheConsumerPublishingAnything} asserts that too.</p>
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("ledgerWriteFailures")
    void aLedgerWriteThatFailsDoesNotUndoOrDeadLetterAnAppliedAcknowledgement(String name, RuntimeException failure) {
        when(verifications.insert(any(PlanVerification.class))).thenThrow(failure);

        // Must not propagate. Anything escaping here reaches the binder, which retries and dead-letters a frame that
        // has already written and announced — the one half-apply this consumer cannot undo, and the one whose replay
        // would activate a membership nobody verified.
        consumer.apply(acknowledgement());

        // And the work that was already done stands: written, and announced through the seam item 27 built.
        verify(membershipService).activateIfPending(MEMBERSHIP_ID);
        verify(verifications).insert(any(PlanVerification.class));
    }

    /**
     * The two failures, chosen to sit on either side of {@code DataAccessException}.
     *
     * <p>{@code DataAccessResourceFailureException} is a sibling of {@code DuplicateKeyException} under
     * {@code NonTransientDataAccessException} — neither is an ancestor of the other — and is a real Mongo
     * translation target: {@code MongoExceptionTranslator} maps socket, timeout and server-selection failures onto
     * it. That is the realistic way this insert fails.</p>
     *
     * <p>{@code IllegalStateException} is not a {@code DataAccessException} at all, and it is here for the tidy-up
     * rather than for realism: narrowing the catch to {@code DataAccessException} looks obviously correct — every
     * exception the javadoc names is one — and would pass with only the first case. A Mongo driver or Spring Data
     * upgrade throwing something outside the hierarchy would then dead-letter an applied frame, silently.</p>
     */
    private static Stream<Arguments> ledgerWriteFailures() {
        return Stream.of(
            Arguments.of(
                "a DataAccessException that is not a DuplicateKeyException",
                new DataAccessResourceFailureException("the database went away mid-write")
            ),
            Arguments.of(
                "a RuntimeException that is not a DataAccessException",
                new IllegalStateException("something outside the Spring data-access hierarchy")
            )
        );
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

    /**
     * That an administrator pressing verify twice does not dead-letter the second press.
     *
     * <p>hc-admin mints a <b>fresh event id</b> per press on purpose — republishing unconditionally is how they
     * recover a frame they think was lost, and <em>"two presses of the button must be two ids or the second is
     * silently ignored as a redelivery"</em>. So the ledger cannot suppress this one, and without the
     * already-satisfied check every repeat, and every successful recovery-republish, would refuse and fill the queue
     * that is supposed to hold only real refusals.</p>
     */
    @Test
    void aSecondVerificationForAMembershipAlreadyActiveOnThatPlanIsIgnored() {
        when(membershipService.pendingFor(PATIENT_ID)).thenReturn(List.of());
        when(membershipService.activeFor(PATIENT_ID)).thenReturn(List.of(pending(MEMBERSHIP_ID, PLAN).status(MembershipStatus.ACTIVE)));

        consumer.apply(acknowledgement());

        // Nothing written, nothing announced, nothing thrown — the state it asks for already holds.
        verify(membershipService, never()).activateIfPending(anyString());
        verify(verifications, never()).insert(any(PlanVerification.class));
    }

    @Test
    void anActiveMembershipOnAnotherPlanDoesNotSatisfyTheAcknowledgement() {
        // The guard on the guard. "Already done" is asked on the plan named, so a patient holding some other tier
        // does not silently absorb a verification that really is about a membership this service cannot find.
        when(membershipService.pendingFor(PATIENT_ID)).thenReturn(List.of());
        when(membershipService.activeFor(PATIENT_ID)).thenReturn(List.of(pending(MEMBERSHIP_ID, "MELON").status(MembershipStatus.ACTIVE)));

        assertRefusedWith(Reason.NO_PENDING_MEMBERSHIP, acknowledgement());
    }

    @Test
    void aPendingChoiceIsAppliedEvenWhenTheSameTierIsAlreadyHeld() {
        // Order matters: pending is asked first, so a patient re-subscribing to a tier they already hold is applied
        // rather than swallowed as "already done". Without that ordering the already-satisfied check would lose a
        // real verification.
        when(membershipService.activeFor(PATIENT_ID)).thenReturn(List.of(pending("membership-old", PLAN).status(MembershipStatus.ACTIVE)));

        consumer.apply(acknowledgement());

        verify(membershipService).activateIfPending(MEMBERSHIP_ID);
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

    /**
     * The type string, pinned as a literal on this side too.
     *
     * <p>It is a contract with a repository that cannot be compiled against this one — hc-admin's
     * {@code PlanVerificationEvent.TYPE}. A rename here is not a compile error there, so without this test the
     * constant could be renamed, every test that builds a fixture through it would follow, and the frames would
     * simply stop being recognised.</p>
     */
    @Test
    void theTypeThisTopicCarriesIsTheLiteralHcAdminPublishes() {
        assertThat(PlanVerificationConsumer.PLAN_VERIFIED).isEqualTo("PlanVerified");
    }

    @Test
    void aFrameOfAnotherTypeIsRefusedRatherThanAppliedAsAnApproval() {
        // THE HAZARD THIS CHECK EXISTS FOR, and it is a rejection rather than a typo. Their item 54 says a second
        // control follows the moment a rejection path is decided; that frame carries a matching plan and no status,
        // so before the type was pinned it went the whole way through and ACTIVATED a membership an administrator
        // had refused. assertActivating cannot catch it — it only fires when a status is present, and the settled
        // payload has none.
        PatientEvent rejection = new PatientEvent(
            UUID.randomUUID().toString(),
            "PlanRejected",
            PatientEvent.VERSION,
            Instant.now(),
            "hcAdminService",
            new PatientEvent.Subject(EMAIL, null, null),
            Map.of("plan", PLAN)
        );

        PlanVerificationRefusedException refusal = assertRefusedWith(Reason.UNEXPECTED_TYPE, rejection);
        assertThat(refusal).hasMessageContaining("PlanRejected").hasMessageContaining("PlanVerified");
        verify(membershipService, never()).activateIfPending(anyString());
    }

    @Test
    void aNullFrameIsRefusedUnderItsOwnReason() {
        // NO_FRAME rather than NO_EVENT_ID: a refusal naming the wrong cause sends the next reader to hc-admin's
        // serialiser for a fault on this side. The binder does not hand a function a null, so this is defensive —
        // but an untested defensive branch is how the wrong reason survives review.
        assertRefusedWith(Reason.NO_FRAME, null);
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
    // The payload shape, now that hc-admin has chosen one
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void thePlanArrivesUnderPlanAsAFlatString() {
        // Their PlanData record serialises to exactly {"plan":"MELON"}. This is the shape, and the object-with-a-code
        // branch that read item 18's "Plan is a commercial object" has been dropped now that they have chosen.
        consumer.apply(acknowledgement(Map.of("plan", PLAN)));
        verify(membershipService).activateIfPending(MEMBERSHIP_ID);
    }

    @Test
    void thePlanMayStillArriveUnderThePlanCodeSpellingThisRepoPublishes() {
        // Kept as an alias, demoted from an open question: planCode is this service's own outbound spelling on
        // PlanChosen, which their earlier draft echoed. One line, and a plausible refactor next door is a non-event.
        consumer.apply(acknowledgement(Map.of("planCode", PLAN)));
        verify(membershipService).activateIfPending(MEMBERSHIP_ID);
    }

    @Test
    void aNullValuedPlanKeyFallsThroughToPlanCodeRatherThanRefusing() {
        // Choosing between the spellings with containsKey refused this frame NO_PLAN_NAMED with a perfectly good code
        // unread in the same map. NULL-VALUED KEYS ARE hc-admin'S HOUSE STYLE — item 27's closing note records them
        // putting a null planCode straight on the wire — so this would have read as a contract disagreement when it
        // was a serialisation habit.
        Map<String, Object> both = new java.util.HashMap<>();
        both.put("plan", null);
        both.put("planCode", PLAN);

        consumer.apply(acknowledgement(both));

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
