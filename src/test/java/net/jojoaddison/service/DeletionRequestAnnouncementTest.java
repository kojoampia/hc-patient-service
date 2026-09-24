package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.jojoaddison.domain.DeletionRequest;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.enumeration.DeletionRequestStatus;
import net.jojoaddison.repository.DeletionRequestRepository;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.service.event.PatientEventPublisher;
import net.jojoaddison.service.event.PatientEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * That a patient is told when their deletion request moves.
 *
 * <p>Until 2026-08-31 they were told nothing. A request was raised and then, whether it was carried out or refused,
 * the patient heard only what they happened to see by signing back in — for the one irreversible thing they can ask
 * for, which is the worst place in this product to be silent.</p>
 *
 * <p>This service can neither send mail nor close an account, so what it owes is an event. The gateway consumes it,
 * exactly as it already does for {@code CareDelegationChanged}.</p>
 */
class DeletionRequestAnnouncementTest {

    private DeletionRequestRepository repository;
    private PatientErasureService erasure;
    private PatientEventPublisher events;
    private ProfileRepository profiles;
    private DeletionRequestService service;

    @BeforeEach
    void setUp() {
        repository = mock(DeletionRequestRepository.class);
        erasure = mock(PatientErasureService.class);
        events = mock(PatientEventPublisher.class);
        profiles = mock(ProfileRepository.class);
        service = new DeletionRequestService(repository, erasure, events, profiles);

        when(repository.save(any(DeletionRequest.class))).thenAnswer(call -> call.getArgument(0));
        when(repository.findOneByPatientIdAndStatus(anyString(), any())).thenReturn(java.util.Optional.empty());
        when(erasure.erase(anyString(), anyString())).thenReturn(Map.of("conditions", 3L));
        // The profile exists and is linked, which is the ordinary case. The completion tests override this with an
        // empty answer, because by the time COMPLETED is announced the erasure has taken the profile.
        when(profiles.findByPatientId("patient-1")).thenReturn(List.of(new Profile().patientId("patient-1").accountId("account-kojo")));
    }

    private DeletionRequest pending() {
        return new DeletionRequest()
            .patientId("patient-1")
            .requestedByEmail("kojo@example.test")
            .requestedByLogin("kojo")
            .status(DeletionRequestStatus.PENDING)
            .requestedAt(Instant.now())
            .dueAt(Instant.now().plusSeconds(3600));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureData() {
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        // any() rather than anyString() for the account id: null is a legitimate frame there — it is what the
        // COMPLETED transition carries by construction — and anyString() refuses null.
        verify(events).publish(eq(PatientEventType.DELETION_REQUEST_CHANGED), anyString(), any(), any(), data.capture());
        return data.getValue();
    }

    @Test
    void raisingAnnouncesItWithTheDateTheErasureIsOwedBy() {
        service.raise("patient-1", "kojo@example.test", "kojo", "moving abroad");

        assertThat(captureData()).containsEntry("change", "RAISED").containsKey("dueAt");
    }

    @Test
    void theSubjectCarriesTheGatewayAccountIdReadFromTheProfile() {
        // The subject's third component is the gateway User.id since 2026-09-24, resolved at announce time because
        // the request does not store it. Not the internal patientId — that never leaves this subsystem's vocabulary.
        service.raise("patient-1", "kojo@example.test", "kojo", "moving abroad");

        verify(events).publish(eq(PatientEventType.DELETION_REQUEST_CHANGED), anyString(), any(), eq("account-kojo"), any());
    }

    @Test
    void withdrawingAnnouncesIt() {
        service.cancel(pending());

        assertThat(captureData()).containsEntry("change", "CANCELLED");
    }

    @Test
    void refusingAnnouncesItWithoutTheAdministratorsWords() {
        service.reject(pending(), "admin", "We could not confirm this was you.");

        Map<String, Object> data = captureData();
        assertThat(data).containsEntry("change", "REJECTED");
        // An administrator's free text is unbounded and could hold anything, including something clinical.
        // The patient reads it on their own request through GET /api/deletion-requests/mine; the mail only
        // says a decision was made.
        assertThat(data).doesNotContainKey("decisionReason");
        assertThat(data.values()).noneMatch(value -> String.valueOf(value).contains("could not confirm"));
    }

    @Test
    void completingStillCarriesAnEmail_afterTheProfileItWouldHaveBeenLookedUpFromIsGone() {
        // The point of the whole design. The erasure takes the Profile with it, so by the time this event is
        // built there is nothing to resolve an email from — requestedByEmail is stored at raise() precisely so
        // this still works. Publishing before the erasure instead would announce a completion that could fail.
        when(profiles.findByPatientId("patient-1")).thenReturn(List.of());

        service.complete(pending(), "admin");

        ArgumentCaptor<String> email = ArgumentCaptor.forClass(String.class);
        verify(events).publish(eq(PatientEventType.DELETION_REQUEST_CHANGED), email.capture(), any(), any(), any());

        assertThat(email.getValue()).isEqualTo("kojo@example.test");
    }

    @Test
    void completingCarriesANullAccountId_becauseTheProfileThatHeldTheLinkIsGone() {
        // The account id has no stored copy on the request, deliberately: a copy would outlive the record it names,
        // on the one event whose whole meaning is that the record is gone. Null honestly says the link no longer
        // resolves. If a consumer ever needs the id on this frame, the fix is a stored-at-raise field on
        // DeletionRequest — a domain change, decided on its own, not a lookup that cannot succeed.
        when(profiles.findByPatientId("patient-1")).thenReturn(List.of());

        service.complete(pending(), "admin");

        ArgumentCaptor<String> accountId = ArgumentCaptor.forClass(String.class);
        verify(events).publish(eq(PatientEventType.DELETION_REQUEST_CHANGED), anyString(), any(), accountId.capture(), any());
        assertThat(accountId.getValue()).isNull();
    }

    @Test
    void completingDoesNotSayHowMuchWasErased() {
        service.complete(pending(), "admin");

        // How many conditions a patient had is a fact about their record. §8.4: an event reports that something
        // happened, never what it said. PatientEventPublisher.assertNothingClinical would not catch this one,
        // because the offending key would be `erasedCounts` rather than a clinical word.
        assertThat(captureData()).doesNotContainKey("erasedCounts");
    }

    @Test
    void theErasureRunsBeforeAnythingIsAnnounced() {
        service.complete(pending(), "admin");

        var order = org.mockito.Mockito.inOrder(erasure, events);
        order.verify(erasure).erase(anyString(), anyString());
        order.verify(events).publish(anyString(), anyString(), any(), any(), any());
        verify(events, times(1)).publish(anyString(), anyString(), any(), any(), any());
    }
}
