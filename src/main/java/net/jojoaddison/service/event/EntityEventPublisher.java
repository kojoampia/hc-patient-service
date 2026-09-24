package net.jojoaddison.service.event;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * Publishes every entity change in this subsystem to {@code patient.event}.
 *
 * <p>Backlog item 45, under the estate decision of 2026-09-17: one pub-sub channel per product carrying all entity
 * CRUD, so that a new consumer subscribes rather than negotiates a topic. hc-admin consumes this into their audit
 * trail (their item 109). The four channels are {@code patient.event}, {@code professional.event},
 * {@code vendor.event} and {@code admin.event} — dotted and singular, which is hc-professional's convention rather
 * than this repo's, because two products cannot each keep their own and also be consumed uniformly.</p>
 *
 * <p><strong>This is a NEW topic beside {@code patient-events}, which is untouched.</strong> That one is a lifecycle
 * stream — seven curated moments that matter to a subscriber's model — and it has a live consumer in hc-admin and a
 * mail router in this subsystem's own gateway. An audit trail needs every write, which is a different stream, not a
 * widening of that one. Retiring the old one is backlog item 46 and is explicitly not this work: add first, remove
 * nothing, and remove the consumer before the producer.</p>
 *
 * <h2>⚠ Nothing here may run on the request thread, and the reason is sixty seconds long</h2>
 *
 * <p>{@link StreamBridge} creates an output binding <em>lazily, inside the first {@code send()} for that
 * destination</em>, and that creation opens an AdminClient bounded by {@code default.api.timeout.ms}. Against a
 * broker that is not there it blocks for <strong>sixty seconds</strong>, under a lock every later publisher queues
 * on. hc-admin measured it: a first {@code POST} took 60.6s and the second 15ms, with the row written, a 201
 * returned and nothing failing.</p>
 *
 * <p>That is survivable for a handful of lifecycle events on a healthy deployment. It is not survivable here,
 * because this publisher fires on <em>every write in the service</em> — so the first write after any deployment
 * would pay it, and a deployment whose broker is genuinely absent would pay it on a persistence callback, which is
 * to say inside somebody's onboarding. Every send is therefore handed to the executor below and no caller waits.</p>
 *
 * <p>⛔ <strong>The rejection policy must stay {@link ThreadPoolExecutor.AbortPolicy}.</strong>
 * {@code CallerRunsPolicy} is the conventional choice for a bounded queue and it would silently restore exactly the
 * defect this class exists to prevent — handing the blocking send back to the request thread the moment the queue
 * filled, which is the moment the broker is slowest. {@code EntityEventPublisherTest} pins it.</p>
 *
 * <h2>Losing a frame is allowed. Delaying a write is not.</h2>
 *
 * <p>The same rule {@link PatientEventPublisher} states: by the time anything is published the write has already
 * happened. There is no outbox and no transaction to hang one on — Mongo runs standalone here — so best-effort
 * after a successful write is the honest design. Every failure is caught and logged, the queue is bounded, and a
 * full queue drops the frame rather than growing without limit.</p>
 */
@Component
public class EntityEventPublisher {

    /** The binding name; {@code application.yml} maps it to the {@code patient.event} destination. */
    public static final String BINDING = "entityEvents-out-0";

    /**
     * The header the Kafka binder reads to choose a partition key, via {@code messageKeyExpression}.
     *
     * <p>The <strong>entity id</strong>, not the actor and not a patient. Two changes to one document must stay in
     * order or an audit trail reports them in whichever order two partitions happened to be drained, which for a
     * create-then-delete pair is a trail saying the document still exists. Keying on the actor would order one
     * person's actions and scatter each document's history, which is the wrong half of the guarantee for this
     * stream — note it is the opposite choice from {@code patient-events}, and for the opposite reason.</p>
     */
    public static final String KEY_HEADER = "entityKey";

    private static final String SOURCE = "hcPatientService";

