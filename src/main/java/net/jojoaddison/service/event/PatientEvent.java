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
 * journey starts.</strong> A profile does not exist until onboarding step 1 creates it, and the two account events
 * happen before that, emitted by a service that has no notion of a patient at all. Email is also the identifier the
 * subsystem already runs on: the gateway puts it in the JWT precisely because it is the only thing the two services
 * share, and {@code PatientScope} resolves a caller by it. Using anything else here would introduce a second notion
 * of identity beside the one the security model depends on.</p>
 *
 * <p><strong>The subject's third component is {@code accountId}, the gateway {@code User.id} — the same field, with
 * the same meaning, that the gateway's own {@code PatientEvent} carries.</strong> It was {@code patientId} — this
 * service's internal profile key — until 2026-09-24, when the architect settled the divergence the other way: two
 * publishers on one topic under one envelope name were sending third components that differed in name <em>and</em>
 * meaning, so a consumer could get the estate join key ({@code account.id = profile.accountId}, the 2026-09-17
 * cross-product decision) from neither. {@code Profile.accountId} <em>is</em> that {@code User.id} —
 * {@code OnboardingService.resolveAccountId()} asks the gateway for the caller's account id and stores it, a unique
 * partial index holds one profile per account, and change unit 004 backfills — so a consumer reading
 * {@code accountId} off either publisher's frame now gets the same identifier for the same person. The internal
 * {@code patientId} no longer travels on this stream at all; it never left this subsystem's own vocabulary.</p>
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
     * @param accountId the gateway {@code User.id}, when this service holds it — the estate join key, and the same
     *     field the gateway's own publisher sends. Null is a legitimate frame and means "this service cannot name
     *     the account": the JWT carried none, the account already owned another profile, or the link was lost to the
     *     race {@code saveUnlinkingIfTheAccountWasTakenMeanwhile} resolves. Never fabricated from anything else.
     */
    public record Subject(String email, String login, String accountId) {}
}
