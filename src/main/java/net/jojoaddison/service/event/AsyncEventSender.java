package net.jojoaddison.service.event;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A single-threaded, bounded, drop-on-full executor for handing a Kafka send off the request thread.
 *
 * <p>Backlog item 71. A broker that is <em>down</em> refuses fast and every publisher here swallows; a broker that is
 * <em>hung</em> — accepting the TCP connection and not answering — blocks a send for {@code max.block.ms}, which is
 * set nowhere in this estate and therefore Kafka's default of sixty seconds. On the request thread that is a patient
 * told their membership failed when it exists, past nginx's timeout, on a request whose database write already
 * succeeded. {@link EntityEventPublisher} solved this for its own stream first and its javadoc carries the full
 * argument; this class is that executor's shape extracted so {@link PatientEventPublisher} and
 * {@link MembershipStreamPublisher} do not carry two more hand-rolled copies. <strong>{@code EntityEventPublisher}
 * itself deliberately keeps its inline original</strong> — item 71's scope is the two unprotected publishers, and
 * folding a guarded, pinned class into a refactor is how a deliberate policy becomes a casualty of tidying.</p>
 *
 * <h2>⛔ One instance per publisher. Never a shared bean.</h2>
 *
 * <p>The queue is the coupling. {@code EntityEventPublisher} fires on <em>every write in the service</em> — hc-admin
 * counts 1236 documents on one dev boot — while {@code patient-events} carries seven curated lifecycle moments that
 * feed hc-admin's watermark, where a gap is meaningful, and {@code patient-membership-events} carries the push a
 * patient is watching for. Share one queue and the noisiest stream decides what the quietest loses: a burst of entity
 * changes against a slow broker would fill the 512 slots and the frame dropped would be somebody's {@code PlanChosen}.
 * Separate instances cost one daemon thread each and make each stream's loss its own load, not its neighbour's.</p>
 *
 * <h2>⛔ The rejection policy must stay {@link ThreadPoolExecutor.AbortPolicy}</h2>
 *
 * <p>{@code CallerRunsPolicy} is the conventional choice for a bounded queue and it would silently restore exactly
 * the defect this class exists to prevent — handing the blocking send back to the request thread the moment the queue
 * fills, which is the moment the broker is slowest. The publishers' unit tests pin it per instance.</p>
 *
 * <h2>Ordering survives, because the pool is {@code (1, 1)}</h2>
 *
 * <p>One thread means submission order is send order, so the per-patient ordering that
 * {@code messageKeyExpression} partitioning provides is unchanged: one patient's frames are submitted in write order,
 * sent in that order, and land on one partition. What was never guaranteed — ordering <em>across</em> topics — is
 * still not, and going asynchronous does not widen that.</p>
 */
final class AsyncEventSender {

    private final ThreadPoolExecutor executor;

    /**
     * The one thing this class logs, and the exception proves the rule rather than breaking it.
     *
     * <p>It deliberately does not log <em>drops</em> — {@code offer} returns a boolean so each publisher can say
     * "dropped a membership push" or "dropped a patient event" in its own words, which are different pages to be
     * woken up to. A drop callback that <em>throws</em> is not a drop, though: it is a bug in the callback, and no
     * caller is in a position to notice it, because the whole point of catching it is that it never reaches one.</p>
     */
    private static final Logger log = LoggerFactory.getLogger(AsyncEventSender.class);

    private final Runnable onDrop;

    /**
     * @param threadName names the daemon thread, so a thread dump attributes a stuck send to its publisher.
     * @param queueCapacity bounded, and sized to absorb a burst rather than buffer an outage — against a hung broker
     *     every send blocks for a minute, so a big queue is minutes of stale frames and no signal.
     * @param onDrop runs once per refused offer, before {@code offer} answers {@code false} — in practice a
     *     {@code Counter::increment} (backlog item 73; {@link DroppedEventCounter} is the one definition). A
     *     <b>required</b> argument on purpose: counting at the call sites instead was the alternative, and its
     *     failure mode is a fourth publisher that wires a sender, drops, and forgets the counter with every test
     *     green — the shape this estate keeps finding. Making the constructor refuse to compile without an answer
     *     for drops moves that from a review catch to a type error. The WARN stays with the caller (each publisher
     *     names what it lost in its own words); this is the half that must not depend on remembering.
     */
    AsyncEventSender(String threadName, int queueCapacity, Runnable onDrop) {
        this.onDrop = onDrop;
        this.executor =
            new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, threadName);
                    // Daemon so a queue still draining cannot hold a shutdown open; drain() below gives it a bounded
                    // chance to finish first.
                    thread.setDaemon(true);
                    return thread;
                },
                // ⛔ Not CallerRunsPolicy. See the class javadoc.
                new ThreadPoolExecutor.AbortPolicy()
            );
    }

    /**
     * Queues the send, answering {@code false} when the queue is full so the caller can log a drop in its own words.
     *
     * <p>Returning a boolean rather than logging here keeps each publisher's WARN specific — "dropped a membership
     * push" and "dropped a patient event" are different pages to be woken up to.</p>
     */
    boolean offer(Runnable send) {
        try {
            executor.execute(send);
            return true;
        } catch (RejectedExecutionException e) {
            // The queue is full, which means the broker is not draining it. Dropping is the design; see the class
            // javadoc on why this must never become CallerRunsPolicy. The count happens HERE, where the drop does,
            // so no caller can forget it; the caller's false-branch WARN says what was lost.
            //
            // GUARDED, and the guard is the whole point of this class rather than defensive habit. `onDrop` runs on
            // the CALLING thread — the request thread this machinery exists to keep clear. Today it is
            // `Counter::increment`, which cannot throw; a future callback that does would escape `offer` into
            // `publish` and surface on the request path, undoing item 71 by way of item 73's own instrumentation.
            // A frame is already being dropped here; losing its count too is strictly better than failing a
            // patient's write because the bookkeeping threw.
            try {
                onDrop.run();
            } catch (RuntimeException dropCallbackFailed) {
                log.warn("A drop callback threw; the frame was dropped and its count may be short", dropCallbackFailed);
            }
            return false;
        }
    }

    /**
     * Gives the queue a bounded chance to drain on shutdown, answering {@code false} if frames were abandoned.
     *
     * <p>Bounded because a deployment must not wait on a broker that may be the reason it is being redeployed.</p>
     */
    boolean drain(Duration patience) {
        executor.shutdown();
        try {
            if (executor.awaitTermination(patience.toMillis(), TimeUnit.MILLISECONDS)) {
                return true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor.shutdownNow();
        return false;
    }

    /** For the tests that pin the rejection policy. The defect it guards against is a one-word edit. */
    ThreadPoolExecutor executorForTest() {
        return executor;
    }
}
