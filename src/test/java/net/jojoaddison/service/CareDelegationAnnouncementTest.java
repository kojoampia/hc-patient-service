package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.jojoaddison.domain.CareDelegation;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.enumeration.DelegationStatus;
import net.jojoaddison.repository.CareDelegationRepository;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.service.event.EntityEventPublisher;
import net.jojoaddison.service.event.PatientEventPublisher;
import net.jojoaddison.service.event.PatientEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * That a delegation change is announced on both channels — and that the old one did not move.
 *
 * <p>Backlog item 46. {@code patient-events} cannot be retired while this subsystem's own gateway reads it for mail, so
 * the migration adds {@code patient.event} first and removes nothing. Both halves are asserted here: the frame the
 * gateway reads <em>today</em> is unchanged, and the frame it will read after the rebind carries the same values.</p>
 *
 * <p>There was no unit test over this service's announcements before item 46 — the delegation transitions are covered by
 * {@code CareDelegationResourceIT}, which runs the real publisher and cannot see what a frame carries. That gap is why
 * the {@code patient-events} payload is pinned here by capture rather than by inspection.</p>
 */
class CareDelegationAnnouncementTest {

    private CareDelegationRepository delegations;
    private ProfileRepository profiles;
    private PatientEventPublisher events;
    private EntityEventPublisher entityEvents;
    private CareDelegationService service;

    @BeforeEach
    void setUp() {
        delegations = mock(CareDelegationRepository.class);
        profiles = mock(ProfileRepository.class);
        events = mock(PatientEventPublisher.class);
        entityEvents = mock(EntityEventPublisher.class);
        service = new CareDelegationService(delegations, profiles, events, entityEvents);

        when(delegations.save(any(CareDelegation.class))).thenAnswer(call -> call.getArgument(0));
        when(profiles.findByPatientId("patient-1"))
            .thenReturn(List.of(new Profile().patientId("patient-1").email("Kojo@Example.Test").accountId("account-kojo")));
    }

    private CareDelegation active() {
        CareDelegation delegation = new CareDelegation()
            .patientId("patient-1")
            .angelEmail("angel@example.test")
            .angelName("Ama")
            .status(DelegationStatus.ACTIVE)
            .grantedAt(Instant.now());
        delegation.setId("delegation-1");
        return delegation;
    }

    private CareDelegation awaitingCountersignature() {
        CareDelegation delegation = new CareDelegation()
            .patientId("patient-1")
            .angelEmail("angel@example.test")
            .status(DelegationStatus.AWAITING_COUNTERSIGNATURE)
            .advanceConsent(true)
            .activationRequestedById("professional-1")
            .grantedAt(Instant.now());
        delegation.setId("delegation-1");
        return delegation;
    }

    /**
     * ⛔ The add-before-removing assertion, and the one that protects live mail.
     *
     * <p>hc-admin's consumer and the gateway's {@code CareDelegationMailer} are both on {@code patient-events} today, so
     * a change to this frame is a change to production behaviour — and one that every assertion about the <em>new</em>
     * frame would be blind to. Type, key, login, account id and every payload key, pinned.</p>
     */
    @Test
    @SuppressWarnings("unchecked")
    void patientEventsStillCarriesExactlyWhatItCarriedBefore() {
        CareDelegation delegation = active();
        when(delegations.findById("delegation-1")).thenReturn(Optional.of(delegation));

        service.revoke("delegation-1", "angel@example.test");

        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(events, times(1))
            .publish(eq(PatientEventType.CARE_DELEGATION_CHANGED), eq("Kojo@Example.Test"), eq(null), eq("account-kojo"), data.capture());
        assertThat(data.getValue().keySet()).containsExactlyInAnyOrder("delegationId", "change", "angelEmail");
        assertThat(data.getValue())
            .containsEntry("change", "REVOKED_BY_ANGEL")
            .containsEntry("delegationId", "delegation-1")
            .containsEntry("angelEmail", "angel@example.test");
    }

    @Test
    void aRevocationAlsoAnnouncesOnPatientEvent() {
        when(delegations.findById("delegation-1")).thenReturn(Optional.of(active()));

        service.revoke("delegation-1", "angel@example.test");

        verify(entityEvents).publishCareDelegationChanged("delegation-1", "REVOKED_BY_ANGEL", "Kojo@Example.Test", "angel@example.test");
    }

    @Test
    void aRipenedStandbyAlsoAnnouncesOnPatientEvent() {
        // The second of the two call sites, and it goes through the overload that carries an `extra` payload — a change
        // wired into only one of them would leave this transition on the old channel alone.
        when(delegations.findById("delegation-1")).thenReturn(Optional.of(awaitingCountersignature()));

        service.countersign("delegation-1", "professional-2");

        verify(entityEvents).publishCareDelegationChanged("delegation-1", "STANDBY_ACTIVATED", "Kojo@Example.Test", "angel@example.test");
    }

    /**
     * The transitions that were never announced stay unannounced — on both channels.
     *
     * <p>{@code accept} and {@code decline} publish nothing today: an angel accepting tells the patient something they
     * asked for, and a decline is told to nobody. Adding a channel must not quietly add frames, because the new channel
     * is the one hc-admin will audit from.</p>
     */
    @Test
    void acceptingAndDecliningAnnounceOnNeitherChannel() {
        // A fresh PENDING document per call: accept() moves the one it is given to ACTIVE, and a shared instance would
        // make decline() refuse for a reason that has nothing to do with what this test is about.
        when(delegations.findById("delegation-1")).thenReturn(Optional.of(active().status(DelegationStatus.PENDING)));
        service.accept("delegation-1", "angel@example.test");

        when(delegations.findById("delegation-1")).thenReturn(Optional.of(active().status(DelegationStatus.PENDING)));
        service.decline("delegation-1", "angel@example.test");

        verifyNoInteractions(events);
        verifyNoInteractions(entityEvents);
    }

    /**
     * A patient whose profile cannot be found produces no frame on either channel.
     *
     * <p>{@code patient-events} refuses a keyless frame on the envelope rather than at the call site, and the new channel
     * does the same for the same reason: the gateway's mailer declines to write to a blank address, so a frame nobody can
     * attribute is one nobody can act on. Asserted as a pair, because the two topics carrying different <em>sets</em> of
     * frames is exactly what would make the eventual rebind lose something.</p>
     */
    @Test
    void anUnresolvableProfileIsRefusedOnBothChannels() {
        when(profiles.findByPatientId("patient-1")).thenReturn(List.of());
        when(profiles.findById("patient-1")).thenReturn(Optional.empty());
        when(delegations.findById("delegation-1")).thenReturn(Optional.of(active()));

        service.revoke("delegation-1", "angel@example.test");

        // Both are called — the refusal lives in each publisher, on the envelope — and both refuse. The point is that
        // neither call site decides, so the two channels cannot come to disagree about what a keyless frame is.
        verify(events).publish(eq(PatientEventType.CARE_DELEGATION_CHANGED), eq(null), any(), eq(null), any());
        verify(entityEvents).publishCareDelegationChanged(anyString(), anyString(), eq(null), anyString());
    }
}
