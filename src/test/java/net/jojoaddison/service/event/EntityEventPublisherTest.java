package net.jojoaddison.service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;

/**
 * What a frame on {@code patient.event} may and may not carry.
 *
 * <p>Backlog item 45. Two of these are the kind of rule that is easy to hold today and easy to lose in six months —
 * "identifiers only" and "the actor is an account id" — so they are pinned as assertions rather than left to a class
 * comment. The third, the rejection policy, is a one-word edit away from silently undoing the reason this publisher
 * has a thread at all.</p>
 *
 * <p>A fourth arrived with hc-admin item 124 and it is the reason {@link #theSubjectIsTheRecordAndNeverTheActor}
 * exists: four products shipped three different envelopes under one {@code type}, this one among them, and the
 * estate's answer is that {@code subject} is the record. That is a cross-product contract a reader of this file alone
 * cannot see, so it is asserted here rather than only argued in {@link EntityEvent}'s javadoc.</p>
 *
 * <p>Sends are asynchronous, so every verification here carries a timeout. A bare {@code verify} would race the
 * sender thread and fail intermittently — which is worse than not testing it, because the flake would eventually be
 * "fixed" by deleting the assertion.</p>
 */
class EntityEventPublisherTest {

    private static final long SEND_TIMEOUT_MS = 5_000;

    @Test
    void anEntityChangeProducesOneFrameCarryingTheFiveFieldsAnAuditRowNeeds() {
        StreamBridge bridge = mock(StreamBridge.class);

        new EntityEventPublisher(bridge, new SimpleMeterRegistry()).publish("Medication", "med-1", EntityChangeAction.CREATED, "account-7");

        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.captor();
        verify(bridge, timeout(SEND_TIMEOUT_MS)).send(eq(EntityEventPublisher.BINDING), captor.capture());
        // Exactly one. A callback that fired twice per save would be invisible to every other assertion here.
        verifyNoMoreInteractions(bridge);

        EntityEvent event = (EntityEvent) captor.getValue().getPayload();

        // 1 and 2 — what changed. In the subject, because the frame is about that document.
        assertThat(event.subject().entityType()).isEqualTo("Medication");
        assertThat(event.subject().entityId()).isEqualTo("med-1");
        // 3 — what happened to it.
        assertThat(event.data()).containsEntry(EntityEvent.ACTION, "CREATED");
        // 4 — when.
        assertThat(event.occurredAt()).as("an audit row without a time is not a row").isNotNull();
        // 5 — who. A fact about the change, so it is in the payload rather than the subject.
        assertThat(event.data()).containsEntry(EntityEvent.ACTOR_ACCOUNT_ID, "account-7");

        assertThat(event.eventId()).as("the idempotency key; delivery is at least once").isNotBlank();
        assertThat(event.type()).isEqualTo(EntityEvent.TYPE);
        assertThat(event.version()).isEqualTo(EntityEvent.VERSION);
        assertThat(event.source()).isEqualTo("hcPatientService");
    }

    @Test
    void aDeleteProducesAFrameToo() {
        // Deletes are what instrumentation misses: they are rarer, they are the writes a per-call-site publisher
        // forgets, and an audit trail that records every creation and no removal is worse than none — it asserts
        // that everything ever created still exists.
        StreamBridge bridge = mock(StreamBridge.class);

        new EntityEventPublisher(bridge, new SimpleMeterRegistry())
            .publish("Allergy", "allergy-3", EntityChangeAction.DELETED, "account-7");

        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.captor();
        verify(bridge, timeout(SEND_TIMEOUT_MS)).send(eq(EntityEventPublisher.BINDING), captor.capture());
        EntityEvent event = (EntityEvent) captor.getValue().getPayload();

        assertThat(event.data()).containsEntry(EntityEvent.ACTION, "DELETED");
        assertThat(event.subject().entityId()).isEqualTo("allergy-3");
    }

