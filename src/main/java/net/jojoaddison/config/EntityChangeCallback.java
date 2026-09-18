package net.jojoaddison.config;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.jojoaddison.security.ActorAccountId;
import net.jojoaddison.service.event.EntityChangeAction;
import net.jojoaddison.service.event.EntityEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterDeleteEvent;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;
import org.springframework.stereotype.Component;

/**
 * Observes every document this service writes or removes, and publishes one {@code patient.event} frame for each.
 *
 * <p>Backlog item 45. {@link AbstractMongoEventListener}{@code <Object>} fires for <strong>every collection</strong>,
 * which is exactly the scope the estate decision asks for — one channel per product carrying all entity CRUD.
 * hc-admin's {@code AuditLogCallback} is the reference implementation this follows, minus its write and plus the
 * publish.</p>
 *
 * <h2>Why a listener and not a call at each write</h2>
 *
 * <p>The alternative — a publish beside each {@code repository.save} — is the shape that produced this estate's
 * recurring defect: it covers the writes somebody remembered and silently misses the rest, and nothing reports the
 * gap. This subsystem has 23 document types written from services, resources, migrations and two seed initializers;
 * a listener covers every one of them the day it is added and covers a new entity the day it is generated. The cost
 * is stated below rather than hidden: there are writes it cannot see.</p>
 *
 * <h2>⚠ What this cannot see — established by reading the call sites, and larger than it first looks</h2>
 *
 * <p>Spring Data raises these events for {@code save}, {@code insert} and {@code remove} going through
 * {@code MongoTemplate} or a repository built on it. <strong>Two shapes of write are therefore invisible or
 * unattributable, and this service already uses both.</strong> They are named here rather than left to be discovered,
 * because a channel claiming "every entity CRUD event" that quietly omits two of them is worse than one that says
 * what it covers.</p>
 *
 * <p><strong>1. Query-based updates raise no event at all.</strong> {@code updateFirst}, {@code updateMulti},
 * {@code upsert} and {@code findAndModify} change documents without ever materialising one, so there is no entity to
 * hand a listener. Two live call sites: the Mongock change units under {@code config/dbmigrations} backfill with
 * {@code updateFirst}, and — the one that matters — {@code MembershipService} moves a membership
 * {@code PENDING → ACTIVE} and {@code PENDING → CANCELLED} with {@code findAndModify}, because the atomicity of that
 * transition <em>is</em> its concurrency guard and must not be traded away for an event. <strong>So a plan activation
 * produces no frame on this channel.</strong> It is not thereby invisible to hc-admin — they publish the decision
 * that causes it and this service answers on {@code patient-events-plan} — but it is absent from the audit stream.</p>
 *
 * <p><strong>2. Criteria deletes raise an event that names no document.</strong> {@code AfterDeleteEvent} carries the
 * <em>query</em> rather than the removed document — after the fact there is nothing else left to carry — so a delete
 * by id yields {@code {_id: …}} and produces a frame, while a delete by criteria yields something like
 * {@code {patient_id: …}} and produces none: {@link EntityEventPublisher} refuses a frame it cannot attribute rather
 * than emitting one with a null id, and one frame per {@code remove} call could not name the several documents it
 * removed anyway. <strong>{@code PatientErasureService} deletes by criteria</strong>
 * ({@code mongoTemplate.remove(byPatient, type)}), so an erasure — the most audit-relevant operation this service
 * performs — produces no frames here. Erasure does announce itself on {@code patient-events} as
 * {@code DeletionRequestChanged}, which is how hc-admin learns of it today, so this is a gap in the new channel
 * rather than a regression in what hc-admin receives.</p>
 *
 * <p>Neither is closed here. The first needs a read after the modify, the second needs the ids collected before the
 * delete, and each changes a path with its own correctness argument that item 45 has no mandate to reopen.
 * {@code EntityChangeCallbackTest} pins the second so it stays a known limit rather than becoming a surprise.</p>
 *
 * <h2>Trap 1 — self-recursion — does not arise here, and that is worth saying</h2>
 *
 * <p>hc-admin's version of this class must exclude its own collection, because it <em>writes</em> an {@code AuditLog}
 * row and saving one fires the listener that saves another. This class writes nothing to Mongo: it publishes to a
 * topic. hc-admin's item 110 notes that publishing makes the loop worse rather than better, because it can now cross
 * a network — their audit row, published, consumed, written again — but that loop closes through a <em>consumer</em>
 * of this stream, and this subsystem consumes neither {@code patient.event} nor anything derived from it. The one
 * read on this path, {@link ActorAccountId}'s profile lookup, is a find and raises no event.</p>
 *
 * <p>⛔ <strong>So do not add a Mongo write to this class or anything it calls.</strong> The absence of an exclusion
 * list here is a consequence of that, not an oversight, and the first write added would need one.</p>
 *
 * <h2>Trap 4 — seed and migration volume — decided rather than discovered</h2>
 *
 * <p>Item 110 warns that "all entity CRUD" includes seeding, and that hc-admin's {@code test} profile alone would
 * publish 1236 frames before serving a request. <strong>Measured here, the exposure is three orders smaller:</strong>
 * the committed demo seed is 12 records, and {@link net.jojoaddison.config.dbmigrations.DevelopmentDataInitializer}
 * saves only the records it finds missing, so a restart against a populated database publishes nothing at all.
 * Seeded writes are therefore published like any other — a seeded document really did come into existence, and
 * excluding them would make this channel "all entity CRUD except the ones we found inconvenient". Revisit if a seed
 * here ever reaches hc-admin's order of magnitude; the publisher's bounded queue is what stops it hurting in the
 * meantime.</p>
 */
