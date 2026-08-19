package net.jojoaddison.service.event;

import java.time.Instant;
import java.util.Map;

/**
 * One thing that happened to one patient, on the shared {@code patient-events} stream.
 *
 * <h2>Why every event shares this envelope</h2>
 *
 * <p>Account creation comes from the gateway, onboarding and delegation from this service, and they all land on one
 * topic. A consumer can therefore route on {@link #type} without knowing every payload in advance, and idempotency and
 * correlation work the same way for all of them rather than being decided per event.</p>
 *
 * <h2>How an event trails back to a patient</h2>
 *
 * <p>On {@code email}, lowercased, from the first event to the last — because <strong>there is no patient when the
 * journey starts.</strong> A {@code patientId} does not exist until onboarding step 1 creates the profile, and the two
 * account events happen before that, emitted by a service that has no notion of a patient at all. Email is also the
 * identifier the subsystem already runs on: the gateway puts it in the JWT precisely because it is the only thing the
 * two services share, and {@code PatientScope} resolves a caller by it. Using anything else here would introduce a
 * second notion of identity beside the one the security model depends on.</p>
 *
 * <p>{@code OnboardingStarted} is the event that binds the two together — the first to carry both the email a consumer
 * has been seeing since registration and the {@code patientId} everything afterwards is keyed by, and the only place
 * that mapping is published.</p>
 *
 * @param eventId unique per emission, and what a consumer keys on. Delivery is at least once, so duplicates are
 *                normal rather than exceptional.
 * @param type see {@link PatientEventType}.
 * @param version the envelope's schema version, not the payload's.
 * @param source which service emitted it.
 * @param subject who it is about.
 * @param data the per-type payload, and <strong>never anything clinical</strong> — see {@link PatientEventPublisher}.
 */
public record PatientEvent(
    String eventId,
    String type,
    int version,
    Instant occurredAt,
    String source,
    Subject subject,
    Map<String, Object> data
) {
    /** The current envelope version. Bump only for a change a consumer cannot ignore. */
    public static final int VERSION = 1;

    /**
     * Who the event is about.
     *
     * @param email lowercased; the correlation key and the partition key.
     * @param login the gateway login, when known.
     * @param patientId null until onboarding step 1 mints it.
     */
    public record Subject(String email, String login, String patientId) {}
}
