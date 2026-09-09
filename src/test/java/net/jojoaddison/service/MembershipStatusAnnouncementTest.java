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
import net.jojoaddison.service.event.PatientEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

    private MembershipRepository memberships;
    private ProfileRepository profiles;
    private PatientEventPublisher events;
    private MembershipService service;

    @BeforeEach
    void setUp() {
        memberships = mock(MembershipRepository.class);
        profiles = mock(ProfileRepository.class);
        events = mock(PatientEventPublisher.class);
        service = new MembershipService(memberships, profiles, events);

        when(memberships.save(any(Membership.class))).thenAnswer(call -> call.getArgument(0));
        when(profiles.findByPatientId(PATIENT_ID)).thenReturn(List.of(new Profile().patientId(PATIENT_ID).email(PATIENT_EMAIL)));
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
        // The one asymmetry in the rule, and it is load-bearing. PUT replaces the document wholesale and
        // statusForUpdate hands an administrator back exactly what the body carried — which is null when the generated
        // update form's status select sits on its blank <option [ngValue]="null">. Announced, that frame carries no
        // status, hc-admin unsets plan_status, and the row leaves the PENDING queue with no decision recorded
        // anywhere. Silence leaves them showing a stale PENDING: wrong, but visible and still actionable.
        //
        // That PUT can null a status at all is a separate defect in this service's own record. It is not fixed here.
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

    /** The payload of the one event this service published, failing the test if it published none. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> published() {
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(events).publish(eq("PlanChosen"), any(), any(), any(), data.capture());
        return data.getValue();
    }
}