    /**
     * Every key a frame on this stream may carry — a closed allowlist, checked at runtime.
     *
     * <p><strong>An allowlist here, where {@link PatientEventPublisher} deliberately uses a denylist.</strong> That
     * one publishes payloads shaped by many call sites for many event types, so an unknown key is a new field
     * somebody is adding and the right answer is to refuse loudly at that point. This payload is a closed shape
     * built by one class: there is no legitimate new key, so anything unrecognised is a mistake, and an allowlist
     * catches the mistake this design is most exposed to — a field value reaching the wire because somebody found it
     * useful. The denylist is applied as well, below, because it costs nothing and the two fail differently.</p>
     *
     * <p>It is two keys rather than three since the 2026-09-18 envelope decision — {@code entityType} and
     * {@code entityId} moved into {@link EntityEvent.Subject}, where being record components makes them structurally
     * closed and puts them out of this guard's reach by construction rather than by omission.</p>
     */
    private static final Set<String> ALLOWED_KEYS = Set.of(EntityEvent.ACTION, EntityEvent.ACTOR_ACCOUNT_ID);

    /**
     * Bounded, and small on purpose.
     *
     * <p>The queue exists to absorb a burst, not to buffer an outage. Against an absent broker every send blocks for
     * a minute, so a large queue would hold minutes of stale frames and report nothing; a small one starts dropping
     * — with a log line each — while the write path stays at full speed, which is the failure this service wants.
     * The seed and migration volume that item 110 warns about (hc-admin counts 1236 documents on a dev boot) is
     * throughput, not burst, and drains at the broker's pace.</p>
     */
    private static final int QUEUE_CAPACITY = 512;

    private final Logger log = LoggerFactory.getLogger(EntityEventPublisher.class);

    private final StreamBridge streamBridge;

    private final ThreadPoolExecutor sender;

    /**
     * Incremented in the {@code RejectedExecutionException} catch below. This class keeps its inline executor (item
     * 71 deliberately left it untouched), so unlike the two {@code AsyncEventSender} publishers the count here is
     * NOT constructor-enforced — this is the one hand-remembered site, and {@code EntityEventPublisherTest} watches
     * it move so forgetting it cannot stay green.
     */
    private final Counter droppedFrames;

