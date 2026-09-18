package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.jojoaddison.domain.Membership;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.enumeration.MembershipStatus;
import net.jojoaddison.repository.MembershipRepository;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.service.event.MembershipStreamPublisher;
import net.jojoaddison.service.event.PatientEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * That a decision about a membership is announced, and that ordinary editing is not.
 *
 * <p>Until 2026-09-09 {@code PlanChosen} was published on create and nowhere else, so an administrator approving a
 * membership — {@code PENDING → ACTIVE}, the exact transition item 19's write guard exists to restrict — told hc-admin
 * nothing. Every row on their queue was a report that a choice had happened, never a status, and nothing retired one;
 * their console had to head the column <em>REPORTED</em> because of it. Backlog item 27.</p>
 *
 * <p><strong>The rule is not "publish on PUT and PATCH".</strong> It is: publish when the status this service
 * <em>persisted</em> differs from the status the stored document held. The distinction is the whole point — a patient
 * renaming their own membership must announce nothing, and item 19's inbound consumer must announce the day it lands
 * without anyone remembering to add a call site.</p>
 *
 * <p><strong>Written against {@link MembershipService} rather than through the resource, deliberately.</strong> The
 * announcement is not a property of an HTTP verb: it belongs to the write. A test that could only reach it through
 * {@code MembershipResource} would pass just as happily with the rule copied into three verbs, which is the shape this
 * repo has twice had to undo. {@link net.jojoaddison.web.rest.MembershipStatusWriteGuardIT} is what pins the HTTP half
 * — who may move a status at all — and it is a separate question from this one.</p>
 */
class MembershipStatusAnnouncementTest {

    private static final String PATIENT_ID = "patient-ama";
    private static final String PATIENT_EMAIL = "Ama@Example.Test";
    private static final String MEMBERSHIP_ID = "membership-1";

    /** The earlier pending choice a second CHOOSE supersedes. */
    private static final String SUPERSEDED_ID = "membership-0";

    private MembershipRepository memberships;
    private ProfileRepository profiles;
    private PatientEventPublisher events;
    private MembershipStreamPublisher stream;
    private MongoTemplate mongoTemplate;
    private MembershipService service;

    @BeforeEach
    void setUp() {
        memberships = mock(MembershipRepository.class);
        profiles = mock(ProfileRepository.class);
        events = mock(PatientEventPublisher.class);
        stream = mock(MembershipStreamPublisher.class);
        mongoTemplate = mock(MongoTemplate.class);
        service = new MembershipService(memberships, profiles, events, stream, mongoTemplate);

        when(memberships.save(any(Membership.class))).thenAnswer(call -> call.getArgument(0));
        when(profiles.findByPatientId(PATIENT_ID)).thenReturn(List.of(new Profile().patientId(PATIENT_ID).email(PATIENT_EMAIL)));
    }

    /**
     * Makes the supersession sweep find one earlier pending membership and cancel it.
     *
     * <p>Both halves, because the sweep is an enumerating {@code find} followed by a compare-and-set per document —
     * stub only the first and it cancels nothing, which would make every assertion about the supersession pass for
     * the wrong reason. The cancelled document is returned because the service holds it: that is what lets a test
     * see whether it is announced.</p>
     */
    private void anEarlierPendingChoiceExists() {
        Membership earlier = membership(MembershipStatus.PENDING).plan("PAWPAW");
        earlier.setId(SUPERSEDED_ID);
        when(mongoTemplate.find(any(), eq(Membership.class))).thenReturn(List.of(earlier));
        when(mongoTemplate.findAndModify(any(), any(), any(FindAndModifyOptions.class), eq(Membership.class)))
            .thenReturn(membership(MembershipStatus.CANCELLED).id(SUPERSEDED_ID));
    }

    private static Membership membership(MembershipStatus status) {
        return new Membership().id(MEMBERSHIP_ID).patientId(PATIENT_ID).plan("PAWPAW").name("PAWPAW Plan").status(status);
    }

