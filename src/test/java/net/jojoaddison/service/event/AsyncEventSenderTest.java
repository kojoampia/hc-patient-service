package net.jojoaddison.service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * The executor both async publishers stand on, pinned where its behaviour is a policy rather than an accident.
 *
 * <p>The drop path is the one most worth watching actually happen: backlog item 71 chose dropping over blocking with
 * the trade stated, and the difference between "the queue rejects" and "the caller runs the send" is invisible to
 * every other test in the suite — a {@code CallerRunsPolicy} executor passes them all, sixty-second stall included.
 * So {@link #aFullQueueRefusesTheOfferAndTheCallerNeverRunsTheSend} does not read the policy off the executor; it
 * fills a real queue against a deliberately wedged thread and watches the offer come back {@code false} with the
 * calling thread's hands clean. Latches, not sleeps: a timing-dependent test that passes on an idle box is exactly
 * the instrument this estate keeps catching.</p>
 */
class AsyncEventSenderTest {

    @Test
    void sendsRunOffTheCallingThreadInSubmissionOrder() throws Exception {
        AsyncEventSender sender = new AsyncEventSender("async-sender-test-order", 8, () -> {});
        List<String> order = new CopyOnWriteArrayList<>();
        AtomicReference<String> senderThread = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(3);

        for (String label : List.of("first", "second", "third")) {
            assertThat(
                sender.offer(() -> {
                    order.add(label);
                    senderThread.set(Thread.currentThread().getName());
                    done.countDown();
                })
            )
                .isTrue();
        }

        assertThat(done.await(5, TimeUnit.SECONDS)).as("the sender thread must actually run what it accepted").isTrue();
        // Single thread, FIFO queue: submission order IS send order. This is the property the backlog entry's
        // original ordering objection missed, and the one that keeps one patient's frames in write order.
        assertThat(order).containsExactly("first", "second", "third");
        assertThat(senderThread.get())
            .as("the configured thread name, so a dump attributes a stuck send")
            .isEqualTo("async-sender-test-order");
    }

    @Test
    void aThrowingDropCallbackNeverEscapesOntoTheCallingThread() throws Exception {
        // The calling thread here is the request thread, which is the one item 71 exists to keep clear. Today
        // `onDrop` is Counter::increment and cannot throw; this pins the property so a future callback that does
        // cannot undo item 71 by way of item 73's own instrumentation. A frame is already being dropped at this
        // point — losing its count too is strictly better than failing a patient's write because bookkeeping threw.
        AsyncEventSender sender = new AsyncEventSender(
            "async-sender-test-throwing-drop",
            1,
            () -> {
                throw new IllegalStateException("the drop callback is broken");
            }
        );
        CountDownLatch wedge = new CountDownLatch(1);
        CountDownLatch occupied = new CountDownLatch(1);

        try {
            assertThat(
                sender.offer(() -> {
                    occupied.countDown();
                    try {
                        wedge.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                })
            )
                .isTrue();
            assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(sender.offer(() -> {})).isTrue();

            // Queue full, so onDrop runs — and throws. The caller must still get its ordinary false.
            assertThatCode(() -> assertThat(sender.offer(() -> {})).isFalse())
                .as("a throwing drop callback must not reach the caller — this is the request thread")
                .doesNotThrowAnyException();
        } finally {
            wedge.countDown();
        }
    }

    @Test
    void aFullQueueRefusesTheOfferAndTheCallerNeverRunsTheSend() throws Exception {
        AtomicInteger drops = new AtomicInteger();
        AsyncEventSender sender = new AsyncEventSender("async-sender-test-drop", 1, drops::incrementAndGet);
        CountDownLatch wedge = new CountDownLatch(1);
        CountDownLatch occupied = new CountDownLatch(1);
        List<String> ranOn = new CopyOnWriteArrayList<>();

        try {
            // Wedge the single thread — a stand-in for a send blocked on a hung broker — and wait until it is
            // provably inside the task, so the next offers exercise the queue and not a race with thread start.
            assertThat(
                sender.offer(() -> {
                    occupied.countDown();
                    try {
                        wedge.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                })
            )
                .isTrue();
            assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();

            // One slot in the queue: accepted — and no drop has been counted yet (item 73).
            assertThat(sender.offer(() -> ranOn.add(Thread.currentThread().getName()))).isTrue();
            assertThat(drops.get()).as("accepted offers must not count as drops").isZero();

            // Queue full. The offer must come back false — and the send must NOT have run here, which is the
            // CallerRunsPolicy defect this class exists to rule out. Watched, not read off the handler.
            boolean accepted = sender.offer(() -> ranOn.add(Thread.currentThread().getName()));
            assertThat(accepted).as("a full queue drops; it never blocks and never borrows the caller").isFalse();
            assertThat(ranOn).as("nothing may have run on the calling thread while the queue was full").isEmpty();
            // Item 73: the refused offer is COUNTED, exactly once, before offer() answers — a drop must be a graph,
            // not a grep, and a counter registered but never incremented is the defect this assertion exists for.
            assertThat(drops.get()).as("the one refused offer is the one counted drop").isEqualTo(1);

            // And the policy object agrees with the observed behaviour — belt to the braces above.
            assertThat(sender.executorForTest().getRejectedExecutionHandler()).isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
        } finally {
            wedge.countDown();
        }

        // Once unwedged, the accepted frame still goes out — dropped means the third offer only.
        assertThat(sender.drain(Duration.ofSeconds(5))).isTrue();
        assertThat(ranOn).containsExactly("async-sender-test-drop");
        assertThat(drops.get()).as("draining the accepted work counts no further drops").isEqualTo(1);
    }

    @Test
    void drainGivesQueuedWorkItsChanceAndReportsAbandonmentHonestly() throws Exception {
        // The @PreDestroy path: a frame queued as a pod stops gets a bounded chance to go out.
        AsyncEventSender drains = new AsyncEventSender("async-sender-test-drain", 8, () -> {});
        CountDownLatch ran = new CountDownLatch(1);
        assertThat(drains.offer(ran::countDown)).isTrue();

        assertThat(drains.drain(Duration.ofSeconds(5))).as("an idle-enough queue drains clean").isTrue();
        assertThat(ran.await(0, TimeUnit.SECONDS)).as("drain returning true means the work actually ran").isTrue();

        // And the other honest answer: a wedged send cannot hold a shutdown open past its patience.
        AsyncEventSender wedged = new AsyncEventSender("async-sender-test-wedged", 8, () -> {});
        CountDownLatch forever = new CountDownLatch(1);
        CountDownLatch inside = new CountDownLatch(1);
        wedged.offer(() -> {
            inside.countDown();
            try {
                forever.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            assertThat(wedged.drain(Duration.ofMillis(50))).as("a deployment must not wait on the broker it is replacing").isFalse();
        } finally {
            forever.countDown();
        }
    }
}