    /**
     * ⛔ hc-admin item 124, and the one assertion in this file that is about the estate rather than about this
     * service.
     *
     * <p>Four products built a {@code .event} producer on the same day from prose, and shipped three envelopes under
     * one {@code type}: subject-is-the-actor here, subject-is-the-record in hc-vendor and hc-admin, and a null
     * subject in hc-professional. The architect settled it on subject-is-the-record, so this file's original shape —
     * {@code Subject(accountId)} — is the regression to guard against, and it is a regression a local reader would
     * see as a tidy-up, because {@code PatientEvent.Subject} genuinely does mean the actor on the stream next door.</p>
     *
     * <p>It asserts the component <em>names</em> and not only the count: a two-field subject holding, say, the
     * account id and the login would pass a count check and be the exact defect this item is about.</p>
     */
    @Test
    void theSubjectIsTheRecordAndNeverTheActor() {
        StreamBridge bridge = mock(StreamBridge.class);

        new EntityEventPublisher(bridge, new SimpleMeterRegistry()).publish("Medication", "med-1", EntityChangeAction.CREATED, "account-7");

        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.captor();
        verify(bridge, timeout(SEND_TIMEOUT_MS)).send(eq(EntityEventPublisher.BINDING), captor.capture());
        EntityEvent event = (EntityEvent) captor.getValue().getPayload();

        assertThat(EntityEvent.Subject.class.getRecordComponents())
            .as("subject is { entityType, entityId } — the record this event is about, per hc-admin item 124")
            .extracting(RecordComponent::getName)
            .containsExactly(EntityEvent.ENTITY_TYPE, EntityEvent.ENTITY_ID);

        assertThat(event.subject().entityType()).isEqualTo("Medication");
        assertThat(event.subject().entityId()).isEqualTo("med-1");
        assertThat(event.data())
            .as("the actor is a payload field on this channel, not the subject")
            .containsEntry(EntityEvent.ACTOR_ACCOUNT_ID, "account-7");

        // And the stream next door still means the other thing, which is why the two records are separate classes.
        assertThat(PatientEvent.Subject.class.getRecordComponents())
            .as("patient-events is about a patient and its subject stays the patient")
            .extracting(RecordComponent::getName)
            .contains("email");
    }

    /**
     * The rule most easily lost later, asserted as an absence rather than a presence.
     *
     * <p>A test that checks the three keys are <em>present</em> goes on passing when a fourth is added beside them.
     * This one fails.</p>
     */
    @Test
    void theFrameCarriesNoFieldValues() {
        StreamBridge bridge = mock(StreamBridge.class);

        new EntityEventPublisher(bridge, new SimpleMeterRegistry())
            .publish("Profile", "profile-1", EntityChangeAction.UPDATED, "account-7");

        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.captor();
        verify(bridge, timeout(SEND_TIMEOUT_MS)).send(eq(EntityEventPublisher.BINDING), captor.capture());
        EntityEvent event = (EntityEvent) captor.getValue().getPayload();

        assertThat(event.data().keySet())
            .as("the payload is a closed shape — a new key here is a field value reaching the wire")
            .containsExactlyInAnyOrder(EntityEvent.ACTION, EntityEvent.ACTOR_ACCOUNT_ID);

        // And the subject too: two fields naming the record, so the shape itself cannot come to carry a name.
        assertThat(EntityEvent.Subject.class.getRecordComponents()).as("Subject names the record and nothing else").hasSize(2);
    }

    @Test
    void aPayloadCarryingAnythingButTheActionAndTheActorIsRefused() {
        Map<String, Object> smuggled = new HashMap<>();
        smuggled.put(EntityEvent.ACTION, "UPDATED");
        smuggled.put(EntityEvent.ACTOR_ACCOUNT_ID, "account-7");
        // The realistic mistake: not a clinical field, but a helpful one. This is what the allowlist is for and what
        // PatientEventPublisher's denylist would wave through — neither guard subsumes the other.
        smuggled.put("dosage", "500mg");

        assertThatThrownBy(() -> EntityEventPublisher.assertIdentifiersOnly(smuggled))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dosage");
    }

    @Test
    void theActorIsAnAccountIdAndNeverALoginOrAnEmail() {
        // A login or an email would arrive here as the actorAccountId argument — there is no other way in — so the
        // assertion that matters is that what the frame carries is what the caller was handed, unchanged and
        // unenriched. The refusal of logins lives where they are resolved: ActorAccountId never returns one.
        StreamBridge bridge = mock(StreamBridge.class);

        new EntityEventPublisher(bridge, new SimpleMeterRegistry())
            .publish("Profile", "profile-1", EntityChangeAction.UPDATED, "account-7");

        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.captor();
        verify(bridge, timeout(SEND_TIMEOUT_MS)).send(eq(EntityEventPublisher.BINDING), captor.capture());
        EntityEvent event = (EntityEvent) captor.getValue().getPayload();

        String serialized = event.toString();
        assertThat(serialized).as("no login on the wire").doesNotContain("ama");
        assertThat(serialized).as("no email on the wire").doesNotContain("@");
        assertThat(event.data()).containsEntry(EntityEvent.ACTOR_ACCOUNT_ID, "account-7");
    }

