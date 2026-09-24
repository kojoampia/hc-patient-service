package net.jojoaddison.service.event;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;
import net.jojoaddison.domain.Membership;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * Puts a membership change on {@code patient-membership-events}, where every running instance picks it up and pushes
 * it to whichever of its own connected browsers is entitled to see it.
 *
 * <h2>Why a broker sits in the middle of a push to a browser</h2>
 *
 * <p><strong>Because the instance that handles the change is not the instance the patient is connected to.</strong> An
 * {@code SseEmitter} is a live socket held by one JVM; the write that moves a membership to {@code ACTIVE} arrives from
 * hc-admin over Kafka and is handled by whichever instance owns that partition. Without a fan-out hop the patient sees
 * their activation only if the coin lands right. Publishing here and consuming in
 * {@link MembershipStreamConsumer} means an event handled by instance A still reaches a client connected to
 * instance B.</p>
 *
 * <p><strong>Production runs a single api container today, so this is not load-bearing yet — which is exactly why it
 * must not be "simplified" into an in-process event bus.</strong> That is backlog item 39 quoting the one thing the
 * generated scaffold this replaces got right. An in-process bus would work on the current deployment, pass every test,
 * and break silently the first time a {@code replicas:} line is added to a compose file: half the patients would stop
 * being told, and nothing would fail.</p>
 *
 * <h2>The rules it inherits, and the one it does not</h2>
 *
 * <p><strong>Publishing never fails the operation.</strong> Same rule and same reason as {@link PatientEventPublisher}:
 * by the time this runs the membership is already written, and a patient told their choice failed because a broker was
 * unreachable is a worse outcome than a patient who has to pull to refresh. Every failure is caught and logged.</p>
 *
 * <p><strong>A frame nobody can be told about is not published.</strong> {@code patientId} is the only routing
 * information the fan-out has — {@link net.jojoaddison.security.PatientScope.Visibility#allows} compares against it —
 * so a membership with no owner has nobody to notify. Such memberships exist: {@code requirePatientIdForWrite} lets an
 * unrestricted caller create one deliberately. Blank counts as absent, the same reading every other producer here
 * applies.</p>
 *
 * <p><strong>What it does NOT inherit is the email key.</strong> {@link PatientEventPublisher} keys
 * {@code patient-events} on the patient's lower-cased email because its consumers are other products that know people
 * by address. Both ends of this topic are in this repository and both already hold {@code patientId}, so keying on it
 * costs no profile lookup on a path that runs inside a Kafka handler, and cannot file an event under an administrator
 * who acted for somebody. The header name is the same — {@code patientKey} — because
 * {@code messageKeyExpression} is configured per binding and reusing the name keeps one spelling in the YAML; the
 * <em>value</em> differs, and a reader comparing the two bindings should expect that.</p>
 *
 * <h2>The send is asynchronous; the no-owner refusal is not</h2>
 *
 * <p>Backlog item 71, the architect's decision of 2026-09-24: a <em>hung</em> broker — accepting the connection and
 * not answering — blocks a send for {@code max.block.ms}, unset in this estate and so Kafka's sixty-second default.
 * This publisher runs inside {@code MembershipResource}'s request as well as inside Kafka handlers, so that minute
 * would land on the patient choosing a plan, past nginx's timeout, after their membership was written. The send
 * therefore runs on {@link AsyncEventSender}'s thread; capping {@code max.block.ms} instead was considered and
 * explicitly declined. The no-owner refusal above stays on the calling thread — it decides whether there is anything
 * to queue at all.</p>
 *
 * <p><strong>Its executor is its own</strong> — not {@link EntityEventPublisher}'s, whose stream fires on every
 * write in the service, and not {@link PatientEventPublisher}'s either, though both are quiet: this is the one stream
 * a patient is actively watching a screen for, and its loss budget should not be a function of anybody else's burst.
 * One daemon thread is the whole cost. {@link AsyncEventSender}'s javadoc carries the full argument.</p>
 */
@Component
public class MembershipStreamPublisher {

    /** The binding name; {@code application.yml} maps it to the {@code patient-membership-events} destination. */
    public static final String BINDING = "membershipEvents-out-0";

    /** The header the Kafka binder reads to choose a partition key, via {@code messageKeyExpression}. */
    public static final String KEY_HEADER = "patientKey";

    /** A membership change is one frame; 128 absorbs a burst of them and bounds staleness. See {@link AsyncEventSender}. */
    private static final int QUEUE_CAPACITY = 128;

    private final Logger log = LoggerFactory.getLogger(MembershipStreamPublisher.class);

    private final StreamBridge streamBridge;

    /** Own instance, own queue — see the class javadoc for why it is not shared. */
    private final AsyncEventSender sender = new AsyncEventSender("membership-stream-publisher", QUEUE_CAPACITY);

    public MembershipStreamPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    /**
     * Announces a membership to its own patient's open streams.
     *
     * @param membership the membership as persisted — the status is read off it, so this reports what was written
     *     rather than what was asked for, exactly as the hc-admin announcement beside it does.
     */
    public void publish(Membership membership) {
        String patientId = membership.getPatientId();
        if (patientId == null || patientId.isBlank()) {
            // Nobody to tell. Not a failure: an administrator may create a membership owned by no patient, and the
            // stream has no way to address one.
            log.debug("Not pushing membership {} — it belongs to no patient", membership.getId());
            return;
        }

        MembershipChangedEvent event = new MembershipChangedEvent(
            UUID.randomUUID().toString(),
            MembershipChangedEvent.TYPE,
            Instant.now(),
            patientId,
            membership.getId(),
            membership.getStatus() == null ? null : membership.getStatus().name()
        );

        // Built on the calling thread and sent on the other one. Only the send is deferred.
        if (!sender.offer(() -> send(event, membership.getId(), patientId))) {
            // The queue is full, which means the broker is not draining it. Dropping is the design: a browser that
            // misses a frame re-fetches on its next connect, where a stalled request is a patient told their
            // choice failed.
            log.warn("Dropped a membership push for {} — the publishing queue is full", membership.getId());
        }
    }

    private void send(MembershipChangedEvent event, String membershipId, String patientId) {
        try {
            // The boolean is worth reading: StreamBridge answers false for a binding it could not resolve rather than
            // throwing, which is a mis-wired producer failing quietly.
            boolean sent = streamBridge.send(BINDING, MessageBuilder.withPayload(event).setHeader(KEY_HEADER, patientId).build());
            if (!sent) {
                log.warn("Pushing membership {} was refused by the binder — check the {} binding", membershipId, BINDING);
            }
        } catch (Exception e) {
            // Deliberately swallowed, and on a thread of its own since item 71 — it cannot reach the caller, and the
            // catch keeps the sender thread alive for the next frame. The membership is written either way.
            log.warn("Could not push membership {} — the record is unaffected", membershipId, e);
        }
    }

    /** Gives queued frames a bounded chance to go out on shutdown; see {@link AsyncEventSender#drain}. */
    @PreDestroy
    void drain() {
        if (!sender.drain(Duration.ofSeconds(2))) {
            log.warn("Shutting down with membership pushes still queued — they are lost, and the records are unaffected");
        }
    }

    /** For the test that pins the rejection policy. The defect it guards against is a one-word edit. */
    ThreadPoolExecutor senderForTest() {
        return sender.executorForTest();
    }
}
