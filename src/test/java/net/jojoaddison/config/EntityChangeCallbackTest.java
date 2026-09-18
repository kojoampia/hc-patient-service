package net.jojoaddison.config;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import net.jojoaddison.domain.Task;
import net.jojoaddison.security.ActorAccountId;
import net.jojoaddison.service.event.EntityChangeAction;
import net.jojoaddison.service.event.EntityEventPublisher;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.mapping.event.AfterDeleteEvent;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;

/**
 * That every kind of write produces the frame it should, and that a create is told apart from an update.
 *
 * <p>Backlog item 45. The create/update distinction is the part with a real mechanism behind it — Spring Data's
 * {@code AfterSaveEvent} is identical for both, so the answer has to be carried over from
 * {@code BeforeConvertEvent} — and a regression there is silent: every frame would simply say {@code UPDATED}, which
 * reads like a service where nothing is ever created rather than like a broken listener.</p>
 */
class EntityChangeCallbackTest {

    private static final String COLLECTION = "task";

    private final EntityEventPublisher publisher = mock(EntityEventPublisher.class);

    private final ActorAccountId actorAccountId = mock(ActorAccountId.class);

    private final EntityChangeCallback callback = new EntityChangeCallback(publisher, actorAccountId);

    @Test
    void aDocumentWithNoIdBeforeConversionIsReportedAsCreated() {
        when(actorAccountId.current()).thenReturn(Optional.of("account-7"));

        Task task = new Task();
        callback.onBeforeConvert(new BeforeConvertEvent<>(task, COLLECTION));
        // The id is generated between the two events, exactly as Mongo would.
        task.setId("task-1");
        callback.onAfterSave(new AfterSaveEvent<>(task, new Document("_id", "task-1"), COLLECTION));

        verify(publisher).publish("Task", "task-1", EntityChangeAction.CREATED, "account-7");
    }

    @Test
    void aDocumentThatAlreadyHadAnIdIsReportedAsUpdated() {
        when(actorAccountId.current()).thenReturn(Optional.of("account-7"));

        Task task = new Task();
        task.setId("task-1");
        callback.onBeforeConvert(new BeforeConvertEvent<>(task, COLLECTION));
        callback.onAfterSave(new AfterSaveEvent<>(task, new Document("_id", "task-1"), COLLECTION));

        verify(publisher).publish("Task", "task-1", EntityChangeAction.UPDATED, "account-7");
    }

    @Test
    void aSaveWithNoPrecedingConversionIsReportedAsUpdatedRatherThanGuessed() {
        // Defensive: the two events are raised as a pair by MongoTemplate, but a listener must not depend on having
        // seen the first one. Reporting UPDATED is the conservative answer — see EntityChangeAction's known limit.
        when(actorAccountId.current()).thenReturn(Optional.of("account-7"));

        Task task = new Task();
        task.setId("task-1");
        callback.onAfterSave(new AfterSaveEvent<>(task, new Document("_id", "task-1"), COLLECTION));

        verify(publisher).publish("Task", "task-1", EntityChangeAction.UPDATED, "account-7");
    }

    @Test
    void aDeleteProducesAFrame() {
        // The write instrumentation misses. A delete has no entity left to inspect, so the id comes off the query
        // document and the type off the event.
        when(actorAccountId.current()).thenReturn(Optional.of("account-7"));

        callback.onAfterDelete(deleteEvent(Task.class, "task-1"));

        verify(publisher).publish("Task", "task-1", EntityChangeAction.DELETED, "account-7");
    }

    @Test
    void aDeleteIssuedAgainstACollectionRatherThanAClassStillNamesSomething() {
        when(actorAccountId.current()).thenReturn(Optional.of("account-7"));

        callback.onAfterDelete(deleteEvent(null, "task-1"));

        verify(publisher).publish(COLLECTION, "task-1", EntityChangeAction.DELETED, "account-7");
    }

    /**
     * ⚠ A KNOWN LIMIT, PINNED SO IT STAYS KNOWN — a criteria delete produces no frame.
     *
     * <p>{@code AfterDeleteEvent} carries the <em>query</em> that matched, not the documents it removed. A delete by
     * id therefore carries {@code {_id: …}} and is attributable; a delete by criteria carries something like
     * {@code {patient_id: …}} and names nothing, so the publisher refuses it rather than emitting a frame with a null
     * id — and one frame could not name the several documents removed in any case.</p>
     *
     * <p><strong>{@code PatientErasureService} deletes by criteria</strong>, so an erasure produces no frames on this
     * channel. That is recorded in item 45's report as an open gap rather than fixed here. This test exists so that
     * the day somebody closes it, a green assertion has to be deliberately changed rather than a silence quietly
     * filled — and so that nobody reads "all entity CRUD" as covering it in the meantime.</p>
     */
    @Test
    void aCriteriaDeleteNamesNothingAndIsRefusedRatherThanPublishedWithANullId() {
        when(actorAccountId.current()).thenReturn(Optional.of("account-7"));

        @SuppressWarnings("unchecked")
        AfterDeleteEvent<Object> byCriteria = (AfterDeleteEvent<Object>) (AfterDeleteEvent<?>) new AfterDeleteEvent<>(
            new Document("patient_id", "patient-1"),
            Task.class,
            COLLECTION
        );
        callback.onAfterDelete(byCriteria);

        verify(publisher).publish(eq("Task"), isNull(), eq(EntityChangeAction.DELETED), eq("account-7"));
        // …and the publisher is what refuses it. EntityEventPublisherTest.aFrameThatNamesNothingIsRefusedRatherThanSent
        // holds that half, so the two together say: the callback reports honestly, the publisher declines to invent.
    }

    /**
     * {@code AfterDeleteEvent} is generic on the entity type while the listener is declared over {@code Object}, so
     * the cast Spring performs at dispatch has to be performed here too. Unchecked by construction, not by accident.
     */
    @SuppressWarnings("unchecked")
    private static AfterDeleteEvent<Object> deleteEvent(Class<?> type, String id) {
        return (AfterDeleteEvent<Object>) (AfterDeleteEvent<?>) new AfterDeleteEvent<>(new Document("_id", id), type, COLLECTION);
    }

    @Test
    void aCallerThisServiceCannotNameProducesAFrameWithNoActor() {
        // A Mongock migration, a startup seed, or an administrator whose account lives in a sibling gateway. The
        // frame is still published; the actor is simply absent.
        when(actorAccountId.current()).thenReturn(Optional.empty());

        Task task = new Task();
        task.setId("task-1");
        callback.onAfterSave(new AfterSaveEvent<>(task, new Document("_id", "task-1"), COLLECTION));

        verify(publisher).publish(eq("Task"), eq("task-1"), eq(EntityChangeAction.UPDATED), isNull());
    }

    @Test
    void aFailureResolvingTheActorCannotFailTheWriteItDescribes() {
        when(actorAccountId.current()).thenThrow(new IllegalStateException("mongo is away"));

        Task task = new Task();
        task.setId("task-1");
        callback.onAfterSave(new AfterSaveEvent<>(task, new Document("_id", "task-1"), COLLECTION));

        // Swallowed, and nothing published — the record it describes is unaffected either way.
        verifyNoInteractions(publisher);
    }
}
