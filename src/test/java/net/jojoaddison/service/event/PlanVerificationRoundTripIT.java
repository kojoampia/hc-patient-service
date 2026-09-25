package net.jojoaddison.service.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Membership;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.enumeration.MembershipStatus;
import net.jojoaddison.repository.MembershipRepository;
import net.jojoaddison.repository.PlanVerificationRepository;
import net.jojoaddison.repository.ProfileRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/**
 * That an acknowledgement really travels: broker, binding, conversion, write, announcement — and that a frame this
 * service cannot apply lands in the dead-letter queue without taking the subscription with it.
 *
 * <h2>What only this test can see</h2>
 *
 * <p>{@code PlanVerificationConsumerTest} proves the rules with the repositories mocked, and
 * {@code PlanVerificationConsumerBindingIT} proves the configuration. Neither can see a frame fail to <em>convert</em>
 * into an {@link AdminEvent} — the envelope item 47 moved this consumer onto, and the conversion that now decides what
 * {@link AdminEvent}'s tolerance is worth — and neither can see what the binder does with a failure. The sibling
 * {@code PatientEventRoundTripIT} exists for the mirror-image reason on the outbound half, and it caught a key
 * serializer that lost every event while every test passed.</p>
 *
 * <p><b>The producer is a raw Kafka client inside this test, and it stays that way.</b> hc-admin owns the publishing
 * half, and item 19's scope fence is explicit that a fake publisher must not be built into main code to make the
 * consumer look alive. Nothing in {@code src/main} writes to this topic.</p>
 *
 * <h2>The frames are hc-admin's shape, checked against their code and against their broker</h2>
 *
 * <p><b>Item 47 changed that shape, and the change is why this class could not simply have its topic string
 * edited.</b> Their half on the retired {@code patient-events-plan} was this repo's own envelope with the address in
 * {@code subject.email}; their half on {@code admin.event} is a different class of theirs
 * ({@code PlanVerifiedEvent}, landed at {@code 7deda9a}) in the channel's own envelope, where {@code subject} names
 * the {@code DirectoryLink} an administrator pressed and the address travels in {@code data.subjectKey}.
 * {@link #verification} matches what they emit, byte for byte against a real frame read off the live quality broker
 * on 2026-09-25 — reproduced in the comment on {@link #ENTITY_CHANGED_ACTION} for the other type. It is written as a
 * <b>literal</b> rather than by serialising {@link AdminEvent}, deliberately: building it through our own record
 * would make the test agree with us by construction, and a field renamed here would rename it on both sides at once
 * while the contract silently moved.</p>
 *
 * <h2>The channel is shared, so an absence is now most of what there is to assert — and absences are dangerous</h2>
 *
 * <p>The headline behaviour of item 47 is that hc-admin's entity churn is <b>ignored</b>: not applied, not refused,
 * not dead-lettered. Every part of that is an absence, and <b>a test whose pass condition is an absence is satisfied
 * by a consumer that is not running at all</b> — which is item 32, the commit that shipped this consumer bound to a
 * topic that did not exist. So {@link #anEntityChangeIsIgnoredWhileARealVerificationInTheSameRunIsApplied} pairs the
 * absence with three positive controls in the same run: a verification that <em>is</em> applied through the same
 * binding, the ignored-frame counter, which can only move if the frame was received and declined, and the applied
 * counter, which is the one signal a drift in the type literal would flatline.</p>
 *
 * <p><b>The same argument reaches the two tests whose whole subject is a frame that does nothing</b> — a redelivery,
 * and an administrator's second press. Both asserted only that the dead-letter queue stayed empty, which a consumer
 * that never received the frame satisfies just as well; both now take a receipt from the already-satisfied counter
 * first. That path writes nothing, announces nothing and throws nothing, so a meter is the only thing on it that can
 * speak.</p>
 *
 * <h2>What this still cannot prove, said plainly</h2>
 *
 * <p><b>Nothing end to end.</b> Everything here is this repository's reading of their source and of frames on their
 * channel, reproduced by a test producer — so it proves that a frame of the shape they publish is consumed, applied
 * and announced, that a frame of every other shape on the channel is ignored, and that anything malformed and
 * addressed to us is refused rather than half-applied. It does not prove that what leaves their JVM is that shape.
 * That needs both stacks up on the quality box and a frame their service published.</p>
 *
 * <p>Backlog items 19 and 47.</p>
 */
@IntegrationTest
class PlanVerificationRoundTripIT {

    private static final String TOPIC = "admin.event";
    private static final String DLQ = "admin.event.hc-patient-dlq";

    /**
     * hc-admin's own id for the record an administrator acted on, and the Kafka key derived from it.
     *
     * <p>{@code <EntityType>/<id>} is the rule for every frame on this channel, whatever its type — their
     * {@code AdminChannel} is its one definition, and it is what keeps two presses for one link in order. <b>Note what
     * it is not: the patient's address.</b> On the retired topic the key WAS the address, and both the key and a
     * {@code patientKey} header carried it; on this channel nothing in the record's metadata names a patient at all.
     * These tests therefore publish under this key and still expect the right membership to move, which is the whole
     * of the migration in one assertion.</p>
     */
    private static final String LINK_KEY = "DirectoryLink/dl-round-trip";

    /** The payload of hc-admin's commonest frame by three orders of magnitude. See {@link #entityChanged}. */
    private static final String ENTITY_CHANGED_ACTION = "SAVED";

    /**
     * A plan code per test, each unique per run.
     *
     * <p><b>Per test, not per class, and that is not tidiness.</b> These four tests share one topic, one broker and —
     * with {@code TESTCONTAINERS_REUSE_ENABLE} — one that outlives the run. A single shared code let the announcement
     * poll below match a frame a <em>different</em> test in this class had put on {@code patient-events}, and it read
     * as the key being wrong rather than as the wrong record having been found. {@code MembershipPlanEventIT} carries
     * one code per test for the same reason.</p>
     */
    private static final String VERIFIED_PLAN = "PAWPAW-" + UUID.randomUUID();

    private static final String REPLAY_PLAN = "MELON-" + UUID.randomUUID();

    private static final String POISON_PLAN = "KUBE-" + UUID.randomUUID();

