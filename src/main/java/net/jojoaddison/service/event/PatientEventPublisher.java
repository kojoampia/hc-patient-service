package net.jojoaddison.service.event;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * Publishes the patient journey to {@code patient-events}.
 *
 * <h2>Two rules, and neither is negotiable</h2>
 *
 * <p><strong>Publishing never fails the operation.</strong> By the time anything is published the write has already
 * happened — the profile exists, the step is recorded, the delegation has changed. Losing an event costs
 * observability; failing the request because the broker was unreachable would cost the patient their onboarding. There
 * is no outbox to make it exactly-once either: Mongo runs standalone here with no replica set, so there is no
 * transaction to hook one onto, and best-effort after a successful write is the honest design. Every failure is caught
 * and logged. <em>Do not "fix" the swallowed exception by rethrowing it.</em></p>
 *
 * <p><strong>An event with no subject key is refused, not sent.</strong> Every consumer of this stream keys on the
 * lower-cased email — hc-admin's {@code SiblingEventParser} drops a frame without one <em>before</em> it reads
 * {@code subject.patientId}, and acks it with no dead-letter record; this product's own
 * {@code CareDelegationMailer} in the gateway reads {@code subject().email()} and silently declines to send. So an
 * unkeyed frame is not a partial event that a consumer might still salvage, it is one nobody can attribute and
 * everybody discards. Publishing it would put an unreadable record on a retained, replayed topic and move the
 * diagnosis into somebody else's log; refusing leaves it here, next to the code that could not name the patient.
 * <b>Blank counts as absent</b> — {@code ""} is what a profile with an empty email field yields, and the consumers
 * treat blank and missing identically, so this must too.</p>
 *
 * <p>This is enforced here rather than at each call site on purpose. It was written first in
 * {@code MembershipResource} and immediately missed by {@code CareDelegationService}, whose unkeyed frame costs a
 * patient the mail telling them their care angel has stepped down. A rule about the envelope belongs to the envelope.
 * Note the one call site this must not break: {@code DeletionRequestService} publishes {@code COMPLETED} after the
 * profile is erased and reads the email off the stored request precisely because the lookup would fail — that path
 * still supplies a key, so it still publishes.</p>
 *
 * <p><strong>No event carries clinical content.</strong> Not a blood group, not an allergy, not a medication, not an
 * ID number, not an address. {@code OnboardingStepCompleted} says step 4 completed; it does not say what step 4 said.
 * A topic is retained, replicated, replayed into whatever consumer is written next and read by people debugging
 * something unrelated — it is the least controlled copy of any data that enters it, and the hardest to delete from.
 * The record itself is protected by {@code PatientScope}, which fails closed and refuses cross-patient reads; a stream
 * carrying the same facts would be that protection routed around. {@link #assertNothingClinical} enforces it at
 * runtime, and there is a test that fails if a clinical key is ever added.</p>
 */
@Component
public class PatientEventPublisher {

    /** The binding name; {@code application.yml} maps it to the {@code patient-events} destination. */
    public static final String BINDING = "patientEvents-out-0";

    /**
     * The header the Kafka binder reads to choose a partition key, via {@code messageKeyExpression}.
     *
     * <p>Every event about one person must land on one partition, or "what happened to this patient, in what order"
     * stops being answerable — which is the only question the stream is really for.</p>
     */
    public static final String KEY_HEADER = "patientKey";

    private static final String SOURCE = "hcPatientService";

    /**
     * Payload keys that would make an event carry the record rather than describe it.
     *
     * <p>A denylist rather than an allowlist on purpose: an allowlist silently drops a new field, where this refuses
     * loudly at the point somebody adds one, which is when the decision is actually being made.</p>
     */
    private static final Set<String> CLINICAL_KEYS = Set.of(
        "bloodgroup",
        "allergy",
        "allergies",
        "medication",
        "medications",
        "condition",
        "conditions",
        "cardnumber",
        "cardtype",
        "address",
        "diagnosis",
        "symptoms",
        "height",
        "weight",
        "systolic",
        "diastolic",
        "heartrate",
        "bloodsugar",
        "value",
        "reading",
        "readings",
        "note",
        "notes"
    );

    private final Logger log = LoggerFactory.getLogger(PatientEventPublisher.class);

    private final StreamBridge streamBridge;

    public PatientEventPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    /**
     * Builds the envelope and publishes it, swallowing anything that goes wrong.
     *
     * @param type see {@link PatientEventType}.
     * @param email the correlation key. Lowercased here so every producer agrees without having to remember to.
     * @param login the gateway login, when known.
     * @param patientId null before onboarding step 1.
     * @param data the payload; must contain nothing clinical.
     */
    public void publish(String type, String email, String login, String patientId, Map<String, Object> data) {
        Map<String, Object> payload = data == null ? Map.of() : new HashMap<>(data);
        assertNothingClinical(type, payload);

        String key = email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
        if (key == null) {
            // Refused rather than sent. See the class javadoc: an unkeyed frame is not a partial event, it is one
            // every consumer drops, and it is the one shape this stream cannot carry.
            log.warn("Not publishing {} — no subject key, so no consumer can attribute it (patientId {})", type, patientId);
            return;
        }
        PatientEvent event = new PatientEvent(
            UUID.randomUUID().toString(),
            type,
            PatientEvent.VERSION,
            Instant.now(),
            SOURCE,
            new PatientEvent.Subject(key, login, patientId),
            payload
        );

        try {
            // The boolean is worth reading: StreamBridge answers false for a binding it could not resolve rather
            // than throwing, which is a mis-wired producer failing quietly.
            boolean sent = streamBridge.send(
                BINDING,
                // No null branch: the guard above returned, so the key is non-blank by here. It used to be
                // `key == null ? "" : key`, and the empty string was the trap — StringSerializer turns "" into a
                // zero-length array rather than a null key, so Kafka's partitioner takes the keyed branch and every
                // unkeyed frame in the estate hashed to one partition instead of being spread.
                MessageBuilder.withPayload(event).setHeader(KEY_HEADER, key).build()
            );
            if (!sent) {
                log.warn("Publishing {} was refused by the binder — check the {} binding", type, BINDING);
            }
        } catch (Exception e) {
            // Deliberately swallowed. See the class javadoc: the write already happened, and the event is a
            // notification rather than the mechanism.
            log.warn("Could not publish {} — the record is unaffected", type, e);
        }
    }

    /**
     * Refuses a payload that would put the record on the wire.
     *
     * <p>Throws rather than stripping the offending key: quietly dropping it would let the caller believe the field is
     * being published, and the next person to read the consumer would wonder why it never arrives.</p>
     */
    static void assertNothingClinical(String type, Map<String, Object> data) {
        for (String key : data.keySet()) {
            if (CLINICAL_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException(
                    "Event " +
                    type +
                    " would carry clinical content in '" +
                    key +
                    "'. Events say that a thing happened, never what it said — see PatientEventPublisher."
                );
            }
        }
    }
}
