package net.jojoaddison.service.event;

import java.time.Instant;
import java.util.Map;

/**
 * One entity change in this subsystem, on {@code patient.event} — enough for hc-admin to write one audit row and
 * deliberately not one byte more.
 *
 * <h2>Why this is a second record beside {@link PatientEvent} rather than a widening of it</h2>
 *
 * <p>The two carry <strong>the same seven-component outer shape</strong> — {@code eventId}, {@code type},
 * {@code version}, {@code occurredAt}, {@code source}, {@code subject}, {@code data} — on purpose, because that is the
 * shape hc-admin's {@code SiblingEventParser} already knows how to read and a different one would cost them a second
 * reader. What differs is the subject, and it differs because the two streams are about different things.</p>
 *
 * <p>Adding a component to {@link PatientEvent.Subject} would have reused the class, and would also have changed the
 * bytes on {@code patient-events} — a live cross-product contract with a consumer in hc-admin and a mail router in
 * this subsystem's own gateway. Backlog item 45 is an <em>add</em>: a new topic beside the old one, with nothing
 * removed and nothing on the existing wire disturbed. A second record is the cost of that, and it is the cheaper
 * side of the trade.</p>
 *
 * <h2>The subject is the record. The actor rides in {@code data}.</h2>
 *
 * <p><strong>This is an estate decision of 2026-09-18 (hc-admin item 124) and it reverses what this class said
 * first.</strong> The original argument here was that the subject is the actor, because {@code PatientEvent.Subject}
 * already means a person on {@code patient-events} and a reader of both streams should not have to hold two meanings
 * for one word. That is a good <em>local</em> argument and it was the wrong <em>global</em> answer: four products
 * built their {@code .event} producer on the same day, from prose, and shipped <strong>three</strong> different
 * envelopes under one {@code type} — hc-patient's subject was the actor, hc-vendor's and hc-admin's was the record,
 * and hc-professional's was null. A consumer reading two of those channels through one code path finds
 * {@code subject.accountId} on one and {@code subject.entityId} on another with nothing in the frame to tell them
 * apart, because {@code type} is identical.</p>
 *
 * <p>The architect settled it on the reading that "subject" means <em>the thing this event is about</em>, and an
 * audit row is about a record:</p>
 *
 * <pre>
 * subject : { entityType, entityId }     the record this event is about
 * data    : { action, actorAccountId }   what happened, and who did it
 * </pre>
 *
 * <p>So the two streams this service produces really do mean different things by {@code subject}, and that is now the
 * documented answer rather than an accident: {@code patient-events} is <em>about a patient</em> and its subject is
 * that patient; this stream is <em>about a document</em> and its subject is that document. The person is a fact
 * <em>about</em> the change, which is what {@code data} is for. {@link PatientEvent} is untouched by this — its
 * subject is a different record type and its bytes are a live cross-product contract.</p>
 *
 * <p>The actor is the gateway {@code User.id} and never a login — see {@link net.jojoaddison.security.ActorAccountId}
 * for why, and for the three real callers it is legitimately {@code null} for. That did not change; only where it
 * sits did.</p>
 *
 * <p>⚠ <strong>The normative artefact is still owed.</strong> This class agreeing with three siblings today is prose
 * agreeing with prose, which is exactly what produced three shapes from one decision. Until a shared schema, a shared
 * fixture or a contract test exists — hc-admin item 124's "done when" — nothing mechanical fails if a fifth producer,
 * or an edit to this file, diverges again.</p>
 *
 * <h2>⛔ {@code data} carries identifiers and metadata. Never a changed value.</h2>
 *
 * <p>Two independent rules land on the same payload and it is worth knowing both, because either one alone would
 * look like it could be traded away. hc-admin's item 110: a channel carrying entity <em>contents</em> rebuilds the
 * local mirrors their item 107 exists to delete, so this is an architectural constraint and not only a privacy one.
 * And this subsystem's own rule, stated at length on {@link PatientEventPublisher}: a topic is the least controlled
 * copy of anything that enters it, and every one of these documents is a patient's clinical record. "All entity CRUD"
 * plus "no identifying content" can only both be true if the payload never says what the record now holds.</p>
 *
 * <p>{@link EntityEventPublisher} enforces that at runtime against a closed allowlist of keys, rather than leaving it
 * to whoever next adds a field.</p>
 *
 * @param eventId unique per emission. Delivery is at least once, so duplicates are normal rather than exceptional.
 * @param type always {@link #TYPE}. Present so the envelope stays routable by a consumer reading several of this
 *     estate's streams through one code path.
 * @param version the envelope's schema version, not the payload's.
 * @param occurredAt <strong>when</strong> — one of the five fields an audit row needs.
 * @param source which service emitted it.
 * @param subject which record changed — {@code entityType} and {@code entityId}.
 * @param data {@code action} and {@code actorAccountId} — and nothing else, ever.
 */
public record EntityEvent(
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
     * The one frame kind this stream carries.
     *
     * <p>A single type with the action in the payload, rather than three types — {@code entity.created} and friends —
     * because all three mean the same thing to the only consumer there is: write an audit row. Three types would make
     * a reader dispatch between three branches that do identical work, and would make "every entity CRUD event" a
     * claim about a set of strings rather than about a channel.</p>
     */
    public static final String TYPE = "EntityChanged";

    /** Subject component, and the field name a consumer reads: the domain class whose document changed. */
    public static final String ENTITY_TYPE = "entityType";

    /** Subject component, and the field name a consumer reads: the document's own id. */
    public static final String ENTITY_ID = "entityId";

    /** Payload key: one of {@link EntityChangeAction}. */
    public static final String ACTION = "action";

    /**
     * Payload key: the gateway {@code User.id} of whoever made the change.
     *
     * <p>Present on every frame and explicitly {@code null} when this service cannot name the caller, rather than
     * omitted — one payload shape, so a consumer never has to tell "no actor" from "this producer stopped sending
     * the field".</p>
     */
    public static final String ACTOR_ACCOUNT_ID = "actorAccountId";

    /**
     * Which record the event is about.
     *
     * <p>A typed pair rather than two more payload keys, and that is worth a sentence because the rest of this frame
     * is a {@code Map}: these two are the correlation key <em>and</em> the partition key (see
     * {@link EntityEventPublisher#KEY_HEADER}), so a consumer must be able to find them without knowing what else the
     * payload happens to hold. It is also hc-vendor's shape component for component, which is the point of the
     * 2026-09-18 decision.</p>
     *
     * @param entityType the simple class name of the domain type — {@code Medication}, {@code Task}, and so on.
     *     Deliberately not the fully-qualified name: a consumer must not be coupled to this product's package layout,
     *     and a repackaging here must not read as a new entity type there.
     * @param entityId the document's own id. Never null on a published frame — {@link EntityEventPublisher} refuses
     *     a frame that names nothing, because no consumer can turn one into an audit row.
     */
    public record Subject(String entityType, String entityId) {}
}