    private static final String REFUSED_PLAN = "ORANGE-" + UUID.randomUUID();

    private static final String SATISFIED_PLAN = "GUAVA-" + UUID.randomUUID();

    private static final String IGNORED_PLAN = "COCOA-" + UUID.randomUUID();

    private static final String MALFORMED_PLAN = "CASSAVA-" + UUID.randomUUID();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private PlanVerificationRepository planVerificationRepository;

    @Autowired
    private Environment environment;

    /**
     * Read so that "nothing happened" can be told from "nothing arrived".
     *
     * <p>The registry is the application's own, so this counter is shared with every other test in the context and its
     * absolute value means nothing. Only the <em>increase</em> across one publish is evidence, which is why every
     * reading here is a before-and-after.</p>
     */
    @Autowired
    private MeterRegistry meterRegistry;

    private String brokers;

    @BeforeEach
    void setUp() {
        brokers = environment.getRequiredProperty("spring.cloud.stream.kafka.binder.brokers");
        profileRepository.deleteAll();
        membershipRepository.deleteAll();
        planVerificationRepository.deleteAll();
    }

    @Test
    void anAcknowledgementActivatesThePendingMembershipAndAnnouncesIt() throws Exception {
        String email = "Ama.Verified@Example.Test";
        String patientId = "patient-ama-verified";
        String membershipId = givenAPendingMembership(email, patientId, VERIFIED_PLAN);

        // Opened before the frame is published, so it reads only what this acknowledgement causes.
        try (KafkaConsumer<String, String> consumer = consumerFromNow("patient-events")) {
            publish(TOPIC, LINK_KEY, verification(UUID.randomUUID().toString(), email, Map.of("plan", VERIFIED_PLAN)));

            Membership activated = awaitStatus(membershipId, MembershipStatus.ACTIVE);
            assertThat(activated.getStatus())
                .as("hc-admin's acknowledgement did not reach the membership — binding, conversion or rule")
                .isEqualTo(MembershipStatus.ACTIVE);

            // The write announces, which is what dequeues the row on hc-admin's panel — item 27 built the seam
            // precisely so this consumer would inherit it, and this asserts it really goes out over a broker rather
            // than that a mock was called. NOT proof their queue dequeues: that needs their side running.
            ConsumerRecord<String, String> announced = pollFor(
                consumer,
                record -> {
                    JsonNode data = read(record).path("data");
                    return VERIFIED_PLAN.equals(data.path("planCode").asText(null)) && "ACTIVE".equals(data.path("status").asText(null));
                },
                Duration.ofSeconds(10)
            );

            assertThat(announced).as("no PlanChosen carrying ACTIVE followed the acknowledgement").isNotNull();
            // Still the patient's key — OURS, on our own stream, and unchanged by item 47. hc-admin's frame arrived
            // keyed on a DirectoryLink id, and this service does not carry that key over: an approval filed under
            // anybody but the patient lands on a different partition from the choice it approves.
            assertThat(announced.key()).isEqualTo("ama.verified@example.test");
            assertThat(read(announced).path("type").asText()).isEqualTo("PlanChosen");
            assertThat(read(announced).path("data").path("membershipId").asText()).isEqualTo(membershipId);
        }
    }