    /**
     * Item 45's conflict, tested rather than assumed.
     *
     * <p>{@link PatientEventPublisher} refuses identifying content <em>at runtime</em>, and this stream carries an
     * actor — so the question "does the existing refusal reject the frames this item adds" has to be answered by
     * running it, not by reading the key list and deciding it looks fine.</p>
     */
    @Test
    void theExistingIdentifyingContentRefusalAcceptsAnAccountIdCarryingFrame() {
        Map<String, Object> payload = new HashMap<>();
        payload.put(EntityEvent.ACTION, "CREATED");
        payload.put(EntityEvent.ACTOR_ACCOUNT_ID, "account-7");

        assertThatCode(() -> PatientEventPublisher.assertNothingClinical(EntityEvent.TYPE, payload)).doesNotThrowAnyException();

        // And the whole path, not only the guard: an accountId-carrying frame reaches the bridge.
        StreamBridge bridge = mock(StreamBridge.class);
        new EntityEventPublisher(bridge, new SimpleMeterRegistry()).publish("Medication", "med-1", EntityChangeAction.CREATED, "account-7");
        verify(bridge, timeout(SEND_TIMEOUT_MS)).send(eq(EntityEventPublisher.BINDING), org.mockito.ArgumentMatchers.any());
    }

    /**
     * Item 73's gate for the one HAND-COUNTED site.
     *
     * <p>This class keeps its inline executor (item 71 deliberately left it untouched), so its drop count is not
     * constructor-enforced the way {@code AsyncEventSender}'s is — it lives in the
     * {@code catch (RejectedExecutionException)} and could be forgotten by an edit that keeps every other test
     * green. This test is the compensation: it drives a real drop through the 512-slot queue and watches the
     * counter move under {@code topic=patient.event}.</p>
     */
    @Test
    void aDroppedEntityChangeMovesTheCounter() throws Exception {
        io.micrometer.core.instrument.simple.SimpleMeterRegistry registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        CountDownLatch wedge = new CountDownLatch(1);
        CountDownLatch occupied = new CountDownLatch(1);
        StreamBridge bridge = mock(StreamBridge.class);
        org.mockito.Mockito
            .when(
                bridge.send(
                    org.mockito.ArgumentMatchers.any(String.class),
                    org.mockito.ArgumentMatchers.any(org.springframework.messaging.Message.class)
                )
            )
            .thenAnswer(call -> {
                occupied.countDown();
                wedge.await();
                return true;
            });
        EntityEventPublisher publisher = new EntityEventPublisher(bridge, registry);

        try {
            publisher.publish("Medication", "med-0", EntityChangeAction.CREATED, null);
            assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();

            for (int i = 0; i < 513; i++) {
                publisher.publish("Medication", "med-" + i, EntityChangeAction.UPDATED, null);
            }

            assertThat(
                registry.get(DroppedEventCounter.METER_NAME).tag(DroppedEventCounter.TOPIC_DIMENSION, "patient.event").counter().count()
            )
                .as("the one overflow publish is the one counted drop")
                .isEqualTo(1.0);
        } finally {
            wedge.countDown();
        }
    }

    /**
     * ⛔ The one-word edit that would silently restore the sixty-second block.
     *
     * <p>{@code CallerRunsPolicy} is the conventional choice for a bounded queue and it hands the blocking send back
     * to the request thread exactly when the broker is slowest. Nothing else in the suite would notice.</p>
     */
    @Test
    void afullQueueDropsTheFrameRatherThanRunningItOnTheCallersThread() {
        EntityEventPublisher publisher = new EntityEventPublisher(mock(StreamBridge.class), new SimpleMeterRegistry());

        assertThat(publisher.senderForTest().getRejectedExecutionHandler()).isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
    }

    @Test
    void aFrameThatNamesNothingIsRefusedRatherThanSent() {
        StreamBridge bridge = mock(StreamBridge.class);
        EntityEventPublisher publisher = new EntityEventPublisher(bridge, new SimpleMeterRegistry());

        publisher.publish("Medication", null, EntityChangeAction.CREATED, "account-7");
        publisher.publish(null, "med-1", EntityChangeAction.CREATED, "account-7");
        publisher.publish("Medication", "med-1", null, "account-7");

        verifyNoMoreInteractions(bridge);
    }

    @Test
    void anUnnameableActorIsAbsentRatherThanAFallback() {
        // The unauthenticated write, the pre-backfill profile, and the sibling product's administrator. All three
        // must produce a frame with no actor — never a login standing in for one.
        StreamBridge bridge = mock(StreamBridge.class);

        new EntityEventPublisher(bridge, new SimpleMeterRegistry()).publish("Task", "task-1", EntityChangeAction.CREATED, null);

        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.captor();
        verify(bridge, timeout(SEND_TIMEOUT_MS)).send(eq(EntityEventPublisher.BINDING), captor.capture());
        EntityEvent event = (EntityEvent) captor.getValue().getPayload();

        // Present and null, rather than missing: the payload keeps one shape, and an explicit null says "this service
        // does not know" where an absent key would say "this producer stopped sending the field".
        assertThat(event.data()).containsKey(EntityEvent.ACTOR_ACCOUNT_ID);
        assertThat(event.data().get(EntityEvent.ACTOR_ACCOUNT_ID)).isNull();
        assertThat(event.subject().entityId()).isEqualTo("task-1");
    }
}
