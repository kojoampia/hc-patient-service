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
import java.time.Instant;
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
     *
     * <p>⚠ <strong>Filtered on {@code family=notification} since item 46</strong>, not only on the topic: two counters
     * now share that topic tag, and a search by topic alone would resolve to whichever of the two Micrometer happened
     * to hand back — a test that reads the wrong meter and passes.</p>
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
                registry
                    .get(DroppedEventCounter.METER_NAME)
                    .tag(DroppedEventCounter.TOPIC_DIMENSION, "patient.event")
                    .tag(DroppedEventCounter.FAMILY_DIMENSION, DroppedEventCounter.NOTIFICATION)
                    .counter()
                    .count()
            )
                .as("the one overflow publish is the one counted drop")
                .isEqualTo(1.0);
            assertThat(
                registry
                    .get(DroppedEventCounter.METER_NAME)
                    .tag(DroppedEventCounter.TOPIC_DIMENSION, "patient.event")
                    .tag(DroppedEventCounter.FAMILY_DIMENSION, DroppedEventCounter.COMMAND)
                    .counter()
                    .count()
            )
                .as("and the command family lost nothing — which is the whole point of the second queue")
                .isZero();
        } finally {
            wedge.countDown();
        }
    }

    /**
     * A dropped command is countable <em>as a command</em>, not merely as a loss on this topic.
     *
     * <p>Item 46's review found the gap this closes: the two families shared one counter under one {@code topic} tag,
     * so after a loss nothing but a substring of a WARN said which had been lost — and the two are not comparable. A
     * dropped notification is a row missing from hc-admin's audit trail; a dropped command is a letter a patient never
     * receives. Wedge the command queue, overflow its 128 slots, and watch the count move under
     * {@code family=command} while the notification family stays at zero.</p>
     */
    @Test
    void aDroppedCommandIsCountedAsACommandAndNotAsAnEntityChange() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CountDownLatch wedge = new CountDownLatch(1);
        CountDownLatch occupied = new CountDownLatch(1);
        StreamBridge bridge = mock(StreamBridge.class);
        org.mockito.Mockito
            .when(bridge.send(org.mockito.ArgumentMatchers.any(String.class), org.mockito.ArgumentMatchers.any(Message.class)))
            .thenAnswer(call -> {
                occupied.countDown();
                wedge.await();
                return true;
            });
        EntityEventPublisher publisher = new EntityEventPublisher(bridge, registry);

        try {
            publisher.publishCareDelegationChanged("delegation-0", "REVOKED_BY_ANGEL", "kojo@example.test", "angel@example.test");
            assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();

            for (int i = 0; i < 129; i++) {
                publisher.publishCareDelegationChanged("delegation-" + i, "REVOKED_BY_ANGEL", "kojo@example.test", "angel@example.test");
            }

            assertThat(
                registry
                    .get(DroppedEventCounter.METER_NAME)
                    .tag(DroppedEventCounter.TOPIC_DIMENSION, "patient.event")
                    .tag(DroppedEventCounter.FAMILY_DIMENSION, DroppedEventCounter.COMMAND)
                    .counter()
                    .count()
            )
                .as("the one overflow command is the one counted drop, under its own family")
                .isEqualTo(1.0);
            assertThat(
                registry
                    .get(DroppedEventCounter.METER_NAME)
                    .tag(DroppedEventCounter.TOPIC_DIMENSION, "patient.event")
                    .tag(DroppedEventCounter.FAMILY_DIMENSION, DroppedEventCounter.NOTIFICATION)
                    .counter()
                    .count()
            )
                .as("a lost letter must not read as a lost audit row")
                .isZero();
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

    // --- the command family, backlog item 46 -------------------------------------------------------------------------

    /**
     * A care-delegation change reaches the channel carrying the values it decides.
     *
     * <p>Backlog item 46. Asserted against the <strong>frame</strong> rather than against the call, because the claim
     * being tested is what a consumer finds: the gateway's {@code CareDelegationMailer} reads {@code data.change}, the
     * patient's address and {@code data.angelEmail}, and none of those exists on an {@code EntityChanged} frame. A test
     * that verified "the publisher was called" would pass with the payload empty.</p>
     */
    @Test
    void aCareDelegationChangeCarriesTheTransitionAndBothAddresses() {
        StreamBridge bridge = mock(StreamBridge.class);

        new EntityEventPublisher(bridge, new SimpleMeterRegistry())
            .publishCareDelegationChanged("delegation-1", "REVOKED_BY_ANGEL", "Kojo@Example.Test", "angel@example.test");

        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.captor();
        verify(bridge, timeout(SEND_TIMEOUT_MS)).send(eq(EntityEventPublisher.BINDING), captor.capture());
        verifyNoMoreInteractions(bridge);

        EntityEvent event = (EntityEvent) captor.getValue().getPayload();

        // The type the gateway's handler filters on — the same constant as on patient-events, never a second spelling.
        assertThat(event.type()).isEqualTo(PatientEventType.CARE_DELEGATION_CHANGED);
        assertThat(EntityEvent.COMMAND_TYPES).contains(event.type());
        // The subject is the record, per hc-admin item 124 — the delegation, not the patient.
        assertThat(event.subject().entityType()).isEqualTo("CareDelegation");
        assertThat(event.subject().entityId()).isEqualTo("delegation-1");
        // The values. `action` cannot stand in for `change`: a revocation and a ripened standby are both a save.
        assertThat(event.data()).containsEntry(EntityEvent.CHANGE, "REVOKED_BY_ANGEL");
        assertThat(event.data())
            .as("the address the mailer reads, lowercased exactly as patient-events carries it")
            .containsEntry(EntityEvent.PATIENT_EMAIL, "kojo@example.test");
        assertThat(event.data()).containsEntry(EntityEvent.ANGEL_EMAIL, "angel@example.test");
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.source()).isEqualTo("hcPatientService");
    }

    /**
     * A deletion-request change carries the date the erasure is owed by.
     *
     * <p>{@code DeletionRequestMailer} formats {@code data.dueAt} into the letter that tells a patient when their record
     * goes. It is the one payload field on either command that is not an identifier or a transition name.</p>
     */
    @Test
    void aDeletionRequestChangeCarriesTheDateTheErasureIsOwedBy() {
        StreamBridge bridge = mock(StreamBridge.class);
        Instant due = Instant.parse("2026-10-09T10:00:00Z");

        new EntityEventPublisher(bridge, new SimpleMeterRegistry())
            .publishDeletionRequestChanged("request-1", "RAISED", "kojo@example.test", due);

        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.captor();
        verify(bridge, timeout(SEND_TIMEOUT_MS)).send(eq(EntityEventPublisher.BINDING), captor.capture());
        EntityEvent event = (EntityEvent) captor.getValue().getPayload();

        assertThat(event.type()).isEqualTo(PatientEventType.DELETION_REQUEST_CHANGED);
        assertThat(event.subject().entityType()).isEqualTo("DeletionRequest");
        assertThat(event.subject().entityId()).isEqualTo("request-1");
        assertThat(event.data()).containsEntry(EntityEvent.CHANGE, "RAISED");
        assertThat(event.data()).containsEntry(EntityEvent.PATIENT_EMAIL, "kojo@example.test");
        assertThat(event.data())
            .as("the string form patient-events already sends, so the gateway's formatDue is unchanged")
            .containsEntry(EntityEvent.DUE_AT, "2026-10-09T10:00:00Z");
    }

    @Test
    void aDeletionRequestWithNoDueDateOmitsTheKeyRatherThanSendingNull() {
        // The rule patient-events states and this must not diverge from: a field a consumer cannot tell from a
        // forgotten one is worse than a missing field.
        StreamBridge bridge = mock(StreamBridge.class);

        new EntityEventPublisher(bridge, new SimpleMeterRegistry())
            .publishDeletionRequestChanged("request-1", "COMPLETED", "kojo@example.test", null);

        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.captor();
        verify(bridge, timeout(SEND_TIMEOUT_MS)).send(eq(EntityEventPublisher.BINDING), captor.capture());
        EntityEvent event = (EntityEvent) captor.getValue().getPayload();

        assertThat(event.data()).doesNotContainKey(EntityEvent.DUE_AT);
        assertThat(event.data().keySet()).containsExactly(EntityEvent.CHANGE, EntityEvent.PATIENT_EMAIL);
    }

    /**
     * ⭐ The partition-key decision, pinned in the one place a reader can see both answers at once.
     *
     * <p>A command is keyed on the <strong>patient</strong> and a notification on the <strong>record</strong>. The
     * failure that matters to a mailer is a later change overtaking an earlier one for the same person — an angel told
     * their access ended before being told they were nominated — and {@code patient-events} gives that ordering by
     * keying on the email. Keying a command on the delegation id instead would take it away, and the gateway half of
     * item 46 would then be a rebind that quietly loses a guarantee with every test still green.</p>
     */
    @Test
    void aCommandIsKeyedOnThePatientWhereANotificationIsKeyedOnTheRecord() {
        StreamBridge bridge = mock(StreamBridge.class);
        EntityEventPublisher publisher = new EntityEventPublisher(bridge, new SimpleMeterRegistry());

        publisher.publishCareDelegationChanged("delegation-1", "REVOKED_BY_PATIENT", "Kojo@Example.Test", "angel@example.test");

        ArgumentCaptor<Message<?>> command = ArgumentCaptor.captor();
        verify(bridge, timeout(SEND_TIMEOUT_MS)).send(eq(EntityEventPublisher.BINDING), command.capture());
        assertThat(command.getValue().getHeaders().get(EntityEventPublisher.KEY_HEADER))
            .as("a command's partition key is the patient, lowercased — per-patient ordering is what the mailer needs")
            .isEqualTo("kojo@example.test");

        publisher.publish("CareDelegation", "delegation-1", EntityChangeAction.UPDATED, "account-7");

        ArgumentCaptor<Message<?>> both = ArgumentCaptor.captor();
        verify(bridge, timeout(SEND_TIMEOUT_MS).times(2)).send(eq(EntityEventPublisher.BINDING), both.capture());
        assertThat(both.getAllValues().get(1).getHeaders().get(EntityEventPublisher.KEY_HEADER))
            .as("and a notification's is still the record, so one document's own history stays ordered")
            .isEqualTo("delegation-1");
    }

    /**
     * ⛔ The command family's guard, on the axis its payload cannot be guarded on.
     *
     * <p>Mutate {@link EntityEvent#COMMAND_TYPES} — add an entry, or open the check — and this goes red on its own.
     * Without it, "publish a command" is a general-purpose way round {@code ALLOWED_KEYS}: any value, under any type, on
     * a channel whose default is identifiers-only.</p>
     */
    @Test
    void aTypeThatIsNotARegisteredCommandIsRefused() {
        assertThatThrownBy(() -> EntityEventPublisher.assertIsAKnownCommand("ProfileChanged"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ProfileChanged");

        // And through the publish path, not only the static: an unregistered type must not reach the bridge either.
        StreamBridge bridge = mock(StreamBridge.class);
        EntityEventPublisher publisher = new EntityEventPublisher(bridge, new SimpleMeterRegistry());
        Map<String, Object> data = new HashMap<>();
        data.put(EntityEvent.CHANGE, "SOMETHING");
        data.put(EntityEvent.PATIENT_EMAIL, "kojo@example.test");

        assertThatThrownBy(() -> publisher.publishCommand("ProfileChanged", "Profile", "profile-1", "kojo@example.test", data))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoMoreInteractions(bridge);
    }

    /**
     * ⛔ The notification family's allowlist is untouched by item 46, and this is the assertion that says so.
     *
     * <p>The rejected alternative to two families was widening {@code ALLOWED_KEYS} to admit {@code change},
     * {@code angelEmail} and {@code dueAt} and dispatching on {@code subject.entityType}. It was declined because the
     * allowlist is what stops a mis-wired producer leaking a field, and that widening would give <em>every</em>
     * {@code EntityChanged} frame on a three-product channel room for an address. Mutate {@code ALLOWED_KEYS} to add any
     * of the three and this goes red.</p>
     */
    @Test
    void theNotificationAllowlistStillRefusesEveryKeyACommandCarries() {
        for (String commandKey : new String[] {
            EntityEvent.CHANGE,
            EntityEvent.PATIENT_EMAIL,
            EntityEvent.ANGEL_EMAIL,
            EntityEvent.DUE_AT,
        }) {
            Map<String, Object> smuggled = new HashMap<>();
            smuggled.put(EntityEvent.ACTION, "UPDATED");
            smuggled.put(EntityEvent.ACTOR_ACCOUNT_ID, "account-7");
            smuggled.put(commandKey, "anything");

            assertThatThrownBy(() -> EntityEventPublisher.assertIdentifiersOnly(smuggled))
                .as("a command's key on a notification payload must still be refused: %s", commandKey)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(commandKey);
        }
    }

    /**
     * ⛔ The notification type must never be registered as a command.
     *
     * <p>That single edit would be the blanket bypass: {@code EntityChanged} frames carrying values, under the type
     * whose entire guard is a two-key allowlist, with nothing else in the suite noticing.</p>
     */
    @Test
    void theNotificationTypeIsNotACommandType() {
        assertThat(EntityEvent.COMMAND_TYPES).doesNotContain(EntityEvent.TYPE);
        assertThatThrownBy(() -> EntityEventPublisher.assertIsAKnownCommand(EntityEvent.TYPE)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * {@code assertNothingClinical} still runs over a command payload.
     *
     * <p>It and the allowlist fail differently and neither subsumes the other — {@code EntityEventPublisher}'s own
     * javadoc says so, and a command is exempt from one of them and not the other. Mutate the
     * {@code assertNothingClinical} call out of {@code publishCommand} and this goes red on its own.</p>
     */
    @Test
    void aCommandCarryingClinicalContentIsRefused() {
        StreamBridge bridge = mock(StreamBridge.class);
        EntityEventPublisher publisher = new EntityEventPublisher(bridge, new SimpleMeterRegistry());
        Map<String, Object> data = new HashMap<>();
        data.put(EntityEvent.CHANGE, "RAISED");
        data.put(EntityEvent.PATIENT_EMAIL, "kojo@example.test");
        // The realistic mistake on this family: a command may carry values, so the temptation is to explain the
        // transition — and an administrator's or clinician's note is exactly what must not travel.
        data.put("notes", "patient reports chest pain");

        assertThatThrownBy(() ->
                publisher.publishCommand(
                    PatientEventType.DELETION_REQUEST_CHANGED,
                    "DeletionRequest",
                    "request-1",
                    "kojo@example.test",
                    data
                )
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("notes");
        verifyNoMoreInteractions(bridge);
    }

    @Test
    void aCommandThatNamesNoPatientOrNoRecordIsRefusedRatherThanSent() {
        // The rule patient-events enforces on the envelope rather than at a call site, applied here for the same
        // reason: blank counts as absent, and the mailer declines to write to a blank address — so an unkeyed frame is
        // one nobody can act on. CareDelegationService's profile lookup can legitimately return nothing.
        StreamBridge bridge = mock(StreamBridge.class);
        EntityEventPublisher publisher = new EntityEventPublisher(bridge, new SimpleMeterRegistry());

        publisher.publishCareDelegationChanged("delegation-1", "REVOKED_BY_ANGEL", null, "angel@example.test");
        publisher.publishCareDelegationChanged("delegation-1", "REVOKED_BY_ANGEL", "  ", "angel@example.test");
        publisher.publishCareDelegationChanged(null, "REVOKED_BY_ANGEL", "kojo@example.test", "angel@example.test");
        publisher.publishDeletionRequestChanged("request-1", null, "kojo@example.test", null);
        publisher.publishDeletionRequestChanged("request-1", "  ", "kojo@example.test", null);

        verifyNoMoreInteractions(bridge);
    }

    /**
     * ⛔ The same one-word edit as {@link #afullQueueDropsTheFrameRatherThanRunningItOnTheCallersThread}, in the second
     * place it now exists.
     */
    @Test
    void aFullCommandQueueDropsTheFrameRatherThanRunningItOnTheCallersThread() {
        EntityEventPublisher publisher = new EntityEventPublisher(mock(StreamBridge.class), new SimpleMeterRegistry());

        assertThat(publisher.commandSenderForTest().getRejectedExecutionHandler()).isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
    }

    /**
     * The command family has its own queue, so a burst of ordinary saves cannot evict a letter.
     *
     * <p>{@link AsyncEventSender}'s javadoc states the rule: share one queue and the noisiest stream decides what the
     * quietest loses. Here the noisy family fires on every write in the service and the quiet one obliges mail, so this
     * wedges the notification queue full and asserts that a command still goes out.</p>
     *
     * <p>⚠ <strong>The type predicate is inside the timed {@code verify}, not in an assertion after it</strong>, and the
     * difference is item 19's shape in miniature. With {@code atLeast(2)} followed by an {@code anyMatch}, a mutation
     * that shares the queue reddens on the <em>count</em> and the line carrying the actual claim never executes — the
     * test was right and its failure landed next to its reasoning rather than on it. {@code argThat} makes Mockito wait
     * for a message of this type specifically, so the red says what was lost.</p>
     */
    @Test
    void aFloodOfEntityChangesCannotDropACommand() throws Exception {
        CountDownLatch wedge = new CountDownLatch(1);
        CountDownLatch occupied = new CountDownLatch(1);
        StreamBridge bridge = mock(StreamBridge.class);
        org.mockito.Mockito
            .when(bridge.send(org.mockito.ArgumentMatchers.any(String.class), org.mockito.ArgumentMatchers.any(Message.class)))
            .thenAnswer(call -> {
                EntityEvent sent = (EntityEvent) ((Message<?>) call.getArgument(1)).getPayload();
                if (EntityEvent.TYPE.equals(sent.type())) {
                    occupied.countDown();
                    wedge.await();
                }
                return true;
            });
        EntityEventPublisher publisher = new EntityEventPublisher(bridge, new SimpleMeterRegistry());

        try {
            publisher.publish("Medication", "med-0", EntityChangeAction.CREATED, null);
            assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 600; i++) {
                publisher.publish("Medication", "med-" + i, EntityChangeAction.UPDATED, null);
            }

            publisher.publishCareDelegationChanged("delegation-1", "REVOKED_BY_ANGEL", "kojo@example.test", "angel@example.test");

            // The claim is "a command went out while the notification queue was wedged", so the type is part of what
            // Mockito waits for rather than something checked afterwards on whatever it happened to capture.
            verify(bridge, timeout(SEND_TIMEOUT_MS))
                .send(
                    eq(EntityEventPublisher.BINDING),
                    org.mockito.ArgumentMatchers.<Message<?>>argThat(message ->
                        message != null && PatientEventType.CARE_DELEGATION_CHANGED.equals(((EntityEvent) message.getPayload()).type())
                    )
                );
        } finally {
            wedge.countDown();
        }
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