    /**
     * That a redelivered acknowledgement cannot activate a membership it was never about.
     *
     * <h2>Two wrong versions of this test preceded this one, and both are worth keeping</h2>
     *
     * <p><b>The first asserted the ledger held exactly one row</b>, on the stated grounds that <em>"the membership
     * would be ACTIVE either way, so the ledger is what is asserted."</em> The ledger is not a place the difference
     * shows: delete the {@code existsById} guard and the replay is handled further down instead, {@code record()} is
     * never reached, and the ledger still holds one row. Measured — with the guard deleted the class passed 4/4.</p>
     *
     * <p><b>The second asserted the dead-letter queue stayed empty</b>, reasoning that ignoring leaves it empty while
     * refusing fills it. That was true when it was written and was made false by a change in the same commit:
     * {@link PlanVerificationConsumer#alreadySatisfied} now absorbs a plain replay as well — the membership is
     * {@code ACTIVE} on the plan named, which is exactly the state the frame asks for — so with the guard deleted the
     * replay is <em>ignored</em> rather than refused and the queue is empty either way. Measured again: still 5/5
     * with the bug present.</p>
     *
     * <p><b>The third version failed under the mutation for the wrong reason, and that is the subtlest of the
     * three.</b> {@link #givenAPendingMembership} saved a {@code Profile} on every call, so calling it twice for one
     * address left two — and {@code findOneByEmailIgnoreCase} throws {@code IncorrectResultSizeDataAccessException}
     * on two matches. With the guard deleted the replay therefore died at the <em>patient lookup</em>, before
     * reaching any membership: the frame dead-lettered, the DLQ assertion fired, the test went red, and the
     * re-selection hazard it names was never exercised at all. The assertion labelled below as the one the ledger
     * exists for never even ran.</p>
     *
     * <p><b>The evidence is which assertion fails, not that one does.</b> With the duplicate profile, deleting the
     * guard failed the <em>dead-letter</em> assertion; with the fixture fixed, the same mutation fails the
     * <em>membership</em> assertion instead — the re-selection hazard itself. Measured twice, at {@code :210 → :217}
     * in the file as it then stood and again at {@code :229 → :239} after these edits. The assertions are the
     * durable reference; the numbers move. That shift is the whole point: <em>a test can fail under the right
     * mutation for the wrong reason</em>, and going red is not evidence that the stated mechanism is the one
     * operating.</p>
     *
     * <p>It was worth measuring before touching the fixture rather than after, and the reason is not obvious: had
     * this gone <b>green</b> once the duplicate was removed, it would have meant the hazard is not reachable at IT
     * level at all and the assertion below was decorative. It goes red, so the hazard is genuinely detectable
     * here.</p>
     *
     * <p>Item 19 records this trap against its own idempotence test — <em>"observed mutation where the hazard is
     * re-selection … all five tests stayed green with the bug present"</em>. It has now caught the same author three
     * times, twice while writing the correction. The argument is not merely to mutate every guard; it is to check
     * <b>where</b> the mutation lands.</p>
     *
     * <h2>What the ledger uniquely buys, which is what this asserts</h2>
     *
     * <p>The two protections overlap almost everywhere and separate in one place: <b>when the patient has since
     * chosen the same tier again.</b> Then a redelivery of the old frame finds a fresh {@code PENDING} membership,
     * the plan agrees, and without the ledger it is <em>activated</em> — a membership nobody verified, brought into
     * force by a duplicate delivery of an acknowledgement about a different membership entirely. The ledger is the
     * only thing that stops it, so this is where the test has to look.</p>
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void aReplayCannotActivateAMembershipChosenAfterTheAcknowledgement() throws Exception {
        String email = "Kofi.Replay@Example.Test";
        String patientId = "patient-kofi-replay";
        String membershipId = givenAPendingMembership(email, patientId, REPLAY_PLAN);

        // One event id, sent twice, which is exactly what at-least-once delivery does of its own accord.
        String eventId = UUID.randomUUID().toString();
        String acknowledgement = verification(eventId, email, Map.of("plan", REPLAY_PLAN));
        publish(TOPIC, LINK_KEY, acknowledgement);
        assertThat(awaitStatus(membershipId, MembershipStatus.ACTIVE).getStatus()).isEqualTo(MembershipStatus.ACTIVE);

        // The patient subscribes to the same tier again — a renewal, or a second household member. This membership
        // has been verified by nobody, and the frame below predates it.
        String chosenLater = givenAPendingMembership(email, patientId, REPLAY_PLAN);
        double satisfiedBefore = satisfiedFrames();

        try (KafkaConsumer<String, String> dlq = consumerFromNow(DLQ)) {
            publish(TOPIC, LINK_KEY, acknowledgement);

            // The receipt that the redelivery was received and recognised as one — the ledger branch increments this.
            // Without it the three assertions below are absences, and "the consumer never saw the second frame"
            // satisfies every one of them.
            assertThat(awaitSatisfiedFramesAtLeast(satisfiedBefore + 1))
                .as("the already-satisfied counter never moved, so nothing here proves the redelivery was received")
                .isGreaterThan(satisfiedBefore);

            // Doubles as the settle window: longer than one delivery plus every retry, so "still PENDING" below means
            // the consumer decided not to touch it rather than not having got to it yet.
            ConsumerRecord<String, String> dead = pollFor(dlq, record -> record.value().contains(eventId), Duration.ofSeconds(6));

            assertThat(dead)
                .as("the replay was dead-lettered instead of ignored — every redelivery would fill the DLQ with successes")
                .isNull();
        }

        // THE ASSERTION THE LEDGER EXISTS FOR, and the one the mutation now lands on. Without existsById this
        // membership is ACTIVE, activated by a duplicate delivery of an acknowledgement that was about the one above
        // it. It did not run at all until the fixture stopped creating a second Profile — see the javadoc.
        assertThat(membershipRepository.findById(chosenLater).orElseThrow().getStatus())
            .as("a redelivered acknowledgement activated a membership chosen after it — nobody verified this one")
            .isEqualTo(MembershipStatus.PENDING);

        // Scoped to the patient rather than `containsExactly` over the whole ledger: the consumer is shared across
        // this class's tests and runs asynchronously, so a row from the previous test can land after @BeforeEach has
        // cleared the collection. Note "recorded twice" is not something the ledger can express — the event id IS the
        // primary key — which is why the two assertions above are the ones carrying this test.
        assertThat(ledgerIdsFor(patientId)).as("the replay was applied a second time under its own row").containsExactly(eventId);
        assertThat(membershipRepository.findById(membershipId).orElseThrow().getStatus()).isEqualTo(MembershipStatus.ACTIVE);
    }

    /**
     * That a fresh acknowledgement for a membership already activated is ignored too — which is <b>hc-admin's normal
     * recovery path</b>, not an edge case.
     *
     * <p>Their {@code PatientPlanVerificationService} mints a new {@code eventId} for every press of the verify
     * button, deliberately, because republishing unconditionally is how they recover a frame they think was lost.
     * The event-id ledger therefore cannot suppress it — by their design it must not. Without
     * {@code alreadySatisfied}, every second press and every successful recovery-republish would refuse
     * {@code NO_PENDING_MEMBERSHIP} and dead-letter, so their recovery mechanism would look like our failure and the
     * queue that is supposed to hold only real refusals would fill with frames whose fault was working.</p>
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void aSecondVerificationWithAFreshEventIdIsSatisfiedRatherThanDeadLettered() throws Exception {
        String email = "Adwoa.Twice@Example.Test";
        String patientId = "patient-adwoa-twice";
        String membershipId = givenAPendingMembership(email, patientId, SATISFIED_PLAN);

        publish(TOPIC, LINK_KEY, verification(UUID.randomUUID().toString(), email, Map.of("plan", SATISFIED_PLAN)));
        assertThat(awaitStatus(membershipId, MembershipStatus.ACTIVE).getStatus()).isEqualTo(MembershipStatus.ACTIVE);

        // A DIFFERENT event id, which is the whole point — the ledger cannot see this one coming.
        String secondPress = UUID.randomUUID().toString();
        double satisfiedBefore = satisfiedFrames();

        try (KafkaConsumer<String, String> dlq = consumerFromNow(DLQ)) {
            publish(TOPIC, LINK_KEY, verification(secondPress, email, Map.of("plan", SATISFIED_PLAN)));

            // THE RECEIPT, and this test had none until the already-satisfied path was counted. Every outcome of that
            // path is an absence — nothing written, nothing announced, nothing thrown, nothing dead-lettered — so the
            // assertion below was equally satisfied by a consumer that never received the second press at all. That is
            // item 32's failure exactly, and a counter is the only thing on this path that can speak.
            assertThat(awaitSatisfiedFramesAtLeast(satisfiedBefore + 1))
                .as("the already-satisfied counter never moved, so nothing here proves the second press was received")
                .isGreaterThan(satisfiedBefore);

            ConsumerRecord<String, String> dead = pollFor(dlq, record -> record.value().contains(secondPress), Duration.ofSeconds(6));

            assertThat(dead).as("an administrator pressing verify twice dead-lettered the second press").isNull();
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void aPoisonFrameIsDeadLetteredAndTheNextGoodFrameIsStillConsumed() throws Exception {
        String email = "Esi.AfterPoison@Example.Test";
        String patientId = "patient-esi-after-poison";
        String membershipId = givenAPendingMembership(email, patientId, POISON_PLAN);

        // Not JSON at all, so it fails before any rule in this repository gets a look at it. This is the frame that
        // would stall the partition for ever if `autoCommitOnError` were turned off, and would vanish without trace
        // if there were no DLQ.
        String poison = "this-is-not-an-event-" + UUID.randomUUID();

        try (KafkaConsumer<String, String> dlq = consumerFromNow(DLQ)) {
            publish(TOPIC, "poison", poison);
            publish(TOPIC, LINK_KEY, verification(UUID.randomUUID().toString(), email, Map.of("plan", POISON_PLAN)));

            // THE ASSERTION THIS TEST EXISTS FOR: the frame behind the poison is still applied. A consumer that died,
            // or a partition that stopped, leaves this PENDING for ever.
            assertThat(awaitStatus(membershipId, MembershipStatus.ACTIVE).getStatus())
                .as("the binding did not survive a poison frame — the next good acknowledgement was never applied")
                .isEqualTo(MembershipStatus.ACTIVE);

            ConsumerRecord<String, String> dead = pollFor(dlq, record -> poison.equals(record.value()), Duration.ofSeconds(10));
            assertThat(dead).as("the poison frame was discarded rather than dead-lettered — its bytes are gone").isNotNull();
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void anAcknowledgementThisServiceRefusesIsDeadLetteredRatherThanDropped() throws Exception {
        // A refusal, not a malformed frame: well-formed, well-keyed, and naming somebody this service has never heard
        // of. It is an administrator's decision that has not taken effect, so the bytes have to be kept — a log line
        // is not recoverable and nothing in this stack alerts on one. This is the answer to "what lands in the DLQ
        // versus what is merely ignored": a refusal lands, a replay does not.
        String eventId = UUID.randomUUID().toString();

        try (KafkaConsumer<String, String> dlq = consumerFromNow(DLQ)) {
            publish(TOPIC, LINK_KEY, verification(eventId, "nobody@example.test", Map.of("plan", REFUSED_PLAN)));
            ConsumerRecord<String, String> dead = pollFor(
                dlq,
                record -> record.value() != null && record.value().contains(eventId),
                Duration.ofSeconds(10)
            );
            assertThat(dead).as("a refused acknowledgement was dropped instead of dead-lettered").isNotNull();
            // The bytes as sent, so an operator can see what hc-admin actually put on the wire rather than this
            // service's paraphrase of it.
            assertThat(read(dead).path("data").path("subjectKey").asText()).isEqualTo("nobody@example.test");
        }
    }

    /**
     * ⭐ <b>The headline behaviour of item 47, over a real broker: hc-admin's entity churn is ignored.</b>
     *
     * <p>Not applied, not refused, not dead-lettered — and every one of those is an <b>absence</b>, which is why this
     * test carries two positive controls in the same run. <b>A test whose pass condition is an absence is satisfied by a
     * consumer that never received anything at all</b>, and that is not a hypothetical here: item 32 shipped this
     * consumer deployed, healthy and subscribed to a topic that did not exist, and no absence-shaped assertion anywhere
     * could see it.</p>
     *
     * <p>The controls are the ignored-frame <b>counter</b>, which can only move if the frame reached the handler and was
     * declined, and a real <b>verification published on the same binding in the same run</b>, which can only be applied
     * if the subscription is live. Together they turn "nothing happened" into "the frame arrived, was read, and was
     * deliberately let go".</p>
     *
     * <p>The entity change is published <em>first</em> deliberately: on one partition the consumer must get past it to
     * reach the verification, so a consumer that dead-lettered or stalled on it fails the control rather than the
     * absence.</p>
     *
     * <h2>⭐ And the two frames that measure what {@link AdminEvent}'s tolerance is actually worth</h2>
     *
     * <p>{@link AdminEvent} justifies making every component nullable on the grounds that another product's future
     * frame shape must not become dead-letter noise here. <b>Nullability covers a field that is missing; it says
     * nothing about a field that is the wrong shape</b> — and {@code Map<String, Object> data} is the strictest thing
     * left in the record. Every other fixture in this class emits exactly the seven declared fields, so until these two
     * frames the claim rested entirely on Jackson defaults that nothing here pinned.</p>
     *
     * <p><b>Measured on 2026-09-25, and the claim was half true.</b> The two frames differ in exactly one thing, which
     * is what makes this a bisection rather than an anecdote:</p>
     *
     * <table>
     *   <caption>Both carry a type nobody here has heard of and an envelope key the record does not declare</caption>
     *   <tr><th>{@code data}</th><th>outcome</th></tr>
     *   <tr><td>an object</td><td><b>ignored</b> — one string comparison, no database read, no dead letter</td></tr>
     *   <tr><td>an array</td><td><b>dead-lettered</b> after four delivery attempts</td></tr>
     * </table>
     *
     * <p>So an unknown <em>type</em> and an unknown <em>envelope key</em> really are free, and a wrong-shaped
     * {@code data} is not. {@link AdminEvent} now says so in those terms rather than claiming tolerance it does not
     * have, and this test is where that boundary is pinned — in both directions, so widening the record later fails
     * here and has to be a decision.</p>
     *
     * <p>⚠ <b>The failure is reported as {@code ClassCastException: [B cannot be cast to AdminEvent}, which names
     * nothing that is wrong.</b> Spring's JSON converter <em>declines</em> the message rather than throwing, the raw
     * {@code byte[]} is handed to the function, and the cast fails on the way in. A future reader meeting that
     * exception should read it as <em>this frame did not convert</em> and go looking at the payload — not at the
     * binding, which is what its wording suggests.</p>
     */
    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void anEntityChangeIsIgnoredWhileARealVerificationInTheSameRunIsApplied() throws Exception {
        String email = "Yaa.Ignored@Example.Test";
        String patientId = "patient-yaa-ignored";
        String membershipId = givenAPendingMembership(email, patientId, IGNORED_PLAN);

        String entityEventId = UUID.randomUUID().toString();
        String unfamiliarEventId = UUID.randomUUID().toString();
        String wrongShapedEventId = UUID.randomUUID().toString();
        double ignoredBefore = ignoredFrames();
        double appliedBefore = appliedFrames();

        try (KafkaConsumer<String, String> dlq = consumerFromNow(DLQ)) {
            publish(TOPIC, "Patient/6ab685b427645bf322b0326e", entityChanged(entityEventId));
            publish(TOPIC, "WageRate/wr-9f2c", unfamiliarFrame(unfamiliarEventId, Map.of("rate", 12, "currency", "GHS")));
            publish(TOPIC, "WageRate/wr-9f2d", unfamiliarFrame(wrongShapedEventId, List.of("audit", "only")));
            publish(TOPIC, LINK_KEY, verification(UUID.randomUUID().toString(), email, Map.of("plan", IGNORED_PLAN)));

            // POSITIVE CONTROL ONE: the binding is alive and the frame behind the entity change was applied. This also
            // doubles as the settle window for the absences below — the consumer has demonstrably reached the later
            // offset, so "the DLQ is empty" is about a frame it has already handled rather than one it has not seen.
            assertThat(awaitStatus(membershipId, MembershipStatus.ACTIVE).getStatus())
                .as("the verification behind the entity change was never applied — the frame was dead-lettered or the binding stalled")
                .isEqualTo(MembershipStatus.ACTIVE);

            // POSITIVE CONTROL TWO: the consumer saw both ignorable frames and declined them. Without this, every
            // assertion below is also satisfied by a consumer that received nothing — and EXACTLY two, not "at least",
            // because a third increment would mean the wrong-shaped frame reached the handler after all and the
            // dead letter asserted below came from somewhere else.
            assertThat(awaitIgnoredFramesAtLeast(ignoredBefore + 2))
                .as("the ignored-frame counter did not move by exactly two — see the table in this test's javadoc for what each frame does")
                .isEqualTo(ignoredBefore + 2);

            // POSITIVE CONTROL THREE: the applied counter is the signal a drift in the type literal would flatline, so
            // a run in which it never moves proves nothing about the ignores above it.
            //
            // ⚠ AWAITED, NOT READ. Reading it straight failed one run in three: the counter is incremented AFTER the
            // membership write and after the ledger row, deliberately — it counts decisions that took effect — so
            // awaitStatus can observe ACTIVE in the window between the two. That ordering is right and the test was
            // wrong, which is worth stating because the obvious fix is to move the increment earlier and that would
            // make the meter count frames that reached the write rather than writes that happened.
            assertThat(awaitAppliedFramesAtLeast(appliedBefore + 1))
                .as("the applied counter never moved, although a membership was activated on this binding")
                .isGreaterThan(appliedBefore);

            // ⚠ ONE DRAIN, THEN ASSERT — three pollFor calls here would be a false pass, and it was one before this
            // comment existed. A Kafka consumer's position only moves forward, so the first poll looking for an
            // ABSENCE reads the dead letter the third poll is looking for, finds it does not match, and drops it: the
            // positive assertion then fails against a queue that really did hold its record. Drain once and assert
            // against the collection. It stops as soon as the wrong-shaped frame arrives, which is sound because the
            // DLQ is written in source-partition order and that frame is published after both of the others.
            List<ConsumerRecord<String, String>> deadLetters = drainUntil(
                dlq,
                record -> record.value().contains(wrongShapedEventId),
                Duration.ofSeconds(20)
            );

            // THE ABSENCE THIS TEST EXISTS FOR. Under item 19's rule this frame was refused, retried four times and
            // dead-lettered — 7433 times over on the channel as it stood on 2026-09-25.
            assertThat(valuesOf(deadLetters))
                .filteredOn(value -> value.contains(entityEventId))
                .as(
                    "hc-admin's entity change was dead-lettered — the queue this service reads after a real refusal is now their audit trail"
                )
                .isEmpty();

            // The half of AdminEvent's tolerance claim that HOLDS: an unknown type carrying an undeclared envelope key
            // costs one string comparison, not a dead letter.
            assertThat(valuesOf(deadLetters))
                .filteredOn(value -> value.contains(unfamiliarEventId))
                .as("a frame of an unknown type with an undeclared envelope key was dead-lettered rather than ignored")
                .isEmpty();

            // ⛔ AND THE HALF THAT DOES NOT, pinned as what it is rather than as what the javadoc wished. A `data` that
            // is not an object does not convert, so the function never runs and the binder dead-letters the bytes.
            assertThat(valuesOf(deadLetters))
                .filteredOn(value -> value.contains(wrongShapedEventId))
                .as("a frame whose `data` is not an object is no longer dead-lettered — AdminEvent got more tolerant, so say so there")
                .hasSize(1);
        }

        // And it wrote nothing: no ledger row for a frame that was never applied, and no membership of its own. The
        // ledger is scoped to the patient for the reason ledgerIdsFor gives; the entity change names no patient at all,
        // so its id must appear under nobody.
        assertThat(planVerificationRepository.findById(entityEventId))
            .as("an ignored frame was recorded in the plan-verification ledger")
            .isEmpty();
    }

