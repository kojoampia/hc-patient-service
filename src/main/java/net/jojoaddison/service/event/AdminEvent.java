package net.jojoaddison.service.event;

import java.time.Instant;
import java.util.Map;

/**
 * One frame on {@code admin.event} — hc-admin's channel, which this service reads and does not own.
 *
 * <h2>Why this is not {@link PatientEvent} with a different destination</h2>
 *
 * <p>Because {@code subject} means something else here, and reusing the record would make one field name mean two
 * things depending on {@code type} — the divergence the estate's one-channel-per-product decision of 2026-09-17 exists
 * to end. On {@code patient-events} the subject is a <em>person</em> ({@code email}, {@code login}, {@code accountId});
 * on this channel it is the <em>record the frame is about</em> ({@code entityType}, {@code entityId}), which is the
 * shape all four products agreed on. hc-admin took the same decision from the other side and for the same reason: their
 * {@code PlanVerifiedEvent} is a second class rather than their existing {@code PlanVerificationEvent} pointed at a new
 * destination, "architect's D1, 2026-09-25".</p>
 *
 * <h2>What is actually on the channel, read off the producer and off the broker</h2>
 *
 * <p>Three types today, all from {@code hcAdminService}, all keyed {@code <EntityType>/<entityId>} with <b>no
 * {@code patientKey} header</b> — their {@code AdminChannel.partitionKey} is the one definition of that key, and
 * {@code OutboundEventPublisher} states the no-header rule. Read from their {@code main} at {@code 7deda9a} ("Item 145
 * step 1: both return legs also publish on admin.event") on 2026-09-25, and confirmed by reading the live quality
 * broker the same day:</p>
 *
 * <pre>
 * EntityChanged         subject (entityType, entityId)               data { action, actorAccountId? }
 * PlanVerified          subject ("DirectoryLink", linkId)            data { plan, subjectKey }
 * ProfessionalVerified  subject ("ProfessionalVerification", id)     data { status, professionalId, actorAccountId? }
 * </pre>
 *
 * <p><b>Only {@code PlanVerified} is addressed to this service</b>, and it is a rounding error on the channel: of the
 * 7435 frames {@code admin.event} held on 2026-09-25, <b>7433 were {@code EntityChanged}</b>, one was
 * {@code PlanVerified} and one {@code ProfessionalVerified}. {@link PlanVerificationConsumer} therefore dispatches on
 * {@link #type} before it does anything else at all. Backlog item 47.</p>
 *
 * <h2>Every component is nullable and {@code version} is boxed, deliberately</h2>
 *
 * <p>This is another product's channel and this record is only a reader of it. A frame that fails to <em>convert</em>
 * is dead-lettered by the binder before this service's own rules ever see it, so a strict record would turn any future
 * frame shape into dead-letter noise on a channel where the overwhelming majority of frames are none of our business.
 * {@code version} in particular is an {@code Integer} rather than an {@code int} because Jackson refuses to bind an
 * absent property onto a primitive ({@code FAIL_ON_NULL_FOR_PRIMITIVES}) and reports it as a conversion failure — one
 * missing field on a frame about a wage rate would land in our dead-letter queue.</p>
 *
 * <h2>⚠ How tolerant this actually is — measured, because the paragraph above overstated it</h2>
 *
 * <p><b>Nullability covers a field that is <em>missing</em>. It says nothing about a field that is the wrong
 * shape</b> — and {@code data} is the most narrowly typed thing left here. That gap was invisible while every fixture
 * in the repository emitted exactly the seven declared fields, so the claim rested on Jackson defaults nothing pinned.
 * Measured over a real broker on 2026-09-25 by
 * {@code PlanVerificationRoundTripIT.anEntityChangeIsIgnoredWhileARealVerificationInTheSameRunIsApplied}, which now
 * publishes two frames differing in one thing:</p>
 *
 * <table>
 *   <caption>A type this repository has never heard of, carrying an envelope key the record does not declare</caption>
 *   <tr><th>{@code data}</th><th>outcome</th></tr>
 *   <tr><td>an object</td><td><b>ignored</b> — binds, dispatches on type, costs one string comparison</td></tr>
 *   <tr><td>an array</td><td><b>dead-lettered</b> after four delivery attempts</td></tr>
 * </table>
 *
 * <p>So an unknown type and an undeclared envelope key really are free; a {@code data} that is not a JSON object is
 * not. <b>That limit is accepted rather than fixed, and the reason is the shape of the risk.</b> Every frame on this
 * channel comes from one serialiser where {@code data} is a typed record, so a non-object {@code data} is a genuine
 * break rather than a variation — and it fails loudly, keeping its bytes, once per such frame. Widening this to
 * {@code Object} would buy tolerance for a case that has never occurred at the cost of a defensive cast in all three
 * places that read the payload.</p>
 *
 * <p>⚠ <b>It is reported as {@code ClassCastException: [B cannot be cast to AdminEvent}, which names nothing that is
 * wrong.</b> Spring's JSON converter <em>declines</em> the message rather than throwing, so the raw {@code byte[]} is
 * handed to the function and the cast fails on the way in. Read that exception as <em>this frame did not convert</em>
 * and look at the payload, not at the binding.</p>
 *
 * @param eventId unique per emission. hc-admin mints a <b>different</b> one for the frame it publishes on
 *                {@code patient-events-plan} about the same decision — "two channels, two identities" — so this is the
 *                idempotency key for what arrives <em>here</em> and nothing else.
 * @param type the discriminator, and the only field read before the frame is either kept or ignored.
 * @param version the envelope's schema version, and hc-admin's to move.
 * @param occurredAt when it happened, as they stamped it.
 * @param source which service emitted it — {@code hcAdminService} for everything on this channel today.
 * @param subject the record the frame is about, never the person and never the actor.
 * @param data the per-type payload. For {@code PlanVerified} it carries both the decided plan and the addressee; see
 *             {@link PlanVerificationConsumer}, which is emphatic about the second one.
 */
public record AdminEvent(
    String eventId,
    String type,
    Integer version,
    Instant occurredAt,
    String source,
    Subject subject,
    Map<String, Object> data
) {
    /**
     * The record a frame is about.
     *
     * @param entityType a domain class's simple name in hc-admin's model — {@code Patient}, {@code DirectoryLink},
     *     {@code WageRate} — explicitly not a collection name, so a consumer is not coupled to their storage layout.
     * @param entityId that record's id in their database, which means nothing in this one.
     */
    public record Subject(String entityType, String entityId) {}
}