    @Test
    void anApprovalAnnouncesThePersistedStatus() {
        service.update(membership(MembershipStatus.ACTIVE), MembershipStatus.PENDING);

        assertThat(published())
            .containsEntry("membershipId", MEMBERSHIP_ID)
            // ACTIVE, not the PENDING it held. hc-admin keys the plan group on membershipId and replaces it
            // wholesale, and their panel filters planStatus=PENDING server-side — so this value is what drops the
            // row off their queue.
            .containsEntry("status", "ACTIVE");
    }

    /**
     * <b>The patient is told at the same moment hc-admin is, and by the same rule.</b>
     *
     * <p>Backlog item 39: an administrator verified a plan, hc-admin was told, and the patient's screen went on saying
     * "Awaiting confirmation" until they restarted the app. The second publish lives inside
     * {@code announceChosenPlan} rather than beside its callers precisely so that the two audiences cannot drift —
     * this test and the one below it are what say so, because "both lines are in one method" is a claim about today's
     * code and not about tomorrow's.</p>
     */
    @Test
    void anApprovalAlsoReachesThePatientsOwnStream() {
        Membership approved = membership(MembershipStatus.ACTIVE);

        service.update(approved, MembershipStatus.PENDING);

        verify(stream).publish(approved);
    }

    @Test
    void aPatientRenamingTheirMembershipTellsTheirStreamNothingEither() {
        // The silence is shared too. A stream that fired on every write would have the patient's client re-fetching
        // every time they edited their own membership name, which is the poll item 39 declined dressed as a push.
        service.update(membership(MembershipStatus.PENDING).name("Our family plan"), MembershipStatus.PENDING);

        verifyNoInteractions(stream);
    }

    @Test
    void aPatientRenamingTheirMembershipAnnouncesNothing() {
        // The case most likely to be got wrong, and the reason the rule is written on the persisted status rather
        // than on the verb. Under item 19's write guard a non-administrator's requested status is discarded and the
        // stored one carried over, so a patient's PUT arrives here having changed nothing hc-admin cares about.
        service.update(membership(MembershipStatus.PENDING).name("Our family plan"), MembershipStatus.PENDING);

        verifyNoInteractions(events);
    }

    @Test
    void aPartialApprovalAnnouncesThePersistedStatus() {
        when(memberships.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(membership(MembershipStatus.PENDING)));

        service.partialUpdate(membership(MembershipStatus.ACTIVE), MembershipStatus.PENDING);

        assertThat(published()).containsEntry("status", "ACTIVE");
    }

    @Test
    void aPartialUpdateThatLeavesTheStatusAloneAnnouncesNothing() {
        when(memberships.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(membership(MembershipStatus.PENDING)));

        // A merge patch carrying only a description: the merge leaves status untouched, so nothing was decided.
        Membership patch = new Membership().id(MEMBERSHIP_ID).description("Renewed after the move");
        service.partialUpdate(patch, MembershipStatus.PENDING);

        verifyNoInteractions(events);
    }

    @Test
    void aPartialUpdateOfAMembershipThatIsNotThereAnnouncesNothing() {
        // There is no persisted status to compare against, so there is nothing to report. Announcing here would put
        // a membership id on the topic that hc-admin would create a plan group for and nothing would ever clear.
        when(memberships.findById(MEMBERSHIP_ID)).thenReturn(Optional.empty());

        assertThat(service.partialUpdate(membership(MembershipStatus.ACTIVE), MembershipStatus.PENDING)).isEmpty();

        verifyNoInteractions(events);
    }

    @Test
    void aStatusReachingAMembershipThatHadNoneAnnounces() {
        // Not a no-op dressed as one: a membership written without a status and later given one has been decided,
        // and null is not a status hc-admin can filter on.
        service.update(membership(MembershipStatus.PENDING), null);

        assertThat(published()).containsEntry("status", "PENDING");
    }