    /**
     * That a frame this service <em>is</em> addressed by and cannot read is still refused, still dead-lettered, and
     * changes nothing.
     *
     * <p><b>The inversion must not have made the consumer permissive about its own frames.</b> "Ignore what is not
     * yours" and "refuse what is yours and malformed" are one line apart in {@link PlanVerificationConsumer#apply}, and
     * an ignore placed one check too late — or a type comparison loosened to "starts with Plan" — would swallow these
     * silently.</p>
     *
     * <p><b>The patient here is real and holds a matching {@code PENDING} membership, and that is the whole design of
     * this test rather than convenience.</b> Against an unknown patient every one of these frames would be
     * dead-lettered <em>anyway</em> — {@code UNKNOWN_PATIENT} — so the dead-letter assertions would pass with the guard
     * under test deleted, which is the trap
     * {@link #aReplayCannotActivateAMembershipChosenAfterTheAcknowledgement} carries three scars from: <em>a test can
     * fail, or pass, under the right mutation for the wrong reason.</em> With a real patient behind it, deleting the
     * blank-{@code eventId} guard <b>activates the membership</b>, so the final assertion is what catches it and not the
     * queue.</p>
     *
     * <p>⚠ What this cannot separate, said rather than implied: deleting the <em>no-plan</em> guard leaves the frame
     * dead-lettered too, under {@code PLAN_DISAGREES}, because a null plan cannot match the one held. That guard's
     * distinctness is {@code PlanVerificationConsumerTest}'s to assert on its {@link Reason}; here it is one of three
     * frames proving the refusal path still reaches the queue at all.</p>
     */
    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void aMalformedVerificationIsStillRefusedAndDeadLettered() throws Exception {
        String email = "Esi.Malformed@Example.Test";
        String patientId = "patient-esi-malformed";
        String membershipId = givenAPendingMembership(email, patientId, MALFORMED_PLAN);

        String withoutAnAddressee = UUID.randomUUID().toString();
        String withoutAPlan = UUID.randomUUID().toString();

        try (KafkaConsumer<String, String> dlq = consumerFromNow(DLQ)) {
            // No data.subjectKey — the field that MOVED in item 47. A frame carrying only hc-admin's subject names a
            // record in their database and nobody here, and it must not be mistaken for a frame addressed elsewhere.
            publish(TOPIC, LINK_KEY, frame(withoutAnAddressee, "PlanVerified", Map.of("plan", MALFORMED_PLAN)));

            // No plan, so the consistency check the payload exists for cannot be made.
            publish(TOPIC, LINK_KEY, frame(withoutAPlan, "PlanVerified", Map.of("subjectKey", email)));

            // A blank eventId, which is the guard the type check now runs before — and the one frame here that WOULD be
            // applied if that guard went, because everything else about it is right.
            publish(TOPIC, LINK_KEY, verification("  ", email, Map.of("plan", MALFORMED_PLAN)));

            assertThat(pollFor(dlq, record -> record.value().contains(withoutAnAddressee), Duration.ofSeconds(20)))
                .as("a PlanVerified frame with no data.subjectKey was accepted or ignored instead of refused")
                .isNotNull();
            assertThat(pollFor(dlq, record -> record.value().contains(withoutAPlan), Duration.ofSeconds(20)))
                .as("a PlanVerified frame naming no plan was accepted or ignored instead of refused")
                .isNotNull();
            assertThat(pollFor(dlq, record -> record.value().contains("\"eventId\":\"  \""), Duration.ofSeconds(20)))
                .as("a PlanVerified frame with a blank eventId was accepted or ignored instead of refused")
                .isNotNull();
        }

        // ⭐ THE ASSERTION THE BLANK-eventId GUARD IS DETECTABLE BY. This membership is everything the third frame
        // needed except an identity to be deduplicated on; without the guard it is ACTIVE, activated by a frame no
        // replay of which could ever be recognised as one.
        assertThat(membershipRepository.findById(membershipId).orElseThrow().getStatus())
            .as("a malformed verification was applied — the ignore path has made the consumer permissive about its own frames")
            .isEqualTo(MembershipStatus.PENDING);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * A patient with one more membership awaiting a decision — the state hc-admin's acknowledgement is about.
     *
     * <p><b>One {@code Profile} per address, and the check is load-bearing rather than tidiness.</b> This saved a
     * profile on every call, so the one test that calls it twice for a patient — a patient choosing the same tier
     * again — created two documents with one email. {@code PlanVerificationConsumer} resolves the patient with
     * {@code findOneByEmailIgnoreCase}, which throws {@code IncorrectResultSizeDataAccessException} on two matches,
     * so that test's frames failed at the patient lookup before reaching any membership. See
     * {@link #aReplayCannotActivateAMembershipChosenAfterTheAcknowledgement} for what that cost.</p>
     *
     * <p>A patient really does hold at most one profile — {@code PatientScope} resolves a caller through the same
     * single-result query — so the duplicate was never a state this service can be in, and no test wanted one. The
     * other three call sites use an address of their own and call once, so this changes nothing for them.</p>
     */
    private String givenAPendingMembership(String email, String patientId, String plan) {
        if (profileRepository.findOneByEmailIgnoreCase(email).isEmpty()) {
            profileRepository.save(new Profile().email(email).patientId(patientId));
        }
        return membershipRepository
            .save(new Membership().patientId(patientId).plan(plan).name(plan + " Plan").status(MembershipStatus.PENDING))
            .getId();
    }

    /**
     * hc-admin's plan verification as it travels on {@code admin.event}.
     *
     * <p>This is the frame read off the live quality broker on 2026-09-25, with the identifiers changed:</p>
     *
     * <pre>
     * {"eventId":"52a14a67-…","type":"PlanVerified","version":1,"occurredAt":"2026-09-25T14:32:58.092Z",
     *  "source":"hcAdminService","subject":{"entityType":"DirectoryLink","entityId":"dl-plan-a6"},
     *  "data":{"plan":"PAWPAW","subjectKey":"k.darkwa@mail.gh"}}
     * </pre>
     *
     * <p>⚠ <b>The address is in {@code data.subjectKey} and nowhere else.</b> There is no {@code subject.email} on this
     * channel — the subject is the record, not the person — so a consumer still reading the old field refuses every one
     * of these {@code NO_SUBJECT_KEY} and dead-letters it. That is the failure item 47 exists to avoid and this fixture
     * is the only thing in the repository that can see it.</p>
     *
     * <p>Written as a literal string rather than by serializing {@link AdminEvent}, deliberately. This is a contract
     * with a repository that cannot be compiled against this one, and building it through our own record would make
     * the test agree with us by construction — a field renamed here would rename it on both sides at once and the
     * test would stay green while the contract moved.</p>
     */
    private static String verification(String eventId, String email, Map<String, Object> data) throws Exception {
        Map<String, Object> payload = new java.util.HashMap<>(data);
        payload.putIfAbsent("subjectKey", email);
        return frame(eventId, "PlanVerified", payload);
    }

    /**
     * hc-admin's entity-change notification — 7433 of the 7435 frames on this channel on 2026-09-25, and the shape this
     * consumer must ignore without refusing.
     *
     * <p>Read off the broker the same day:</p>
     *
     * <pre>
     * {"eventId":"f4177675-…","type":"EntityChanged","version":1,"occurredAt":"2026-09-25T14:31:16.766Z",
     *  "source":"hcAdminService","subject":{"entityType":"Patient","entityId":"6ab685b4…"},"data":{"action":"SAVED"}}
     * </pre>
     *
     * <p>Note that it carries a perfectly good {@code eventId} and no plan and no addressee, which is exactly why the
     * type has to be dispatched on <em>first</em>: under item 19's ordering it passed the blank-id guard, reached the
     * type check and was dead-lettered.</p>
     */
    private static String entityChanged(String eventId) throws Exception {
        return frame(eventId, "EntityChanged", Map.of("action", ENTITY_CHANGED_ACTION));
    }

    /**
     * A frame from hc-admin's future: a type nobody here has heard of, an envelope key this repository's record does
     * not declare, and a {@code data} that is not an object.
     *
     * <h2>What it pins, and why it is one frame rather than three</h2>
     *
     * <p>{@link AdminEvent} argues that a lenient record keeps another product's future frames out of this service's
     * dead-letter queue. <b>Nullability delivers only half of that.</b> It covers a field that is absent; it says
     * nothing about one that arrives with the wrong shape, and {@code Map<String, Object> data} is the strictest
     * remaining thing in the record — a {@code data} that is an array or a scalar is the obvious way a future frame of
     * theirs stops binding. That the binder tolerates it rests on two Jackson defaults (unknown properties ignored,
     * and how a mismatched {@code data} is handled), <em>neither of which this repository configures</em>, so this
     * fixture is what stands between that paragraph and wishful thinking.</p>
     *
     * <p>All three departures ride one frame deliberately. The assertion is that it is ignored, so a frame per
     * departure would cost three settle windows to prove one thing; and if it is ever dead-lettered instead, the
     * bisection is three publishes of a fixture that already exists rather than a test that was never written.</p>
     *
     * <p><b>It is not hc-admin's — there is no {@code WageRateChanged} on the channel today.</b> That is the point: a
     * shape this repository has never seen is exactly what the tolerance claim is about, and their real types are
     * already covered by {@link #entityChanged} and by the frames in {@code PlanVerificationConsumerTest}.</p>
     */
    private static String unfamiliarFrame(String eventId, Object data) throws Exception {
        Map<String, Object> envelope = new java.util.HashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("type", "WageRateChanged");
        envelope.put("version", 2);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("source", "hcAdminService");
        envelope.put("subject", Map.of("entityType", "WageRate", "entityId", "wr-9f2c"));
        // An envelope key AdminEvent does not declare: their schema growing a field is not this service's business.
        envelope.put("correlationId", UUID.randomUUID().toString());
        envelope.put("data", data);
        return MAPPER.writeValueAsString(envelope);
    }

    /** Any frame on the channel, in the envelope every type on it shares. */
    private static String frame(String eventId, String type, Map<String, Object> data) throws Exception {
        Map<String, Object> envelope = new java.util.HashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("type", type);
        envelope.put("version", 1);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("source", "hcAdminService");
        envelope.put("subject", Map.of("entityType", "DirectoryLink", "entityId", "dl-round-trip"));
        envelope.put("data", data);
        return MAPPER.writeValueAsString(envelope);
    }

    private void publish(String topic, String key, String value) throws Exception {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", brokers);
        properties.put("key.serializer", StringSerializer.class.getName());
        properties.put("value.serializer", StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(properties)) {
            producer.send(new ProducerRecord<>(topic, key, value)).get();
        }
    }

    /** How many frames this consumer has declined so far, across the whole context. */
    private double ignoredFrames() {
        return count(PlanVerificationConsumer.IGNORED_METER_NAME);
    }

    /** How many of hc-admin's decisions have taken effect here — the signal a drift in the type literal flatlines. */
    private double appliedFrames() {
        return count(PlanVerificationConsumer.APPLIED_METER_NAME);
    }

    /**
     * How many frames arrived asking for a state that already held.
     *
     * <p>The only observable output of that path: it writes nothing, announces nothing and throws nothing, so an
     * assertion about it without this meter can only be an absence — and an absence is what a consumer that received
     * nothing also produces.</p>
     */
    private double satisfiedFrames() {
        return count(PlanVerificationConsumer.SATISFIED_METER_NAME);
    }

    private double count(String meterName) {
        Counter counter = meterRegistry.find(meterName).tag("topic", PlanVerificationConsumer.CHANNEL).counter();
        // Absent until the first increment on a fresh registry; the consumer registers all four eagerly, so this is
        // belt and braces rather than an expected branch.
        return counter == null ? 0 : counter.count();
    }

    /** Waits for the declined count to reach a target, because the consumer runs on its own thread. */
    private double awaitIgnoredFramesAtLeast(double target) throws Exception {
        return await(this::ignoredFrames, target);
    }

    /** Waits for the already-satisfied count to reach a target. */
    private double awaitSatisfiedFramesAtLeast(double target) throws Exception {
        return await(this::satisfiedFrames, target);
    }

    /** Waits for the applied count to reach a target — see the comment at its one call site on why the wait is needed. */
    private double awaitAppliedFramesAtLeast(double target) throws Exception {
        return await(this::appliedFrames, target);
    }

    /**
     * Polls one meter until it reaches a target, or gives up with the value it actually holds.
     *
     * <p>Returns rather than asserts, so the caller's {@code as(...)} names what the absence would have meant. The
     * deadline matches the DLQ polls: long enough to outlast a delivery and its retries, so a value short of the
     * target means the frame was handled some other way rather than not yet handled.</p>
     */
    private double await(java.util.function.DoubleSupplier meter, double target) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        double seen = meter.getAsDouble();
        while (System.nanoTime() < deadline && seen < target) {
            Thread.sleep(200);
            seen = meter.getAsDouble();
        }
        return seen;
    }

    /** Reads the membership back until the consumer has moved it, or gives up with the status it actually holds. */
    private Membership awaitStatus(String membershipId, MembershipStatus expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        Membership membership = null;
        while (System.nanoTime() < deadline) {
            membership = membershipRepository.findById(membershipId).orElseThrow();
            if (expected.equals(membership.getStatus())) {
                return membership;
            }
            Thread.sleep(200);
        }
        return membership;
    }

    /**
     * The event ids the ledger holds for one patient. No settle wait of its own — the caller's DLQ poll is longer
     * than any the ledger could need.
     *
     * <p>Scoped to the patient rather than reading the whole collection, because these tests share one consumer and
     * one database and it applies frames asynchronously: a row from the previous test can arrive after
     * {@code @BeforeEach} has cleared the collection.</p>
     */
    private List<String> ledgerIdsFor(String patientId) {
        return planVerificationRepository
            .findAll()
            .stream()
            .filter(row -> patientId.equals(row.getPatientId()))
            .map(net.jojoaddison.domain.PlanVerification::getId)
            .toList();
    }

    private static JsonNode read(ConsumerRecord<String, String> record) {
        try {
            return MAPPER.readTree(record.value());
        } catch (Exception e) {
            throw new IllegalStateException("not JSON: " + record.value(), e);
        }
    }

    /**
     * Reads every record the topic produces until one matches, or the deadline passes — and <b>returns all of
     * them</b>.
     *
     * <h2>⚠ Why this exists and {@link #pollFor} was not enough</h2>
     *
     * <p><b>A Kafka consumer's position only moves forward, so two {@code pollFor} calls on one consumer are not two
     * independent questions.</b> The first one consumes and discards everything that does not match its own
     * predicate — including the record the second one is about to look for. A test that polls for an <em>absence</em>
     * and then for a <em>presence</em> therefore reports the presence missing whenever the queue delivered both
     * inside the first window, which is a false failure that depends on timing and looks exactly like the behaviour
     * under test having changed.</p>
     *
     * <p>{@code anEntityChangeIsIgnoredWhileARealVerificationInTheSameRunIsApplied} asks three questions of one
     * queue, so it drains once and filters the result. Stopping early on a match is safe there because the
     * dead-letter queue is written in source-partition order and the frame it waits for is published last of the
     * three.</p>
     */
    private static List<ConsumerRecord<String, String>> drainUntil(
        KafkaConsumer<String, String> consumer,
        java.util.function.Predicate<ConsumerRecord<String, String>> until,
        Duration deadlineAfter
    ) {
        List<ConsumerRecord<String, String>> seen = new java.util.ArrayList<>();
        long deadline = System.nanoTime() + deadlineAfter.toNanos();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
            boolean done = false;
            for (ConsumerRecord<String, String> record : records) {
                if (record.value() == null) {
                    continue;
                }
                seen.add(record);
                done = done || until.test(record);
            }
            if (done) {
                return seen;
            }
        }
        return seen;
    }

