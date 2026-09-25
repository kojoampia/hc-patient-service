package net.jojoaddison.service.event;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.jojoaddison.domain.CareDelegation;
import net.jojoaddison.domain.DeletionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * Publishes every entity change in this subsystem to {@code patient.event}, and the two commands that channel carries.
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
 * <h2>⭐ Item 46, first half: this channel also carries two COMMANDS, and they are a second family</h2>
 *
 * <p>{@code patient-events} cannot be retired while the gateway's {@code PatientEventMailRouter} is reading it, and
 * that router cannot work from an {@code EntityChanged} frame — it needs {@code change}, the patient's address, the
 * angel's address and {@code dueAt}, none of which a two-key allowlist may carry. So
 * {@link #publishCareDelegationChanged} and {@link #publishDeletionRequestChanged} put the same two frames on
 * {@code patient.event} as well, under their own {@code type}s, carrying the values they decide. The architect's ruling
 * of 2026-09-25, and the shape is argued in {@link EntityEvent}.</p>
 *
 * <p>⛔ <strong>Nothing is removed from {@code patient-events} by this.</strong> Both call sites publish to both
 * topics; hc-admin still consumes the old one and the mail router still reads it. Rebinding the router is the second
 * half and lives in the gateway. A producer writing where nobody reads is harmless — <em>this topic has no consumer
 * group at all yet</em>, verified on the quality broker on 2026-09-25 — where a consumer reading where nobody writes
 * is silence that looks like health.</p>
 *
 * <h2>Two queues, one topic — and the queue is the coupling</h2>
 *
 * <p>The command family gets its own {@link AsyncEventSender} rather than sharing the notification family's 512 slots,
 * for the reason that class's javadoc states in as many words: share one queue and the noisiest stream decides what
 * the quietest loses. The notification family fires on <em>every write in the service</em>; the command family is the
 * handful of lifecycle moments that oblige a letter to a patient. Behind one queue, a burst of ordinary saves against a
 * slow broker would drop somebody's "your care angel has stepped down" — and after the gateway half of item 46 that is
 * a mail nobody sends, because the frame on the old topic will be gone. Two queues cost one daemon thread and make each
 * family's loss its own load. They cost no ordering either: the two families are keyed differently, so they were never
 * on the same partition to begin with.</p>
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
     * <p>For a <strong>notification</strong> it is the <strong>entity id</strong>, not the actor and not a patient. Two
     * changes to one document must stay in order or an audit trail reports them in whichever order two partitions
     * happened to be drained, which for a create-then-delete pair is a trail saying the document still exists. Keying
     * on the actor would order one person's actions and scatter each document's history, which is the wrong half of the
     * guarantee for this stream — note it is the opposite choice from {@code patient-events}, and for the opposite
     * reason.</p>
     *
     * <h2>⭐ For a COMMAND it is the patient, and that is a decision rather than an oversight</h2>
     *
     * <p>Two frames on one channel keyed two different ways is a choice and hc-admin's item 145 faces the identical one
     * on their return leg, so the argument is written out rather than implied.</p>
     *
     * <p><strong>What keying on the patient buys: per-patient ordering, which is what the consumer needs and what it
     * has today.</strong> The only consumer of these two types is a mail dispatcher, and the failure that matters to it
     * is a later change overtaking an earlier one for the same person: an angel told their access has ended before they
     * are told they were nominated, or a patient told their record is gone before being told it was going.
     * {@code patient-events} gives it that ordering by keying on the lowercased email, and
     * {@link PatientEventType#DELETION_REQUEST_CHANGED}'s javadoc names it as the reason those transitions share one
     * type. <strong>Keying a command on the entity id would silently take it away</strong> — and the gateway half of
     * item 46 would then be a rebind that loses a guarantee while every test passed, which is exactly the class of
     * defect this subsystem keeps finding.</p>
     *
     * <p><strong>What it gives up: co-partitioning with the same document's own notification frame.</strong> A
     * revocation puts two frames on this topic — {@code EntityChanged} about the {@code CareDelegation}, keyed on the
     * delegation, and {@code CareDelegationChanged}, keyed on the patient — and nothing orders those two against each
     * other. Nobody reads both: the audit consumer dispatches on {@code type} and ignores commands, the mail router
     * ignores notifications. <strong>Per-entity ordering is not lost, it is subsumed:</strong> a delegation and a
     * deletion request each belong to exactly one patient, so every frame about one document lands on that patient's
     * partition and stays ordered there too.</p>
     *
     * <p>⚠ <strong>One header name for both, deliberately, and the name is now half-honest.</strong> The binding's
     * {@code messageKeyExpression} reads exactly this header, so a second name would mean editing that expression —
     * the one line governing the key of every frame on a live cross-product channel, whose failure mode is a
     * {@code ClassCastException} at send time inside a publisher that swallows, on a thread of its own. And no test in
     * this repository can protect it: the property is read from {@code src/test/resources/config/application.yml},
     * which <em>replaces</em> the main file rather than merging with it, so a correct test config and a wrong main one
     * are green. A honest header name is not worth that trade. This change therefore touches no YAML at all.</p>
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

    /**
     * The command family's own queue, sized like {@code patient-events}' because it carries the same traffic.
     *
     * <p>128 rather than 512: these are the handful of lifecycle moments that oblige a letter, not every write in the
     * service, so this absorbs hundreds of concurrent writers while bounding how much stale mail a recovering broker
     * would send. See the class javadoc on why it is a second queue rather than a share of the first.</p>
     */
    private static final int COMMAND_QUEUE_CAPACITY = 128;

    private final Logger log = LoggerFactory.getLogger(EntityEventPublisher.class);

    private final StreamBridge streamBridge;

    private final ThreadPoolExecutor sender;

    /** The command family's sender — see {@link #COMMAND_QUEUE_CAPACITY} and the class javadoc. */
    private final AsyncEventSender commandSender;

    /**
     * Incremented in the {@code RejectedExecutionException} catch below. This class keeps its inline executor (item
     * 71 deliberately left it untouched), so unlike the two {@code AsyncEventSender} publishers the count here is
     * NOT constructor-enforced — this is the one hand-remembered site, and {@code EntityEventPublisherTest} watches
     * it move so forgetting it cannot stay green.
     */
    private final Counter droppedFrames;

    /**
     * The command family's own drop counter — same meter, same {@code topic}, {@code family=command}.
     *
     * <p>Constructor-enforced by {@link AsyncEventSender}, unlike {@link #droppedFrames} above, which is the one
     * hand-remembered site in this class.</p>
     */
    private final Counter droppedCommands;

    public EntityEventPublisher(StreamBridge streamBridge, MeterRegistry meterRegistry) {
        this.streamBridge = streamBridge;
        this.droppedFrames = DroppedEventCounter.register(meterRegistry, "patient.event", DroppedEventCounter.NOTIFICATION);
        // A SECOND COUNTER, NOT A SHARE OF THE FIRST, and it is the instrumentation half of the two-queue argument.
        // Both tag `topic=patient.event`, so summing still answers "did this topic lose anything"; they differ on
        // `family`, so the question the two queues exist to answer — did a burst of ordinary saves cost somebody a
        // letter — is a graph rather than a substring of a WARN. One shared counter made the two indistinguishable
        // after the fact, which is item 73's own argument applied one level finer.
        this.droppedCommands = DroppedEventCounter.register(meterRegistry, "patient.event", DroppedEventCounter.COMMAND);
        this.commandSender = new AsyncEventSender("patient-command-publisher", COMMAND_QUEUE_CAPACITY, droppedCommands::increment);
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

    /**
     * A care delegation moved — the frame the gateway turns into mail for the patient and their angel.
     *
     * <p>Backlog item 46. The twin of what {@code CareDelegationService} already publishes on {@code patient-events},
     * in this channel's envelope: the delegation is the subject, and the two addresses and the transition are the
     * values the command decides. <strong>The old frame is still published too</strong> — this is an add.</p>
     *
     * @param delegationId the delegation's own id, which becomes {@code subject.entityId}. Refused when absent.
     * @param change the transition — {@code REVOKED_BY_ANGEL}, {@code REVOKED_BY_PATIENT}, {@code STANDBY_ACTIVATED}.
     *     A cross-repo contract: the gateway's {@code CareDelegationMailer} switches on these literals.
     * @param patientEmail the patient's address. Lowercased here, and the partition key — see {@link #KEY_HEADER}. A
     *     frame without one is refused rather than sent, exactly as on {@code patient-events}: the mailer declines to
     *     send to a blank address, so an unkeyed frame is one nobody can act on.
     * @param angelEmail the nominated angel's address, as stored.
     */
    public void publishCareDelegationChanged(String delegationId, String change, String patientEmail, String angelEmail) {
        String key = subjectKey(patientEmail);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(EntityEvent.CHANGE, change);
        data.put(EntityEvent.PATIENT_EMAIL, key);
        data.put(EntityEvent.ANGEL_EMAIL, angelEmail);
        publishCommand(PatientEventType.CARE_DELEGATION_CHANGED, CareDelegation.class.getSimpleName(), delegationId, key, data);
    }

    /**
     * A deletion request moved — the frame the gateway turns into mail, and into closing the account.
     *
     * <p>Backlog item 46, and the twin of {@code DeletionRequestService}'s existing {@code patient-events} frame.
     * ⚠ <strong>{@code COMPLETED} is the one transition whose subject no longer exists:</strong> the erasure has already
     * taken the {@code Profile}, so the address is the copy stored on the request at {@code raise} and a consumer must
     * not try to resolve the patient from it. It is also the one frame a consumer must <em>act</em> on rather than
     * record.</p>
     *
     * @param requestId the request's own id, which becomes {@code subject.entityId}. Refused when absent.
     * @param change the transition — {@code RAISED}, {@code CANCELLED}, {@code COMPLETED}, {@code REJECTED}. A
     *     cross-repo contract: {@code DeletionRequestMailer} and {@code DeletionAccountCloser} switch on these.
     * @param patientEmail {@code requestedByEmail}, read off the request and never looked up. Lowercased here, and the
     *     partition key.
     * @param dueAt when the erasure is owed by, or null — omitted from the payload rather than sent as null.
     */
    public void publishDeletionRequestChanged(String requestId, String change, String patientEmail, Instant dueAt) {
        String key = subjectKey(patientEmail);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(EntityEvent.CHANGE, change);
        data.put(EntityEvent.PATIENT_EMAIL, key);
        if (dueAt != null) {
            data.put(EntityEvent.DUE_AT, dueAt.toString());
        }
        publishCommand(PatientEventType.DELETION_REQUEST_CHANGED, DeletionRequest.class.getSimpleName(), requestId, key, data);
    }

    /**
     * Builds a command envelope and hands it to the command sender.
     *
     * <p><strong>Package-private, so the two methods above are the only way in from outside this package.</strong> Each
     * of them builds its payload from named parameters, so no <em>caller outside</em> {@code service.event} can choose a
     * key or a type.</p>
     *
     * <p>⚠ <strong>Be precise about what is closed, because the two axes are not equally closed.</strong> The
     * <em>type</em> is closed twice: {@link #assertIsAKnownCommand} checks it at runtime against
     * {@link EntityEvent#COMMAND_TYPES}, and there are exactly two typed methods. The <em>keys</em> are closed only by
     * those two methods' fixed construction — this method takes a {@code Map} from anywhere in this package, and the
     * only check it runs over it is {@link PatientEventPublisher#assertNothingClinical}, a <strong>23</strong>-entry
     * exact-lowercase denylist that refuses {@code address} and would pass {@code homeAddress}, {@code dosage},
     * {@code nhsNumber} or {@code gpsLocation} without a murmur — all five measured off the set rather than read off it. <strong>That is the design and not a gap</strong> — a command carries the values it decides, so a key
     * allowlist would refuse the whole family — but "no call site can invent a key" would be a stronger claim than the
     * code makes, and this javadoc made it until item 46's review. A new command type is therefore a decision about
     * what goes on the wire, reviewed as one, rather than something a guard will catch.</p>
     *
     * <p>What this method adds beyond the two public shapes is the runtime type check, because a third typed method
     * added here without registering its type would otherwise publish a value-carrying frame under a type no consumer
     * knows — which is the hole the notification allowlist exists to prevent, reopened from the other side.</p>
     *
     * @param type must be one of {@link EntityEvent#COMMAND_TYPES}; anything else throws.
     * @param entityType the domain class whose transition this is.
     * @param entityId that document's id. A frame that names nothing is refused.
     * @param subjectKey the already-normalised partition key — the patient. Refused when blank.
     * @param data the values this command decides; must carry {@link EntityEvent#CHANGE} and nothing clinical.
     */
    void publishCommand(String type, String entityType, String entityId, String subjectKey, Map<String, Object> data) {
        assertIsAKnownCommand(type);
        if (entityType == null || entityType.isBlank() || entityId == null || entityId.isBlank()) {
            // Refused rather than sent, for the reason a notification naming nothing is: the frame is about a record,
            // and one that names none is a frame whose diagnosis lands in somebody else's log.
            log.warn("Not publishing {} — it names no record", type);
            return;
        }
        if (subjectKey == null || subjectKey.isBlank()) {
            // The rule PatientEventPublisher states for the stream next door, applied here because the same consumer
            // needs the same thing: blank counts as absent, and the mailer declines to write to a blank address.
            log.warn("Not publishing {} for {} — no subject key, so no consumer can attribute it", type, entityType);
            return;
        }
        Object change = data.get(EntityEvent.CHANGE);
        if (change == null || String.valueOf(change).isBlank()) {
            // A command that does not say what it decided is one the mailer's switch falls through, silently.
            log.warn("Not publishing {} for {} — it says no change", type, entityType);
            return;
        }
        // The allowlist deliberately does NOT run here — a command carries values, which is the whole point, and
        // assertIdentifiersOnly would refuse every one of them. assertNothingClinical still does: the two guards fail
        // differently and neither subsumes the other, and a clinical key is as wrong on a command as on a notification.
        PatientEventPublisher.assertNothingClinical(type, data);

        EntityEvent event = new EntityEvent(
            UUID.randomUUID().toString(),
            type,
            EntityEvent.VERSION,
            Instant.now(),
            SOURCE,
            new EntityEvent.Subject(entityType, entityId),
            data
        );

        // Built on the calling thread, sent on the command family's own. Only the send is deferred.
        if (!commandSender.offer(() -> send(event, subjectKey))) {
            // Counted inside offer() under family=command; named here, and named as what it is rather than as its
            // neighbour. This line used to open "Dropped an entity change for", identical to the notification path
            // above and differing only by a word in the trailing clause — so the one loss on this topic that costs a
            // patient something read exactly like the one that costs an audit row.
            //
            // ⚠ The `quality` repo's startup.sh greps its logs for the literal "Dropped an entity change" as one of
            // three silent-failure strings. It will NOT match this line, deliberately: the fix is a fourth string
            // there rather than a misleading message here, and the counter above is the instrument that does not
            // depend on anybody having chosen the right substring.
            log.warn(
                "Dropped a patient command on patient.event — {} for {} — the command publishing queue is full, so the letter it obliges will not be sent",
                type,
                entityType
            );
        }
    }

    /**
     * ⛔ Refuses a type that is not a registered command.
     *
     * <p>The command family's guard, on the axis its payload cannot be guarded on. {@code ALLOWED_KEYS} closes a
     * notification by its <em>keys</em>; nothing can close a command that way, because values are what it is for. So
     * what is closed is the set of types allowed to carry values at all — without it, "publish a command" would be a
     * general-purpose way round the allowlist and the next field somebody wants would arrive through it.</p>
     *
     * <p>Throws, and on the calling thread, for {@link #assertIdentifiersOnly}'s reason: this is a programming error
     * rather than a runtime condition, so it must surface in the test that introduces it rather than as a log line on a
     * background thread.</p>
     */
    static void assertIsAKnownCommand(String type) {
        if (!EntityEvent.COMMAND_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                "'" +
                type +
                "' is not a command this channel carries. A command carries the value it decides, so the set of types " +
                "allowed to do that is closed — see EntityEvent.COMMAND_TYPES."
            );
        }
    }

    /** The partition key for a command: the patient's address, lowercased once here so no call site has to remember. */
    private static String subjectKey(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
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
        // Drained after, not instead: two queues mean two chances to abandon frames, and the command queue's are the
        // ones that would have become letters.
        if (!commandSender.drain(Duration.ofSeconds(2))) {
            log.warn("Shutting down with patient commands still queued — they are lost, and the records are unaffected");
        }
    }

    /** For the test that pins the rejection policy. The defect it guards against is a one-word edit. */
    ThreadPoolExecutor senderForTest() {
        return sender;
    }

    /** As above, for the command family's own queue — the same one-word edit, in a second place since item 46. */
    ThreadPoolExecutor commandSenderForTest() {
        return commandSender.executorForTest();
    }
}
