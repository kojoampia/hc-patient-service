package net.jojoaddison.service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.jojoaddison.domain.Membership;
import net.jojoaddison.domain.enumeration.MembershipStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;

/**
 * The envelope of the patient-facing push, and the executor that keeps it off the request thread.
 *
 * <p>This publisher had no unit test of its own before backlog item 71 — its behaviour was pinned through
 * {@code MembershipPlanEventTest}'s broken-broker case and the round-trip IT. Item 71 gave it a thread, and a thread
 * needs pins a pass-through did not: the send must reach the bridge (with a timeout, because a bare verify races the
 * sender), the no-owner refusal must stay synchronous, and the rejection policy must never become
 * {@code CallerRunsPolicy} — the one-word edit that would silently restore the sixty-second block this class exists
 * to prevent.</p>
 */
class MembershipStreamPublisherTest {

    private static final long SEND_TIMEOUT_MS = 5_000;

    private static Membership membership(String patientId) {
        return new Membership().id("membership-1").patientId(patientId).plan("PAWPAW").status(MembershipStatus.ACTIVE);
    }

    @Test
    void aMembershipChangeReachesTheBridgeKeyedOnThePatient() {
        StreamBridge bridge = mock(StreamBridge.class);

        new MembershipStreamPublisher(bridge, new SimpleMeterRegistry()).publish(membership("patient-1"));

        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.captor();
        verify(bridge, timeout(SEND_TIMEOUT_MS)).send(eq(MembershipStreamPublisher.BINDING), captor.capture());

        MembershipChangedEvent event = (MembershipChangedEvent) captor.getValue().getPayload();
        assertThat(event.patientId()).isEqualTo("patient-1");
        assertThat(event.membershipId()).isEqualTo("membership-1");
        assertThat(event.status()).isEqualTo("ACTIVE");
        // The partition key is the patientId — both ends of this topic live in this repository, so no email lookup
        // runs inside a Kafka handler. See the class javadoc for why the header name matches patient-events anyway.
        assertThat(captor.getValue().getHeaders().get(MembershipStreamPublisher.KEY_HEADER)).isEqualTo("patient-1");
    }

    @Test
    void aMembershipWithNoOwnerIsRefusedSynchronouslyRatherThanQueued() {
        // The refusal stays on the calling thread by design — it decides whether there is anything to queue at all.
        // verifyNoInteractions with no timeout is only sound BECAUSE nothing was queued: if this refusal ever moves
        // onto the sender thread, this test starts passing for the wrong reason, and the assertion on the executor's
        // queue below is what would catch that.
        StreamBridge bridge = mock(StreamBridge.class);
        MembershipStreamPublisher publisher = new MembershipStreamPublisher(bridge, new SimpleMeterRegistry());

        publisher.publish(membership(null));
        publisher.publish(membership("   "));

        verifyNoInteractions(bridge);
        assertThat(publisher.senderForTest().getQueue()).as("a refused frame must never be queued either").isEmpty();
    }

    @Test
    void aFailedPushNeverReachesTheCaller() {
        StreamBridge bridge = mock(StreamBridge.class);
        doThrow(new IllegalStateException("broker hung")).when(bridge).send(any(String.class), any(Message.class));

        assertThatCode(() -> new MembershipStreamPublisher(bridge, new SimpleMeterRegistry()).publish(membership("patient-1")))
            .doesNotThrowAnyException();

        // The timeout-verify keeps the assertion above honest: the throwing send RAN, on the sender thread, and its
        // throw reached nobody — including the sender thread's own next frame, which the catch in send() protects.
        verify(bridge, timeout(SEND_TIMEOUT_MS)).send(any(String.class), any(Message.class));
    }

    /**
     * Item 73's gate, and the pin that the tag answers "WHICH stream lost frames".
     *
     * <p>One registry serves two publishers; a drop driven through this one moves the counter under
     * {@code topic=patient-membership-events} to exactly 1 while {@code topic=patient-events} stays at 0. A single
     * undifferentiated counter would pass a moved-once assertion and be unable to answer the only question the
     * meter exists for.</p>
     */
    @Test
    void aDropCountsUnderThisStreamsTagAndNotItsNeighbours() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CountDownLatch wedge = new CountDownLatch(1);
        CountDownLatch occupied = new CountDownLatch(1);
        StreamBridge bridge = mock(StreamBridge.class);
        org.mockito.Mockito
            .when(bridge.send(org.mockito.ArgumentMatchers.any(String.class), org.mockito.ArgumentMatchers.any(Message.class)))
            .thenAnswer(call -> {
                occupied.countDown();
                wedge.await();
                return true;
            });
        MembershipStreamPublisher publisher = new MembershipStreamPublisher(bridge, registry);
        // The neighbour registers its counter against the same registry and never drops.
        new PatientEventPublisher(mock(StreamBridge.class), registry);

        try {
            publisher.publish(membership("patient-1"));
            assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();

            for (int i = 0; i < 129; i++) {
                publisher.publish(membership("patient-1"));
            }

            assertThat(
                registry
                    .get(DroppedEventCounter.METER_NAME)
                    .tag(DroppedEventCounter.TOPIC_DIMENSION, "patient-membership-events")
                    .counter()
                    .count()
            )
                .as("the one overflow publish is the one counted drop")
                .isEqualTo(1.0);
            assertThat(
                registry.get(DroppedEventCounter.METER_NAME).tag(DroppedEventCounter.TOPIC_DIMENSION, "patient-events").counter().count()
            )
                .as("the neighbour stream lost nothing and must read so")
                .isZero();
        } finally {
            wedge.countDown();
        }
    }

    /**
     * ⛔ The one-word edit that would silently restore the sixty-second block.
     *
     * <p>{@code CallerRunsPolicy} hands the blocking send back to the request thread exactly when the broker is
     * slowest. Pinned per instance — this publisher's executor is deliberately its own, not shared, so the pin on
     * {@code EntityEventPublisher}'s says nothing about this one.</p>
     */
    @Test
    void aFullQueueDropsTheFrameRatherThanRunningItOnTheCallersThread() {
        MembershipStreamPublisher publisher = new MembershipStreamPublisher(mock(StreamBridge.class), new SimpleMeterRegistry());

        assertThat(publisher.senderForTest().getRejectedExecutionHandler()).isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
    }
}
