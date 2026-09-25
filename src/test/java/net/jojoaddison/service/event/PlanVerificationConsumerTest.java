package net.jojoaddison.service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.HashMap;
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
 * The rules a frame on {@code admin.event} has to pass, one at a time — and the far larger number that are none of this
 * service's business and must cost nothing.
 *
 * <h2>Each guarded thing is mutated separately, and asserted on its reason</h2>
 *
 * <p>"It refused" is one assertion and the refusals are not interchangeable. Two pending memberships and a plan that
 * disagrees are the pair this consumer could most easily confuse — both end in a throw, both mention a plan — so a
 * test matching on message text would pass with either wired to the other. Asserting {@link Reason} is what keeps
 * one test per reason honest; there are eleven reasons and each is mutated separately.</p>
 *
 * <p><b>Since backlog item 47 the fixtures are {@link AdminEvent}s in hc-admin's own envelope</b>, which is not the
 * envelope item 19 was written against: the addressee is {@code data.subjectKey}, and {@code subject} names a
 * {@code DirectoryLink} in their database that means nothing here. Written from their {@code PlanVerifiedEvent} at
 * {@code 7deda9a} and from frames read off the live quality broker, not from either backlog entry — and as a literal
 * map rather than by serialising anything of theirs, since nothing here can compile against their repository.</p>
 *
 * <p>Written against the handler with the repositories mocked, deliberately. What is under test here is the rule; that
 * the rule is reachable from a real channel at all is {@code PlanVerificationConsumerBindingIT}'s question, and that a
 * refusal does not take the binding with it is {@code PlanVerificationRoundTripIT}'s. Backlog items 19 and 47.</p>
 */
class PlanVerificationConsumerTest {

    private static final String EMAIL = "Ama.Plan@Example.Test";
    private static final String LOWERCASED = "ama.plan@example.test";
    private static final String PATIENT_ID = "patient-ama-plan";
    private static final String MEMBERSHIP_ID = "membership-1";
    private static final String PLAN = "PAWPAW";

    /** hc-admin's id for the record an administrator pressed the button on. It is a DirectoryLink, and it is theirs. */
    private static final String LINK_ID = "dl-p12";

    private MembershipService membershipService;
    private ProfileRepository profiles;
    private PlanVerificationRepository verifications;
    private SimpleMeterRegistry meters;
    private PlanVerificationConsumer consumer;

