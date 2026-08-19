package net.jojoaddison.service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;

/**
 * The two rules {@link PatientEventPublisher} exists to enforce.
 *
 * <p>Both are the kind that hold right up until somebody adds one more field or tightens one more error path, which is
 * why they are pinned here rather than left to the class comment.</p>
 */
class PatientEventPublisherTest {

    @Test
    void theEnvelopeCarriesWhatAConsumerNeedsToCorrelateAndDeduplicate() {
        StreamBridge bridge = mock(StreamBridge.class);
        new PatientEventPublisher(bridge)
            .publish(
                PatientEventType.ONBOARDING_STARTED,
                "  Ama@Example.Test ",
                "ama",
                "patient-1",
                Map.of("startedAt", "2026-08-19T09:00:00Z")
            );

        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.captor();
        verify(bridge).send(eq(PatientEventPublisher.BINDING), captor.capture());
        PatientEvent event = (PatientEvent) captor.getValue().getPayload();

        assertThat(event.eventId()).as("the idempotency key; delivery is at least once").isNotBlank();
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.source()).isEqualTo("hcPatientService");
        assertThat(event.version()).isEqualTo(PatientEvent.VERSION);
        // Lowercased and trimmed here, so every producer agrees on the correlation key without having to remember to.
        assertThat(event.subject().email()).isEqualTo("ama@example.test");
        assertThat(event.subject().patientId()).isEqualTo("patient-1");
        assertThat(captor.getValue().getHeaders().get(PatientEventPublisher.KEY_HEADER))
            .as("the partition key, so one patient's events stay in order")
            .isEqualTo("ama@example.test");
    }

    @Test
    void aFailedPublishNeverReachesTheCaller() {
        StreamBridge bridge = mock(StreamBridge.class);
        doThrow(new IllegalStateException("broker down")).when(bridge).send(any(String.class), any(Message.class));

        // The write has already happened by this point. Losing the event costs observability; propagating the failure
        // would cost the patient their onboarding.
        assertThatCode(() ->
                new PatientEventPublisher(bridge).publish(PatientEventType.ONBOARDING_COMPLETED, "ama@example.test", null, "p1", Map.of())
            )
            .doesNotThrowAnyException();
    }

    /**
     * The guard that keeps the record off the wire.
     *
     * <p>If this test is ever "fixed" by removing a key from the denylist, read {@link PatientEventPublisher}'s class
     * comment first: a topic is the least controlled copy of anything that enters it, and the record is otherwise
     * protected by {@code PatientScope} refusing cross-patient reads.</p>
     */
    @Test
    void anEventCannotCarryClinicalContent() {
        StreamBridge bridge = mock(StreamBridge.class);
        PatientEventPublisher publisher = new PatientEventPublisher(bridge);

        for (String key : new String[] { "bloodGroup", "allergies", "medications", "conditions", "cardNumber", "address", "diagnosis" }) {
            assertThatThrownBy(() ->
                    publisher.publish(PatientEventType.ONBOARDING_STEP_COMPLETED, "ama@example.test", null, "p1", Map.of(key, "anything"))
                )
                .as("payload key %s", key)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clinical");
        }
    }

    @Test
    void aStepEventSaysThatAStepHappenedAndNotWhatItSaid() {
        StreamBridge bridge = mock(StreamBridge.class);
        new PatientEventPublisher(bridge)
            .publish(
                PatientEventType.ONBOARDING_STEP_COMPLETED,
                "ama@example.test",
                null,
                "p1",
                Map.of("step", 4, "stepName", "currentState", "completedAt", "2026-08-19T09:00:00Z")
            );

        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.captor();
        verify(bridge).send(eq(PatientEventPublisher.BINDING), captor.capture());
        PatientEvent event = (PatientEvent) captor.getValue().getPayload();

        assertThat(event.data()).containsOnlyKeys("step", "stepName", "completedAt");
    }
}
