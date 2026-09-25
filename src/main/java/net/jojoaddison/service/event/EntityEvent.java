package net.jojoaddison.service.event;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * One frame on {@code patient.event} — the envelope this subsystem's single channel carries.
 *
 * <h2>⭐ Two frame families, told apart by {@code type} — the architect's decision of 2026-09-25</h2>
 *
 * <p>This record began as one shape: an entity change, {@code type} always {@link #TYPE}, enough for hc-admin to write
 * one audit row and deliberately not one byte more. Backlog item 46 needed a second, because
 * {@code patient-events} cannot be retired while this subsystem's own gateway mail router is the thing reading it, and
 * an {@code EntityChanged} frame cannot carry what that router needs. The architect's ruling — quoted in hc-admin's
 * {@code broker/AdminEntityEvent.java} since 2026-09-18 and applied here unchanged rather than as a new exception:</p>
 *
 * <blockquote>A notification carries identifiers and metadata. A command carries the value it decides.</blockquote>
 *
 * <table border="1">
 *   <caption>The two families</caption>
 *   <tr><th>{@code type}</th><th>family</th><th>{@code data}</th><th>guard</th></tr>
 *   <tr>
 *     <td>{@link #TYPE}</td><td>notification</td><td>{@link #ACTION}, {@link #ACTOR_ACCOUNT_ID} and nothing else</td>
 *     <td>a closed allowlist of <em>keys</em></td>
 *   </tr>
 *   <tr>
 *     <td>{@link #COMMAND_TYPES}</td><td>command</td><td>the values the change decides — see {@link #CHANGE}</td>
 *     <td>a closed allowlist of <em>types</em></td>
 *   </tr>
 * </table>
 *
 * <p><strong>The two guards sit on different axes and that is the whole design.</strong> A notification's payload is a
 * closed shape, so an unknown <em>key</em> is a mistake and {@code EntityEventPublisher.ALLOWED_KEYS} refuses it. A
 * command's payload is values by definition, so no key list can guard it — what is closed instead is the set of
 * <em>types</em> that may travel as a command at all. ⛔ Without that second guard, "publish a command on
 * {@code patient.event}" would be a blanket bypass of the allowlist, and the next person needing one extra field would
 * reach for it. Both families additionally run
 * {@link PatientEventPublisher#assertNothingClinical(String, Map)}, which fails differently again.</p>
 *
 * <p>⚠ <strong>A reader must dispatch on {@code type} before it touches {@code data}</strong>, and a reader of
 * <em>several</em> of this estate's channels must dispatch on the topic as well. The command types are the same
 * strings as on {@code patient-events} — {@link PatientEventType#CARE_DELEGATION_CHANGED} and
 * {@link PatientEventType#DELETION_REQUEST_CHANGED}, referenced rather than re-spelt — because the gateway's mailers
 * compare against those constants and one spelling per product is the only way the two topics cannot drift. The cost
 * is that one {@code type} string now names two envelopes across two topics: {@link PatientEvent}'s subject is the
 * patient, this one's is the record. Within {@code patient.event} the type determines the shape, which is the property
 * a consumer actually needs; across topics it does not, and this paragraph is the only warning there is.</p>
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
 * <h2>⛔ A NOTIFICATION's {@code data} carries identifiers and metadata. Never a changed value.</h2>
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
 * <p><strong>A command is the stated exception and not a hole in the rule.</strong> Its payload is the decision — the
 * change, and the addresses the mail it obliges must reach — because a notification of a revocation is of no use to
 * something whose job is to write to the two people affected, which is the whole of why item 46 is a migration rather
 * than a deletion. What a command must still never carry is the <em>record</em>: no blood group, no allergy, no
 * address, no administrator's free text. The two addresses are contact details, already on {@code patient-events}
 * today, and travelling nowhere new.</p>
 *
 * @param eventId unique per emission. Delivery is at least once, so duplicates are normal rather than exceptional.
 * @param type {@link #TYPE} for a notification, or one of {@link #COMMAND_TYPES} for a command. What a consumer
 *     dispatches on, and what decides the shape of {@code data}.
 * @param version the envelope's schema version, not the payload's.
 * @param occurredAt <strong>when</strong> — one of the five fields an audit row needs.
 * @param source which service emitted it.
 * @param subject which record changed — {@code entityType} and {@code entityId}. The same meaning in both families:
 *     a command about a delegation names that delegation, never the patient it is about.
 * @param data {@code action} and {@code actorAccountId} for a notification — and nothing else, ever. For a command,
 *     the values it decides; see {@link #CHANGE}.
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
     * The notification family's one type.
     *
     * <p>A single type with the action in the payload, rather than three types — {@code entity.created} and friends —
     * because all three mean the same thing to the only consumer there is: write an audit row. Three types would make
     * a reader dispatch between three branches that do identical work, and would make "every entity CRUD event" a
     * claim about a set of strings rather than about a channel.</p>
     *
     * <p>⚠ This was <em>the</em> type on this stream until 2026-09-25 and is now one of two families; see the class
     * javadoc. It must never appear in {@link #COMMAND_TYPES} — registering it there would let a command-shaped payload
     * travel under the type whose whole guard is a two-key allowlist.</p>
     */
    public static final String TYPE = "EntityChanged";

    /**
     * ⛔ The command family, closed — every {@code type} that may carry values on this channel, and no others.
     *
     * <p>Backlog item 46. These two are not a curated selection of what might be useful: they are exactly the frames
     * the gateway's {@code PatientEventMailRouter} filters for, which is why {@code patient-events} has a consumer
     * inside this subsystem and therefore cannot be deleted. Measured 2026-09-25: its three handlers read
     * {@code data.change}, the patient's address, {@code data.angelEmail} and {@code data.dueAt}, and
     * <strong>{@code data.action} cannot stand in for {@code data.change}</strong> — a {@code DeletionRequest} reaching
     * {@code COMPLETED} and a {@code CareDelegation} being revoked are both {@code SAVED}.</p>
     *
     * <p><strong>Referenced from {@link PatientEventType} rather than re-spelt.</strong> The same frame goes to both
     * topics for as long as item 46's migration lasts, the gateway compares against those constants, and a second
     * literal here is how the two would drift a rename apart.</p>
     *
     * <p>⚠ <strong>Adding to this set is a decision, not a convenience.</strong> It widens what may be published as a
     * value on a channel whose default is identifiers-only, so a new entry needs a consumer that cannot work without
     * it — the argument these two make — rather than a caller that found it handy.</p>
     */
    public static final Set<String> COMMAND_TYPES = Set.of(
        PatientEventType.CARE_DELEGATION_CHANGED,
        PatientEventType.DELETION_REQUEST_CHANGED
    );

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
     * Command payload key: which transition the record made — {@code RAISED}, {@code COMPLETED},
     * {@code REVOKED_BY_ANGEL}, {@code STANDBY_ACTIVATED} and so on.
     *
     * <p><strong>The reason the command family exists at all.</strong> {@link #ACTION} cannot stand in for it: every
     * one of those transitions is a save, so a notification says {@code UPDATED} for all of them and the mailer's
     * {@code switch} has nothing to dispatch on. The vocabulary is per type and is the same set of literals
     * {@code patient-events} carries today — the gateway's handlers compare against them as strings, so a value here
     * is a cross-repo contract in the way a type name is.</p>
     */
    public static final String CHANGE = "change";

    /**
     * Command payload key: the patient's address, lowercased — the same value, from the same field, that this frame's
     * twin on {@code patient-events} carries as {@code subject.email}.
     *
     * <p><strong>In {@code data} rather than in {@link Subject}, and that is the one place this design diverges most
     * visibly from the stream it replaces.</strong> All four products spent hc-admin item 124 converging on
     * {@code subject} meaning <em>the thing this event is about</em>; this repo's {@code 216a44ca} was that repair. A
     * {@code PatientEvent}-shaped subject on this topic would put two subject meanings under one topic name, which is
     * precisely the divergence the estate has just finished removing — and it would be quiet, because a consumer that
     * dispatches on {@code type} before reading {@code subject} is safe today.</p>
     */
    public static final String PATIENT_EMAIL = "patientEmail";

    /** Command payload key: the nominated care angel's address, as stored — a contact detail, not the record. */
    public static final String ANGEL_EMAIL = "angelEmail";

    /**
     * Command payload key: when an erasure is owed by, as an ISO-8601 string.
     *
     * <p>Omitted rather than sent as null when the request has no date, exactly as on {@code patient-events}: a field a
     * consumer cannot tell from a forgotten one is worse than a missing field, and the gateway's {@code formatDue}
     * already treats absent and null identically.</p>
     */
    public static final String DUE_AT = "dueAt";

    /**
     * Which record the event is about.
     *
     * <p>A typed pair rather than two more payload keys, and that is worth a sentence because the rest of this frame
     * is a {@code Map}: these two are the correlation key, so a consumer must be able to find them without knowing what
     * else the payload happens to hold. It is also hc-vendor's shape component for component, which is the point of the
     * 2026-09-18 decision.</p>
     *
     * <p>⚠ <strong>The partition key is the subject for a notification and the patient for a command</strong>, so this
     * pair is no longer "the partition key" in both families — {@link EntityEventPublisher#KEY_HEADER} carries that
     * argument, and this sentence said otherwise until 2026-09-25.</p>
     *
     * @param entityType the simple class name of the domain type — {@code Medication}, {@code Task}, and so on.
     *     Deliberately not the fully-qualified name: a consumer must not be coupled to this product's package layout,
     *     and a repackaging here must not read as a new entity type there.
     * @param entityId the document's own id. Never null on a published frame — {@link EntityEventPublisher} refuses
     *     a frame that names nothing, because no consumer can turn one into an audit row.
     */
    public record Subject(String entityType, String entityId) {}
}