    @BeforeEach
    void setUp() {
        membershipService = mock(MembershipService.class);
        profiles = mock(ProfileRepository.class);
        verifications = mock(PlanVerificationRepository.class);
        meters = new SimpleMeterRegistry();
        consumer = new PlanVerificationConsumer(membershipService, profiles, verifications, meters);

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
     * A verification in the shape hc-admin actually sends on {@code admin.event} — read from their
     * {@code PlanVerifiedEvent} at {@code 7deda9a}, and matching a frame read off the quality broker on 2026-09-25:
     * {@code subject} the DirectoryLink they acted on, {@code data} carrying the plan and the addressee.
     *
     * <p>The caller supplies {@code data} and {@code subjectKey} is added to it unless the caller named one, so that
     * every test about the plan, the status or the membership does not have to restate the addressee — and so that the
     * one test about a missing addressee has to say so out loud.</p>
     */
    private static AdminEvent verification(Map<String, Object> data) {
        Map<String, Object> payload = new HashMap<>(data);
        payload.putIfAbsent("subjectKey", EMAIL);
        return frame(UUID.randomUUID().toString(), PlanVerificationConsumer.PLAN_VERIFIED, payload);
    }

    private static AdminEvent verification() {
        return verification(Map.of("plan", PLAN));
    }

    /** Any frame on the channel, with nothing added to it — the form the ignore path and the refusals need. */
    private static AdminEvent frame(String eventId, String type, Map<String, Object> data) {
        return new AdminEvent(eventId, type, 1, Instant.now(), "hcAdminService", new AdminEvent.Subject("DirectoryLink", LINK_ID), data);
    }

    // ---------------------------------------------------------------------------------------------------------
    // The one that must work
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void aValidAcknowledgementActivatesTheSinglePendingMembership() {
        consumer.apply(verification());

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

        consumer.apply(verification());

        verify(membershipService).activateIfPending(MEMBERSHIP_ID);
    }

    @Test
    void applyingItRecordsTheVerification() {
        consumer.apply(verification());

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
        consumer.apply(verification());

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
        AdminEvent event = verification();
        when(verifications.existsById(event.eventId())).thenReturn(true);

        consumer.apply(event);

        // Neither again, which is the whole requirement: delivery is at least once, so this is the normal case rather
        // than the exceptional one.
        verify(membershipService, never()).activateIfPending(anyString());
        verify(verifications, never()).insert(any(PlanVerification.class));
    }

    @Test
    void aReplayIsIgnoredRatherThanRefused() {
        AdminEvent event = verification();
        when(verifications.existsById(event.eventId())).thenReturn(true);

        // Not a throw: dead-lettering a redelivery would fill the DLQ with successes, and an operator reading it
        // would be reading a queue of things that worked.
        consumer.apply(event);
    }

    // ---------------------------------------------------------------------------------------------------------
    // The refusals, one apiece
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void aVerificationWithNoEventIdIsRefused() {
        // A frame this service IS addressed by, and still malformed. The ignore path added by item 47 must not have
        // made the consumer permissive about the frames it is meant to act on — this is the same refusal it always
        // was, reached through the type check rather than before it.
        AdminEvent event = frame(null, PlanVerificationConsumer.PLAN_VERIFIED, Map.of("plan", PLAN, "subjectKey", EMAIL));

        assertRefusedWith(Reason.NO_EVENT_ID, event);
    }

    @Test
    void aVerificationWithNoSubjectKeyInItsPayloadIsRefused() {
        // ⚠ THE FIELD THAT MOVED. On patient-events-plan the addressee was subject.email; on admin.event it is
        // data.subjectKey, because `subject` there names the DirectoryLink hc-admin acted on. A frame carrying only
        // the subject — which is what EVERY frame on this channel carries — names nobody in this database.
        AdminEvent event = frame(UUID.randomUUID().toString(), PlanVerificationConsumer.PLAN_VERIFIED, Map.of("plan", PLAN));

        PlanVerificationRefusedException refusal = assertRefusedWith(Reason.NO_SUBJECT_KEY, event);

        // The message has to send the next reader to the right field. "No subject" would point them at hc-admin's
        // serialiser for a frame whose subject is perfectly well formed and simply is not a person.
        assertThat(refusal).hasMessageContaining("data.subjectKey").hasMessageContaining("DirectoryLink/" + LINK_ID);
    }

    @Test
    void aBlankSubjectKeyNamesNobodyEither() {
        AdminEvent event = frame(
            UUID.randomUUID().toString(),
            PlanVerificationConsumer.PLAN_VERIFIED,
            Map.of("plan", PLAN, "subjectKey", "  ")
        );

        assertRefusedWith(Reason.NO_SUBJECT_KEY, event);
    }

    @Test
    void anAcknowledgementNamingNoPlanIsRefused() {
        // The payload is one field. A frame without it is not a smaller version of the contract, it is a frame whose
        // consistency check cannot be made at all.
        assertRefusedWith(Reason.NO_PLAN_NAMED, verification(Map.of("membershipId", MEMBERSHIP_ID)));
    }

    @Test
    void anUnknownEmailIsRefused() {
        when(profiles.findOneByEmailIgnoreCase(LOWERCASED)).thenReturn(Optional.empty());

        assertRefusedWith(Reason.UNKNOWN_PATIENT, verification());
        verify(membershipService, never()).activateIfPending(anyString());
    }

    @Test
    void aPatientWithNoPendingMembershipIsRefused() {
        when(membershipService.pendingFor(PATIENT_ID)).thenReturn(List.of());

        assertRefusedWith(Reason.NO_PENDING_MEMBERSHIP, verification());
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

        consumer.apply(verification());

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

        assertRefusedWith(Reason.NO_PENDING_MEMBERSHIP, verification());
    }

    @Test
    void aPendingChoiceIsAppliedEvenWhenTheSameTierIsAlreadyHeld() {
        // Order matters: pending is asked first, so a patient re-subscribing to a tier they already hold is applied
        // rather than swallowed as "already done". Without that ordering the already-satisfied check would lose a
        // real verification.
        when(membershipService.activeFor(PATIENT_ID)).thenReturn(List.of(pending("membership-old", PLAN).status(MembershipStatus.ACTIVE)));

        consumer.apply(verification());

        verify(membershipService).activateIfPending(MEMBERSHIP_ID);
    }

    @Test
    void aPatientWithTwoPendingMembershipsIsRefusedRatherThanGuessedAt() {
        when(membershipService.pendingFor(PATIENT_ID)).thenReturn(List.of(pending(MEMBERSHIP_ID, PLAN), pending("membership-2", PLAN)));

        // Item 19's decision, and the reason for it: silently picking one of two pending memberships activates a year
        // of care nobody sold. Note both are on the same plan, so the consistency check cannot be what saves this —
        // the uniqueness rule has to.
        assertRefusedWith(Reason.MORE_THAN_ONE_PENDING_MEMBERSHIP, verification());
        verify(membershipService, never()).activateIfPending(anyString());
    }

    @Test
    void anAcknowledgementNamingADifferentPlanIsRefused() {
        when(membershipService.pendingFor(PATIENT_ID)).thenReturn(List.of(pending(MEMBERSHIP_ID, "MELON")));

        // The whole reason `plan` is in the payload: it carries no new information, and its use is turning a silent
        // mismatch into a stop. Delete the check and this test is what fails.
        assertRefusedWith(Reason.PLAN_DISAGREES, verification());
        verify(membershipService, never()).activateIfPending(anyString());
    }

    // ---------------------------------------------------------------------------------------------------------
    // The channel is shared, and almost none of it is ours — backlog item 47
    // ---------------------------------------------------------------------------------------------------------

    /**
     * The type string, pinned as a literal on this side too.
     *
     * <p>It is a contract with a repository that cannot be compiled against this one — hc-admin's
     * {@code PlanVerifiedEvent.TYPE}. A rename here is not a compile error there, so without this test the constant
     * could be renamed, every test that builds a fixture through it would follow, and the frames would simply stop
     * being recognised. Note that it pins the value against their <em>channel's</em> class; they hold the same string
     * a second time for the retired topic and say in as many words not to merge the two.</p>
     */
    @Test
    void theTypeThisServiceIsAddressedByIsTheLiteralHcAdminPublishes() {
        assertThat(PlanVerificationConsumer.PLAN_VERIFIED).isEqualTo("PlanVerified");
    }

    /**
     * ⭐ <b>The headline behaviour of item 47: an entity change is ignored, not refused.</b>
     *
     * <p>{@code admin.event} carries hc-admin's entire entity CRUD — 7433 of the 7435 frames on it on 2026-09-25 —
     * and under item 19's rule every one of them would be refused, retried four times and dead-lettered. That would
     * cost more than it sounds: the dead-letter queue this service reads after a real refusal would be buried under
     * another product's wage rates.</p>
     */
    @Test
    void anEntityChangeFromHcAdminIsIgnoredRatherThanRefused() {
        AdminEvent entityChanged = frame(UUID.randomUUID().toString(), "EntityChanged", Map.of("action", "SAVED"));

        consumer.apply(entityChanged);

        verify(membershipService, never()).activateIfPending(anyString());
        verify(verifications, never()).insert(any(PlanVerification.class));
    }

    /**
     * ⭐ <b>That the type is checked BEFORE the ledger, which is an ordering and not a behaviour.</b>
     *
     * <p>Both orderings ignore the frame, so no assertion about the outcome can tell them apart — and the cost is the
     * whole point: item 19 read the ledger first, so on this channel every entity change hc-admin writes would become
     * a primary-key read in this service's database. An {@code EntityChanged} frame has a perfectly good
     * {@code eventId}, so nothing earlier would have stopped it getting that far.</p>
     */
    @Test
    void aFrameThatIsNotOursCostsNoDatabaseReadAtAll() {
        consumer.apply(frame(UUID.randomUUID().toString(), "EntityChanged", Map.of("action", "SAVED")));

        verify(verifications, never()).existsById(anyString());
        verify(profiles, never()).findOneByEmailIgnoreCase(anyString());
    }

    /**
     * That a frame with no {@code eventId} and no business here is ignored rather than refused.
     *
     * <p>The reverse ordering would refuse it {@link Reason#NO_EVENT_ID} — a dead letter, in this service's queue,
     * about a producer defect in a frame addressed to somebody else entirely.</p>
     */
    @Test
    void aMalformedFrameOfAnotherTypeIsStillNoneOfThisServicesBusiness() {
        consumer.apply(frame(null, "ProfessionalVerified", Map.of("status", "VERIFIED", "professionalId", "p1")));

        verify(membershipService, never()).activateIfPending(anyString());
    }

    /**
     * ⚠ <b>What the inversion gives up, asserted rather than left in a comment.</b>
     *
     * <p>hc-admin's item 54 promises a rejection frame the moment that path is decided. Item 19 dead-lettered it so a
     * person would find it; this now ignores it. <b>The state that leaves behind is the safe one</b> — the membership
     * stays {@code PENDING}, nothing is granted, and the frame stays on their channel where it can be replayed once
     * somebody models it. What must never happen is the third outcome: applying it. Before the type was dispatched on
     * at all, a rejection carrying a matching plan and no status went the whole way through and ACTIVATED a membership
     * an administrator had refused — {@code assertActivating} cannot catch that, because it only fires when a status is
     * present and the settled payload has none.</p>
     */
    @Test
    void aRejectionIsIgnoredAndAboveAllIsNotAppliedAsAnApproval() {
        AdminEvent rejection = frame(UUID.randomUUID().toString(), "PlanRejected", Map.of("plan", PLAN, "subjectKey", EMAIL));

        consumer.apply(rejection);

        verify(membershipService, never()).activateIfPending(anyString());
        verify(verifications, never()).insert(any(PlanVerification.class));
    }

    /**
     * ⭐ <b>That ignoring is counted, because the alternative reading of silence is "bound to nothing".</b>
     *
     * <p>Item 32 shipped this consumer deployed, healthy, serving and subscribed to a topic that did not exist, and
     * nothing could see it because a quiet consumer and an absent one produce the same output. On a channel this busy
     * the count of declined frames is the direct answer to "is it reading at all", and it is what lets every
     * assertion-by-absence in {@code PlanVerificationRoundTripIT} mean something.</p>
     */
    @Test
    void anIgnoredFrameIsCountedSoThatSilenceCanBeToldFromABindingThatNeverBound() {
        consumer.apply(frame(UUID.randomUUID().toString(), "EntityChanged", Map.of("action", "SAVED")));
        consumer.apply(frame(UUID.randomUUID().toString(), "ProfessionalVerified", Map.of("status", "VERIFIED")));

        assertThat(ignored()).isEqualTo(2);
    }

    @Test
    void anAppliedVerificationIsNotCountedAsIgnored() {
        // The other half, and the one that stops the counter meaning "frames seen". A meter that ticked for everything
        // would answer "is it reading" and stop answering "is any of this ours", which is the question it is for.
        consumer.apply(verification());

        assertThat(ignored()).isZero();
    }

    /**
     * ⭐ <b>The alarm the inversion moved, made assertable: a drift in the type literal is invisible in the ignored
     * count and obvious in the applied one.</b>
     *
     * <p>{@link PlanVerificationConsumer#PLAN_VERIFIED} is pinned against a string hc-admin owns, and a rename of it
     * there is not a compile error here. Under item 19's rule that drift dead-lettered every verification and somebody
     * found it. Under item 47's it takes the ignore path instead — membership stays {@code PENDING}, nothing thrown,
     * nothing dead-lettered, nothing logged above {@code TRACE}, <b>and the ignored counter goes on climbing exactly as
     * it does when all is well</b>, because on this channel it is dominated by hc-admin's entity churn either way.</p>
     *
     * <p>So this test plays the drift out: the same verification, with the type spelled as a plausible future version
     * of theirs. Everything looks healthy and <em>nothing</em> is applied — which is the shape only a second meter can
     * show, and the reason there is one.</p>
     */
    @Test
    void aDriftInTheTypeLiteralShowsAsAppliedGoingToZeroWhileIgnoredKeepsClimbing() {
        consumer.apply(frame(UUID.randomUUID().toString(), "EntityChanged", Map.of("action", "SAVED")));
        consumer.apply(frame(UUID.randomUUID().toString(), "PlanVerifiedV2", Map.of("plan", PLAN, "subjectKey", EMAIL)));

        // Healthy-looking: frames are arriving and being read, and the busiest meter on the channel moved.
        assertThat(ignored()).as("the channel went quiet, which is not the condition under test").isEqualTo(2);

        // And not one decision took effect. Nothing else in this class, and nothing in the dead-letter queue, can say so.
        assertThat(applied()).as("a frame whose type this service does not recognise was applied anyway").isZero();
        assertThat(refused()).as("a frame of an unrecognised type was refused — that is item 19's rule, not item 47's").isZero();
        verify(membershipService, never()).activateIfPending(anyString());
    }

    @Test
    void anAppliedVerificationIsCounted() {
        consumer.apply(verification());

        assertThat(applied()).isEqualTo(1);
        assertThat(refused()).isZero();
        assertThat(satisfied()).isZero();
    }

    @Test
    void aRefusalIsCountedAsWellAsDeadLettered() {
        when(profiles.findOneByEmailIgnoreCase(LOWERCASED)).thenReturn(Optional.empty());

        assertRefusedWith(Reason.UNKNOWN_PATIENT, verification());

        // The counterpart to reading the dead-letter queue: every refusal is also a dead letter, so this makes "did we
        // refuse anything this week" answerable without reading it — DroppedEventCounter's argument, on the inbound side.
        assertThat(refused()).isEqualTo(1);
        assertThat(applied()).isZero();
    }

    @Test
    void aReplayIsCountedAsSatisfiedRatherThanAppliedOrIgnored() {
        AdminEvent event = verification();
        when(verifications.existsById(event.eventId())).thenReturn(true);

        consumer.apply(event);

        // Not `ignored`: this frame WAS addressed to this service, and folding it in there would inflate the meter that
        // answers "how much of this channel is none of our business" with our own redeliveries.
        assertThat(satisfied()).isEqualTo(1);
        assertThat(ignored()).isZero();
        assertThat(applied()).isZero();
    }

    @Test
    void aSecondPressForAPlanAlreadyActiveIsCountedAsSatisfied() {
        when(membershipService.pendingFor(PATIENT_ID)).thenReturn(List.of());
        when(membershipService.activeFor(PATIENT_ID)).thenReturn(List.of(pending(MEMBERSHIP_ID, PLAN).status(MembershipStatus.ACTIVE)));

        consumer.apply(verification());

        // hc-admin's ordinary recovery path, and the one outcome with no output of any kind — see
        // PlanVerificationRoundTripIT, whose test of it asserted nothing but an absence until this meter existed.
        assertThat(satisfied()).isEqualTo(1);
        assertThat(applied()).isZero();
        assertThat(refused()).isZero();
    }

    /**
     * ⭐ <b>That the four meters account for every frame exactly once.</b>
     *
     * <p>Without this each is only a lower bound, and the question they exist to answer — <em>is the channel being
     * read, and is any of it taking effect</em> — is asked as a ratio between them. A path that incremented two, or
     * none, would make that ratio quietly wrong rather than obviously wrong. A fifth outcome added later fails here
     * first, which is the point.</p>
     */
    @Test
    void everyFrameLandsInExactlyOneOfTheFourCounters() {
        consumer.apply(frame(UUID.randomUUID().toString(), "EntityChanged", Map.of("action", "SAVED")));
        consumer.apply(verification());

        AdminEvent replay = verification();
        when(verifications.existsById(replay.eventId())).thenReturn(true);
        consumer.apply(replay);

        when(profiles.findOneByEmailIgnoreCase(LOWERCASED)).thenReturn(Optional.empty());
        assertRefusedWith(Reason.UNKNOWN_PATIENT, verification());

        assertThat(ignored() + applied() + satisfied() + refused()).as("four frames handled, four increments").isEqualTo(4);
        assertThat(List.of(ignored(), applied(), satisfied(), refused())).containsExactly(1.0, 1.0, 1.0, 1.0);
    }

    private double ignored() {
        return count(PlanVerificationConsumer.IGNORED_METER_NAME);
    }

    private double applied() {
        return count(PlanVerificationConsumer.APPLIED_METER_NAME);
    }

    private double refused() {
        return count(PlanVerificationConsumer.REFUSED_METER_NAME);
    }

    private double satisfied() {
        return count(PlanVerificationConsumer.SATISFIED_METER_NAME);
    }

    /**
     * Reads one of the four by name.
     *
     * <p>{@code get} rather than {@code find}, so a meter the consumer stopped registering fails the test rather than
     * reading as zero — a counter that is absent and a counter that has not moved are the same number and not the same
     * fact, which is the whole argument for having these at all.</p>
     */
    private double count(String meterName) {
        return meters.get(meterName).tag("topic", PlanVerificationConsumer.CHANNEL).counter().count();
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
        AdminEvent event = verification(Map.of("plan", PLAN, "status", "VERIFIED"));

        PlanVerificationRefusedException refusal = assertRefusedWith(Reason.STATUS_NOT_IN_THIS_SERVICES_VOCABULARY, event);
        assertThat(refusal).hasMessageContaining("VERIFIED").hasMessageContaining("ACTIVE");

        // And the binding survives it: the very next frame is applied. A refusal is an exception the binder retries
        // and dead-letters, never a subscription that stops.
        consumer.apply(verification());
        verify(membershipService).activateIfPending(MEMBERSHIP_ID);
    }

    @Test
    void aStatusThatIsRecognisedButIsNotAnActivationIsRefused() {
        // Distinct from the test above on purpose: CANCELLED maps perfectly well and is still not what this exchange
        // carries. Applying an arbitrary status because a sibling asked would be a larger grant than the contract and
        // would route around the HTTP write guard entirely.
        assertRefusedWith(Reason.STATUS_NOT_AN_ACTIVATION, verification(Map.of("plan", PLAN, "status", "CANCELLED")));
        verify(membershipService, never()).activateIfPending(anyString());
    }

    @Test
    void aMembershipThatStoppedBeingPendingUnderneathIsRefusedRatherThanOverwritten() {
        // The conditional update matched nothing, so somebody else moved it — an administrator cancelling it, most
        // likely. Their decision is real and this one is stale. Overwriting was item 30's defect with a sibling
        // product holding the pen.
        when(membershipService.activateIfPending(MEMBERSHIP_ID)).thenReturn(Optional.empty());

        assertRefusedWith(Reason.RACED_BY_ANOTHER_WRITER, verification());
        verify(verifications, never()).insert(any(PlanVerification.class));
    }

    @Test
    void aRefusedAcknowledgementIsNotRecordedAsApplied() {
        when(membershipService.pendingFor(PATIENT_ID)).thenReturn(List.of());

        assertRefusedWith(Reason.NO_PENDING_MEMBERSHIP, verification());

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
        consumer.apply(verification(Map.of("plan", PLAN)));
        verify(membershipService).activateIfPending(MEMBERSHIP_ID);
    }

    @Test
    void thePlanMayStillArriveUnderThePlanCodeSpellingThisRepoPublishes() {
        // Kept as an alias, demoted from an open question: planCode is this service's own outbound spelling on
        // PlanChosen, which their earlier draft echoed. One line, and a plausible refactor next door is a non-event.
        consumer.apply(verification(Map.of("planCode", PLAN)));
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

        consumer.apply(verification(both));

        verify(membershipService).activateIfPending(MEMBERSHIP_ID);
    }

    @Test
    void aPlanThatDiffersOnlyInCasingIsNotAMismatch() {
        // A difference of spelling rather than of meaning. Refusing on it would be a refusal that taught nobody
        // anything, and the refusal exists to catch a plan that is genuinely not the one held.
        consumer.apply(verification(Map.of("plan", "pawpaw")));
        verify(membershipService).activateIfPending(MEMBERSHIP_ID);
    }

    /**
     * That this frame was refused, and refused for the stated reason.
     *
     * <p>Written as a try/catch rather than through {@code assertThatThrownBy} because the reason has to be read off
     * the exception itself: "it threw" is the assertion these tests must <em>not</em> settle for, since every one of
     * them throws and the point is which.</p>
     */
    private PlanVerificationRefusedException assertRefusedWith(Reason expected, AdminEvent event) {
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