    @Test
    void clearingAStatusAnnouncesNothing() {
        // The one asymmetry in the rule, and it is load-bearing. Announced, a cleared status is a frame carrying no
        // status at all: hc-admin unsets plan_status and the row leaves the PENDING queue with no decision recorded
        // anywhere. Silence leaves them showing a stale PENDING: wrong, but visible and still actionable.
        //
        // The REST layer no longer offers a way to reach this — item 30 stopped PUT nulling a status on 2026-09-09 —
        // and the guard is still right, which is why this test calls the service rather than an endpoint. A stored
        // status that is already null carries null over into exactly this case, and any caller in the service package
        // (item 19's inbound consumer next) reaches this method without passing the resource's guards at all.
        service.update(membership(null), MembershipStatus.ACTIVE);

        verifyNoInteractions(events);
    }

    @Test
    void aCallerOutsideTheWebLayerInheritsTheAnnouncementWithNoFurtherCallSite() {
        // This is item 19's inbound patient-events-plan consumer, written out: read the patient's PENDING membership,
        // set ACTIVE, save. It will live in this package, it calls this method, and it announces without adding a
        // fourth call site or a second copy of the rule — which is the whole reason item 27 goes before it.
        Membership stored = membership(MembershipStatus.PENDING);
        MembershipStatus held = stored.getStatus();
        stored.setStatus(MembershipStatus.ACTIVE);

        service.update(stored, held);

        verify(events).publish(eq("PlanChosen"), eq(PATIENT_EMAIL), any(), eq(PATIENT_ID), any());
        assertThat(published()).containsEntry("status", "ACTIVE");
    }

    /**
     * That the shape item 19's consumer actually uses announces too.
     *
     * <p>The test above is how the consumer was <em>imagined</em> when item 27 landed, and it is not how it was
     * built: read-then-save is a lost-update window with a sibling product holding the pen, so the consumer calls
     * {@link MembershipService#activateIfPending} and the held status is the update's criterion rather than an
     * earlier read. The announcement had to come with it — a conditional update that did not announce would have
     * dequeued nothing on hc-admin's panel, which is the entire point of the transition.</p>
     */
    @Test
    void theInboundConsumersConditionalUpdateAnnouncesToo() {
        when(mongoTemplate.findAndModify(any(), any(), any(FindAndModifyOptions.class), eq(Membership.class)))
            .thenReturn(membership(MembershipStatus.ACTIVE));

        assertThat(service.activateIfPending(MEMBERSHIP_ID)).isPresent();

        verify(events).publish(eq("PlanChosen"), eq(PATIENT_EMAIL), any(), eq(PATIENT_ID), any());
        assertThat(published()).containsEntry("status", "ACTIVE");
    }

    @Test
    void aConditionalUpdateThatMatchedNothingAnnouncesNothing() {
        // Somebody else moved the membership out of PENDING first. Their decision is a real decision and this one is
        // stale, so there is nothing to report — and, crucially, nothing was written either. An announcement here
        // would tell hc-admin the membership went ACTIVE when it did not.
        when(mongoTemplate.findAndModify(any(), any(), any(FindAndModifyOptions.class), eq(Membership.class))).thenReturn(null);

        assertThat(service.activateIfPending(MEMBERSHIP_ID)).isEmpty();

        verifyNoInteractions(events);
    }