    public EntityEventPublisher(StreamBridge streamBridge, MeterRegistry meterRegistry) {
        this.streamBridge = streamBridge;
        this.droppedFrames = DroppedEventCounter.register(meterRegistry, "patient.event");
        this.sender =
            new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "entity-event-publisher");
                    // Daemon so a queue still draining cannot hold a shutdown open; PreDestroy below gives it a bounded
                    // chance to finish first.
                    thread.setDaemon(true);
                    return thread;
                },
                // ⛔ Not CallerRunsPolicy. See the class javadoc.
                new ThreadPoolExecutor.AbortPolicy()
            );
    }

    /**
     * Builds the envelope and hands it to the sender thread.
     *
     * @param entityType the domain class whose document changed. Carried in {@link EntityEvent.Subject}.
     * @param entityId the document's own id; a frame without one names nothing and is refused. Carried in
     *     {@link EntityEvent.Subject}.
     * @param action what happened. Carried in {@code data}.
     * @param actorAccountId the gateway {@code User.id} of whoever made the change, or null when this service cannot
     *     name them — see {@link net.jojoaddison.security.ActorAccountId}. Carried in {@code data}, per the
     *     2026-09-18 envelope decision. Null is a legitimate frame, unlike a null entity id.
     */
    public void publish(String entityType, String entityId, EntityChangeAction action, String actorAccountId) {
        if (entityType == null || entityType.isBlank() || entityId == null || entityId.isBlank() || action == null) {
            // Refused rather than sent. An audit row needs to name the thing that changed; a frame that cannot is one
            // no consumer can turn into a row, and publishing it would move the diagnosis into somebody else's log.
            log.warn("Not publishing an entity change — entityType, entityId or action was absent");
            return;
        }

        // LinkedHashMap rather than Map.of so the payload serializes in the order it is written here, which is the
        // order a reader of the topic wants — and because Map.of refuses a null value, which an unnameable actor is.
        // Map.of's iteration order is also deliberately randomised per JVM.
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(EntityEvent.ACTION, action.name());
        // Put unconditionally, null and all, so the payload has one shape rather than two. A consumer that has to
        // tell "no actor" from "this producer stopped sending the field" is being asked a question the wire cannot
        // answer; an explicit null says which.
        data.put(EntityEvent.ACTOR_ACCOUNT_ID, actorAccountId);
        assertIdentifiersOnly(data);
        // Defence in depth, and a different failure: the denylist catches a clinical key that somehow reached a
        // payload, the allowlist above catches any key that is not one of the two. Neither subsumes the other.
        PatientEventPublisher.assertNothingClinical(EntityEvent.TYPE, data);

        EntityEvent event = new EntityEvent(
            UUID.randomUUID().toString(),
            EntityEvent.TYPE,
            EntityEvent.VERSION,
            Instant.now(),
            SOURCE,
            new EntityEvent.Subject(entityType, entityId),
            data
        );

        // Built on the calling thread — where the security context and the request attributes exist — and sent on
        // the other one. Only the send is deferred.
        try {
            sender.execute(() -> send(event, entityId));
        } catch (RejectedExecutionException e) {
            // The queue is full, which means the broker is not draining it. Dropping is the design: see the class
            // javadoc on why this must never become CallerRunsPolicy. Counted as well as logged (item 73) — a run
            // of drops must be a graph, not a grep.
            droppedFrames.increment();
            log.warn("Dropped an entity change for {} — the publishing queue is full", entityType);
        }
    }

    private void send(EntityEvent event, String key) {
        try {
            // The boolean is worth reading: StreamBridge answers false for a binding it could not resolve rather than
            // throwing, which is a mis-wired producer failing quietly.
            boolean sent = streamBridge.send(BINDING, MessageBuilder.withPayload(event).setHeader(KEY_HEADER, key).build());
            if (!sent) {
                log.warn("Publishing an entity change was refused by the binder — check the {} binding", BINDING);
            }
        } catch (Exception e) {
            // Deliberately swallowed, and on a thread of its own, so it cannot reach the write that provoked it.
            log.warn("Could not publish an entity change — the record is unaffected", e);
        }
    }

    /**
     * Refuses a payload carrying anything but the action and the actor.
     *
     * <p>Throws rather than stripping: a dropped key would let the caller believe a field is being published, and the
     * next person to read the consumer would wonder why it never arrives. It throws on the <em>calling</em> thread,
     * which is deliberate — this is a programming error rather than a runtime condition, and it should surface in
     * the test that introduced it rather than as a log line on a background thread.</p>
     */
    static void assertIdentifiersOnly(Map<String, Object> data) {
        for (String key : data.keySet()) {
            if (!ALLOWED_KEYS.contains(key)) {
                throw new IllegalArgumentException(
                    "An entity event may not carry '" +
                    key +
                    "'. This stream says that a document changed, never what it now holds — see EntityEvent."
                );
            }
        }
    }

    /**
     * Gives the queue a bounded chance to drain on shutdown.
     *
     * <p>Two seconds, because the frames are notifications and a deployment must not wait on a broker that may be
     * the reason it is being redeployed.</p>
     */
    @PreDestroy
    void drain() {
        sender.shutdown();
        try {
            if (!sender.awaitTermination(2, TimeUnit.SECONDS)) {
                log.warn("Shutting down with entity changes still queued — they are lost, and the records are unaffected");
                sender.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sender.shutdownNow();
        }
    }

    /** For the test that pins the rejection policy. The defect it guards against is a one-word edit. */
    ThreadPoolExecutor senderForTest() {
        return sender;
    }
}
