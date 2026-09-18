package net.jojoaddison.service.event;

import java.time.Instant;
import java.util.UUID;
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
 */
@Component
public class MembershipStreamPublisher {

    /** The binding name; {@code application.yml} maps it to the {@code patient-membership-events} destination. */
    public static final String BINDING = "membershipEvents-out-0";

    /** The header the Kafka binder reads to choose a partition key, via {@code messageKeyExpression}. */
    public static final String KEY_HEADER = "patientKey";

    private final Logger log = LoggerFactory.getLogger(MembershipStreamPublisher.class);

    private final StreamBridge streamBridge;

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

        try {
            // The boolean is worth reading: StreamBridge answers false for a binding it could not resolve rather than
            // throwing, which is a mis-wired producer failing quietly.
            boolean sent = streamBridge.send(BINDING, MessageBuilder.withPayload(event).setHeader(KEY_HEADER, patientId).build());
            if (!sent) {
                log.warn("Pushing membership {} was refused by the binder — check the {} binding", membership.getId(), BINDING);
            }
        } catch (Exception e) {
            // Deliberately swallowed. The membership is written; this is a notification, never the mechanism.
            log.warn("Could not push membership {} — the record is unaffected", membership.getId(), e);
        }
    }
}