    /**
     * That superseding the patient's earlier pending choice puts <b>one</b> frame on the topic, and it is the new
     * membership's.
     *
     * <p>This is item 40's most consequential design question and this test is the whole of the answer. hc-admin holds
     * one plan group per patient, keyed on {@code membershipId} and replaced wholesale, and both frames would carry
     * the same subject key and so land on the same partition in order. A second frame announcing the superseded
     * membership as {@code CANCELLED} would therefore arrive <em>after</em> the creation frame and leave their
     * directory row naming a cancelled membership — <b>the patient's new choice would never reach the
     * {@code planStatus=PENDING} queue their console is built on</b>. Nothing would retire it, because nothing had
     * raised it.</p>
     *
     * <p><strong>Asserted here rather than over a real broker, deliberately.</strong> {@code MembershipPlanEventIT}
     * says in as many words why: proving an absence on Kafka means waiting out a timeout and calling silence a pass,
     * which is a slow test that cannot fail honestly. A mocked publisher can count frames.</p>
     *
     * <p><strong>{@code verify(events)} with no count is the assertion.</strong> Mockito's default is exactly one
     * invocation, so a supersession that announced would fail here with "wanted 1 time but was 2" — and
     * {@code published()} would fail on the same call. Both directions were measured; see backlog item 40.</p>
     */
    @Test
    void supersedingAnEarlierPendingChoiceAnnouncesOnlyTheNewOne() {
        anEarlierPendingChoiceExists();

        service.save(membership(MembershipStatus.PENDING).plan("MELON").name("MELON Plan"));

        // That a membership really was superseded, so "one frame" is an absence caused by the rule rather than by the
        // sweep quietly not running. The query itself is pinned against a database in MembershipStatusWriteGuardIT —
        // a mocked MongoTemplate can see that this service asked, never what it asked for.
        verify(mongoTemplate).findAndModify(any(), any(), any(FindAndModifyOptions.class), eq(Membership.class));
        // Exactly one frame, and it names the membership just written rather than the one just cancelled.
        verify(events).publish(eq("PlanChosen"), eq(PATIENT_EMAIL), any(), eq(PATIENT_ID), any());
        assertThat(published())
            .containsEntry("membershipId", MEMBERSHIP_ID)
            .containsEntry("planCode", "MELON")
            .containsEntry("status", "PENDING");
    }

    @Test
    void aMembershipThatIsNotPendingSupersedesNothing() {
        // The invariant is about pending choices only, so an administrator creating an ACTIVE membership directly
        // must not reach for the sweep at all. Whether an approval moots a request the patient made is a back-office
        // judgement and not this service's to take.
        service.save(membership(MembershipStatus.ACTIVE));

        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void aMembershipWithNoOwnerSupersedesNothing() {
        // PatientScope.requirePatientIdForWrite lets an unrestricted caller create a record with no patientId, so
        // without this guard a sweep keyed on a null owner would match EVERY ownerless pending membership in the
        // collection and cancel the lot as though they belonged to one person.
        service.save(new Membership().id(MEMBERSHIP_ID).plan("PAWPAW").status(MembershipStatus.PENDING));

        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void aMembershipWhoseOwnerIsBlankSupersedesNothingEither() {
        // Blank counts as absent, the reading PatientEventPublisher already applies to a subject key. A whitespace
        // patientId would otherwise group with nothing and match nothing, which is a query nobody meant to run.
        service.save(new Membership().id(MEMBERSHIP_ID).patientId("   ").plan("PAWPAW").status(MembershipStatus.PENDING));

        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void anAdministratorSendingAMembershipBackToPendingSupersedesToo() {
        // The PUT/PATCH half. A patient cannot reach it — their requested status is discarded and the stored one
        // carried over — but an administrator can move an ACTIVE membership back to PENDING, which is the second way
        // into two pending choices and the one a rule written only on POST would miss. That drift is the reason the
        // rule is written on the persisted status of every write, exactly as the announcement rule is.
        anEarlierPendingChoiceExists();

        service.update(membership(MembershipStatus.PENDING), MembershipStatus.ACTIVE);

        verify(mongoTemplate).findAndModify(any(), any(), any(FindAndModifyOptions.class), eq(Membership.class));
    }

    /** The payload of the one event this service published, failing the test if it published none. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> published() {
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(events).publish(eq("PlanChosen"), any(), any(), any(), data.capture());
        return data.getValue();
    }
}