@Component
public class EntityChangeCallback extends AbstractMongoEventListener<Object> {

    private static final Logger LOG = LoggerFactory.getLogger(EntityChangeCallback.class);

    /**
     * Documents seen at {@code BeforeConvertEvent} with no id yet — which is to say, inserts.
     *
     * <p>Identity-based, not equals-based: two distinct new documents of the same type with all-null fields are
     * equal to one another and are not the same insert. Thread-confined because the two events for one save happen
     * on the thread that called {@code save}.</p>
     */
    private static final ThreadLocal<Set<Object>> PENDING_INSERTS = ThreadLocal.withInitial(() ->
        Collections.newSetFromMap(new IdentityHashMap<>())
    );

    /**
     * A save that throws between the two events leaves its entry behind, on a pooled thread that lives for the life
     * of the application. The cap bounds that: a leak costs at most this many references and then resets, where an
     * unbounded set would hold every failed write for ever. Well above any single request's document count.
     */
    private static final int PENDING_INSERTS_CAP = 256;

    private final EntityEventPublisher publisher;

    private final ActorAccountId actorAccountId;

    /**
     * Both lazy because each is built, directly or otherwise, on the {@code MongoTemplate} that raises these events —
     * injecting eagerly closes a cycle at context startup. hc-admin's {@code AuditLogCallback} carries the same note
     * for the same reason.
     */
    public EntityChangeCallback(@Lazy EntityEventPublisher publisher, @Lazy ActorAccountId actorAccountId) {
        this.publisher = publisher;
        this.actorAccountId = actorAccountId;
    }

    /**
     * Remembers that this document is about to be inserted.
     *
     * <p>The only point at which an insert and an update are distinguishable. See {@link EntityChangeAction} for the
     * full reasoning and for the one case this gets conservatively wrong.</p>
     */
    @Override
    public void onBeforeConvert(BeforeConvertEvent<Object> event) {
        Object source = event.getSource();
        if (source == null || idOf(source) != null) {
            return;
        }
        Set<Object> pending = PENDING_INSERTS.get();
        if (pending.size() >= PENDING_INSERTS_CAP) {
            // Only reachable when saves have been failing between the two events. Worth a line, because the visible
            // symptom otherwise is inserts quietly reported as updates once the cap is hit.
            LOG.warn("Clearing {} unresolved pending inserts — some saves did not complete", pending.size());
            pending.clear();
        }
        pending.add(source);
    }

    @Override
    public void onAfterSave(AfterSaveEvent<Object> event) {
        Object source = event.getSource();
        if (source == null) {
            return;
        }
        // Removed whether or not it was present, so the entry never outlives the save that created it.
        boolean wasInsert = PENDING_INSERTS.get().remove(source);
        publish(source.getClass().getSimpleName(), idOf(source), wasInsert ? EntityChangeAction.CREATED : EntityChangeAction.UPDATED);
    }

    @Override
    public void onAfterDelete(AfterDeleteEvent<Object> event) {
        // A delete reports the query it matched, because after the fact there is nothing else left to report. The
        // type can be absent when a delete was issued against a collection name rather than a class, so the
        // collection name is the fallback — it names the same thing in a different vocabulary, which is better than
        // a frame hc-admin cannot attribute to anything.
        String entityType = event.getType() == null ? event.getCollectionName() : event.getType().getSimpleName();
        Object id = event.getDocument() == null ? null : event.getDocument().get("_id");
        publish(entityType, id == null ? null : id.toString(), EntityChangeAction.DELETED);
    }

    /**
     * Resolves the actor and hands the frame to the publisher.
     *
     * <p>Every failure is caught. This runs inside somebody's write, and a stream that cannot describe a change must
     * not be able to prevent it — the rule {@code PatientEventPublisher} states for publishing, applied one level up
     * to the observation as well.</p>
     */
    private void publish(String entityType, String entityId, EntityChangeAction action) {
        try {
            // Resolved here, on the calling thread, because the security context and the request attributes exist
            // here and not on the publisher's sender thread.
            String actor = actorAccountId.current().orElse(null);
            publisher.publish(entityType, entityId, action, actor);
        } catch (RuntimeException e) {
            LOG.warn("Could not publish the {} of a {} — the record is unaffected", action, entityType, e);
        }
    }

    /**
     * The document's own id, by reflection, because the 23 document types share no interface that exposes one.
     *
     * <p>The id and nothing else. Every field beside it on these documents is a patient's clinical record.</p>
     */
    private static String idOf(Object entity) {
        try {
            Method getId = entity.getClass().getMethod("getId");
            Object id = getId.invoke(entity);
            return id == null ? null : id.toString();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