    /** The payloads, so an assertion reads as a filter over strings rather than over Kafka machinery. */
    private static List<String> valuesOf(List<ConsumerRecord<String, String>> records) {
        return records.stream().map(ConsumerRecord::value).toList();
    }

    /**
     * Reads until a matching record shows up, or the deadline passes.
     *
     * <p><b>The deadline is a parameter because the two directions need different ones.</b> Waiting for a record to
     * arrive can stop the moment it does; waiting to conclude that <em>none</em> will has to outlast the whole retry
     * schedule, or it proves only that the consumer was slower than the test.</p>
     */
    private static ConsumerRecord<String, String> pollFor(
        KafkaConsumer<String, String> consumer,
        java.util.function.Predicate<ConsumerRecord<String, String>> matches,
        Duration deadlineAfter
    ) {
        long deadline = System.nanoTime() + deadlineAfter.toNanos();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
            for (ConsumerRecord<String, String> record : records) {
                if (record.value() != null && matches.test(record)) {
                    return record;
                }
            }
        }
        return null;
    }

    /**
     * A consumer positioned at the <b>end</b> of a topic, so it sees only what is published after it is opened.
     *
     * <h2>Why not {@code subscribe} + {@code auto.offset.reset=earliest}, which is what the sibling tests do</h2>
     *
     * <p>Because these tests read the dead-letter queue, and reading it from the beginning is wrong twice over. It is
     * <b>slow and gets slower</b>: with {@code TESTCONTAINERS_REUSE_ENABLE} the broker outlives the run, so every
     * dead letter every previous run produced is scanned again, and the cost grows with the number of times anybody
     * has run the suite. And it is <b>unsound for a negative assertion</b> — "no record carrying this event id" is
     * only meaningful against records that could have been produced by this test, and a positive match against a
     * record from an earlier run would be a false failure that reproduces on one machine and nowhere else.</p>
     *
     * <p>Opening at the end makes both assertions O(what this test caused). It has to be opened <em>before</em> the
     * frame under test is published, which is why every caller does that rather than publishing first — the poll
     * after {@code seekToEnd} is what forces the position to resolve, and without it the assignment is lazy and the
     * seek has nothing to act on.</p>
     */
    private KafkaConsumer<String, String> consumerFromNow(String topic) {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", brokers);
        properties.put("group.id", "plan-verification-round-trip-" + UUID.randomUUID());
        properties.put("key.deserializer", StringDeserializer.class.getName());
        properties.put("value.deserializer", StringDeserializer.class.getName());

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties);
        List<TopicPartition> partitions = consumer
            .partitionsFor(topic)
            .stream()
            .map(partition -> new TopicPartition(topic, partition.partition()))
            .toList();
        consumer.assign(partitions);
        consumer.seekToEnd(partitions);
        // FORCES THE LAZY SEEK TO RESOLVE NOW, and it has to be position() rather than poll(Duration.ZERO).
        //
        // This was poll(ZERO) and it is not equivalent: that call can return without resolving anything, so the
        // seek stays pending and the NEXT poll positions at the end as of then — which is after the frame under
        // test was published. It cost a false failure on the poison test, and it would have been much worse than
        // that: a mispositioned consumer sees nothing, and "sees nothing" is exactly what the two negative
        // assertions in this class are looking for. They would have passed for the wrong reason, which is the
        // vacuous-test failure this class already carries one scar from.
        partitions.forEach(consumer::position);
        return consumer;
    }
}
