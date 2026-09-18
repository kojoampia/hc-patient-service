package net.jojoaddison.service.event;

import java.time.Instant;

/**
 * "Something about your membership changed — ask again."
 *
 * <p>The whole payload of the patient-facing event stream, and it is this small on purpose. Backlog item 39 decided
 * that the smallest useful push is a typed notice carrying the membership id and the new status, not a domain object:
 * the client already knows how to re-fetch, it has to re-fetch on reconnect anyway because the stream does not replay,
 * and a second serialization of {@code Membership} would be a contract nobody meant to promise.</p>
 *
 * <p><strong>{@code patientId} is on the frame because the frame is fanned out, not addressed.</strong> Every instance
 * consumes every event and decides for itself which of its own connected browsers may see it — so the subject has to
 * travel with it. That decision is {@link net.jojoaddison.security.PatientScope.Visibility#allows}, and it is the
 * reason nothing else about the patient is here: an event is filtered by comparing identifiers, never by reading the
 * record.</p>
 *
 * <p><strong>Nothing clinical, for the reasons {@link PatientEventPublisher} sets out at length</strong> — and one more
 * that is specific to this topic. A frame here is delivered to a browser rather than to a consumer this estate
 * controls, so whatever it carries ends up in a client that item 39 has not written yet. A membership id and a status
 * are safe to hand to a session already entitled to read the membership; the record itself is not the stream's to
 * give.</p>
 *
 * @param eventId unique per frame, used as the SSE event id so a client can tell a redelivery from a new change.
 * @param type always {@link #TYPE} today; present so a second kind of push does not need a second topic.
 * @param occurredAt when this service persisted the change.
 * @param patientId whose membership it is — the only routing information the fan-out has.
 * @param membershipId the membership that changed.
 * @param status its status as persisted, by name rather than as an enum so the wire shape does not move if the enum's
 *     serialization ever does. Null is possible: {@code Membership.status} carries no {@code @NotNull}.
 */
public record MembershipChangedEvent(
    String eventId,
    String type,
    Instant occurredAt,
    String patientId,
    String membershipId,
    String status
) {
    /**
     * The one event type this topic carries.
     *
     * <p>Unlike {@link PatientEventType#PLAN_CHOSEN} and {@code PlanVerificationConsumer.PLAN_VERIFIED} this is
     * <em>not</em> a cross-product contract — both ends are in this repository, and the only other reader is a client
     * in {@code web/} and {@code mobile/} that cycles 2 and 3 will write. It is still a literal on the wire, so a
     * rename is a change to the SSE {@code event:} line that those clients switch on.</p>
     */
    public static final String TYPE = "MembershipChanged";
}
