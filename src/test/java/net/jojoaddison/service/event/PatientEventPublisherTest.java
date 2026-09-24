package net.jojoaddison.service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;

/**
 * The two rules {@link PatientEventPublisher} exists to enforce.
 *
 * <p>Both are the kind that hold right up until somebody adds one more field or tightens one more error path, which is
 * why they are pinned here rather than left to the class comment.</p>
 *
 * <p>Sends are asynchronous since backlog item 71, so every verification of the bridge carries a timeout — the same
 * rule {@link EntityEventPublisherTest} states: a bare {@code verify} races the sender thread and fails
 * intermittently, which is worse than not testing it, because the flake would eventually be "fixed" by deleting the
 * assertion. The timeout is a poll, not a sleep: it returns the moment the send lands and fails reliably when it
 * never does. The two REFUSALS stay verified without one, deliberately — they are synchronous by design
 * ({@code verifyNoInteractions} after a refusal is only meaningful because nothing was ever queued), and a timeout
 * on them would paper over that property going missing.</p>
 */
class PatientEventPublisherTest {

    private static final long SEND_TIMEOUT_MS = 5_000;

    @Test
    void theEnvelopeCarriesWhatAConsumerNeedsToCorrelateAndDeduplicate() {
        StreamBridge bridge = mock(StreamBridge.class);
        new PatientEventPublisher(bridge, new SimpleMeterRegistry())
            .publish(
                PatientEventType.ONBOARDING_STARTED,
                "  Ama@Example.Test ",
                "ama",
                "account-1",
                Map.of("startedAt", "2026-08-19T09:00:00Z")
            );

        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.captor();
        verify(bridge, timeout(SEND_TIMEOUT_MS)).send(eq(PatientEventPublisher.BINDING), captor.capture());
        PatientEvent event = (PatientEvent) captor.getValue().getPayload();

        assertThat(event.eventId()).as("the idempotency key; delivery is at least once").isNotBlank();
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.source()).isEqualTo("hcPatientService");
        assertThat(event.version()).isEqualTo(PatientEvent.VERSION);
        // Lowercased and trimmed here, so every producer agrees on the correlation key without having to remember to.
        assertThat(event.subject().email()).isEqualTo("ama@example.test");
        // The gateway User.id, carried through untouched — the same field the gateway's own publisher sends, so a
        // consumer joins either producer's frames on one key. The internal patientId no longer travels here.
        assertThat(event.subject().accountId()).isEqualTo("account-1");
        assertThat(captor.getValue().getHeaders().get(PatientEventPublisher.KEY_HEADER))
            .as("the partition key, so one patient's events stay in order")
            .isEqualTo("ama@example.test");
    }

    @Test
    void anEventWithNoSubjectKeyIsRefusedRatherThanSent() {
        // Every consumer of this stream keys on the email. hc-admin's SiblingEventParser drops a keyless frame BEFORE
        // it reads subject.patientId and acks it with no dead-letter record, and the gateway's CareDelegationMailer
        // declines to send on a blank address — so an unkeyed frame is not a partial event, it is one nobody can
        // attribute. Blank counts as absent: "" is what a profile with an empty email field yields, and it used to
        // reach the wire as a zero-length Kafka key, which is a key, so every such frame in the estate hashed to one
        // partition rather than being spread.
        StreamBridge bridge = mock(StreamBridge.class);
        PatientEventPublisher publisher = new PatientEventPublisher(bridge, new SimpleMeterRegistry());

        publisher.publish(PatientEventType.PLAN_CHOSEN, null, null, "account-1", Map.of());
        publisher.publish(PatientEventType.PLAN_CHOSEN, "", null, "account-1", Map.of());
        publisher.publish(PatientEventType.PLAN_CHOSEN, "   ", null, "account-1", Map.of());

        verifyNoInteractions(bridge);
    }

    @Test
    void aFailedPublishNeverReachesTheCaller() {
        StreamBridge bridge = mock(StreamBridge.class);
        doThrow(new IllegalStateException("broker down")).when(bridge).send(any(String.class), any(Message.class));

        // The write has already happened by this point. Losing the event costs observability; propagating the failure
        // would cost the patient their onboarding. Since item 71 the send runs on the sender thread, so this
        // assertion alone would pass vacuously — the timeout-verify below is what keeps it honest, proving the
        // throwing send actually RAN and its throw reached nobody.
        assertThatCode(() ->
                new PatientEventPublisher(bridge, new SimpleMeterRegistry())
                    .publish(PatientEventType.ONBOARDING_COMPLETED, "ama@example.test", null, "p1", Map.of())
            )
            .doesNotThrowAnyException();

        verify(bridge, timeout(SEND_TIMEOUT_MS)).send(any(String.class), any(Message.class));
    }

    /**
     * ⛔ The one-word edit that would silently restore the sixty-second block.
     *
     * <p>{@code CallerRunsPolicy} is the conventional choice for a bounded queue and it hands the blocking send back
     * to the request thread exactly when the broker is slowest — the same pin {@link EntityEventPublisherTest}
     * carries, per instance because each publisher owns its own {@link AsyncEventSender}.</p>
     */
    @Test
    void aFullQueueDropsTheFrameRatherThanRunningItOnTheCallersThread() {
        PatientEventPublisher publisher = new PatientEventPublisher(mock(StreamBridge.class), new SimpleMeterRegistry());

        assertThat(publisher.senderForTest().getRejectedExecutionHandler()).isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
    }

    /**
     * Item 73's gate: the counter is watched moving, on a real drop, driven through this publisher's own queue.
     *
     * <p>The bridge is wedged on a latch (a stand-in for a hung broker), the single sender thread blocks inside the
     * first send, the queue fills, and the next publish drops — counter 0 before, exactly 1 after, under this
     * stream's own {@code topic} tag. A counter that is registered but never incremented is precisely the kind of
     * check this backlog keeps cataloguing, which is why this does not settle for asserting registration.</p>
     */
    @Test
    void aDroppedFrameMovesTheCounterUnderThisStreamsTag() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CountDownLatch wedge = new CountDownLatch(1);
        CountDownLatch occupied = new CountDownLatch(1);
        StreamBridge bridge = mock(StreamBridge.class);
        org.mockito.Mockito
            .when(bridge.send(any(String.class), any(Message.class)))
            .thenAnswer(call -> {
                occupied.countDown();
                wedge.await();
                return true;
            });
        PatientEventPublisher publisher = new PatientEventPublisher(bridge, registry);

        try {
            // First publish occupies the sender thread inside the wedged send; wait until it provably has.
            publisher.publish(PatientEventType.ONBOARDING_STARTED, "ama@example.test", null, "account-1", Map.of());
            assertThat(occupied.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

            double before = registry
                .get(DroppedEventCounter.METER_NAME)
                .tag(DroppedEventCounter.TOPIC_DIMENSION, "patient-events")
                .counter()
                .count();
            assertThat(before).as("nothing has dropped yet").isZero();

            // Fill the queue, then one more: the overflow is the drop.
            for (int i = 0; i < 129; i++) {
                publisher.publish(PatientEventType.ONBOARDING_STEP_COMPLETED, "ama@example.test", null, "account-1", Map.of());
            }

            double after = registry
                .get(DroppedEventCounter.METER_NAME)
                .tag(DroppedEventCounter.TOPIC_DIMENSION, "patient-events")
                .counter()
                .count();
            assertThat(after).as("the one overflow publish is the one counted drop").isEqualTo(1.0);
        } finally {
            wedge.countDown();
        }
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
        PatientEventPublisher publisher = new PatientEventPublisher(bridge, new SimpleMeterRegistry());

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
        new PatientEventPublisher(bridge, new SimpleMeterRegistry())
            .publish(
                PatientEventType.ONBOARDING_STEP_COMPLETED,
                "ama@example.test",
                null,
                "p1",
                Map.of("step", 4, "stepName", "currentState", "completedAt", "2026-08-19T09:00:00Z")
            );

        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.captor();
        verify(bridge, timeout(SEND_TIMEOUT_MS)).send(eq(PatientEventPublisher.BINDING), captor.capture());
        PatientEvent event = (PatientEvent) captor.getValue().getPayload();

        assertThat(event.data()).containsOnlyKeys("step", "stepName", "completedAt");
    }
}
