package net.jojoaddison.service.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * into a {@link PatientEvent}, and neither can see what the binder does with a failure. The sibling
 * {@code PatientEventRoundTripIT} exists for the mirror-image reason on the outbound half, and it caught a key
 * serializer that lost every event while every test passed.</p>
 *
 * <p><b>The producer is a raw Kafka client inside this test, and it stays that way.</b> hc-admin owns the publishing
 * half, and item 19's scope fence is explicit that a fake publisher must not be built into main code to make the
 * consumer look alive. Nothing in {@code src/main} writes to this topic.</p>
 *
 * <h2>The frames are hc-admin's shape, checked against their code rather than the backlog</h2>
 *
 * <p>Their half landed at {@code ceb9eae} — {@code PlanVerificationEvent} and {@code PatientPlanVerificationService},
 * read on 2026-09-10. {@link #frame} matches what they emit: this repo's envelope, {@code type} of
 * {@code PlanVerified}, {@code subject.email} lowercased, and {@code data} of {@code {"plan": "..."}}. It is written
 * as a <b>literal</b> rather than by serialising {@link PatientEvent}, deliberately — building it through our own
 * record would make the test agree with us by construction, and a field renamed here would rename it on both sides
 * at once while the contract silently moved.</p>
 *
 * <h2>What this still cannot prove, said plainly</h2>
 *
 * <p><b>Nothing end to end.</b> Both halves now exist, and neither has been run against the other. Everything here is
 * this repository's reading of their source, reproduced by a test producer — so it proves that a frame of the shape
 * they say they send is consumed, applied and announced, and that anything else is refused rather than half-applied.
 * It does not prove that what leaves their JVM is that shape. That needs both stacks up on the quality box, and it is
 * item 19's last open bullet.</p>
 *
 * <p>Backlog item 19.</p>
 */
@IntegrationTest
class PlanVerificationRoundTripIT {

    private static final String TOPIC = "patient-events-plan";
    private static final String DLQ = "patient-events-plan.hc-patient-dlq";

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

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private PlanVerificationRepository planVerificationRepository;

    @Autowired
    private Environment environment;

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
            publish(TOPIC, "ama.verified@example.test", frame(UUID.randomUUID().toString(), email, Map.of("plan", VERIFIED_PLAN)));

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
            // Still the patient's key. An approval filed under anybody else lands on a different partition from the
            // choice it approves.
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
     * <p>Item 19 records this trap against its own idempotence test — <em>"observed mutation where the hazard is
     * re-selection … all five tests stayed green with the bug present"</em>. It has now caught the same author twice,
     * which is the argument for mutating every guard rather than trusting that an assertion looks relevant.</p>
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
        String acknowledgement = frame(eventId, email, Map.of("plan", REPLAY_PLAN));
        publish(TOPIC, "kofi.replay@example.test", acknowledgement);
        assertThat(awaitStatus(membershipId, MembershipStatus.ACTIVE).getStatus()).isEqualTo(MembershipStatus.ACTIVE);

        // The patient subscribes to the same tier again — a renewal, or a second household member. This membership
        // has been verified by nobody, and the frame below predates it.
        String chosenLater = givenAPendingMembership(email, patientId, REPLAY_PLAN);

        try (KafkaConsumer<String, String> dlq = consumerFromNow(DLQ)) {
            publish(TOPIC, "kofi.replay@example.test", acknowledgement);

            // Doubles as the settle window: longer than one delivery plus every retry, so "still PENDING" below means
            // the consumer decided not to touch it rather than not having got to it yet.
            ConsumerRecord<String, String> dead = pollFor(dlq, record -> record.value().contains(eventId), Duration.ofSeconds(6));

            assertThat(dead)
                .as("the replay was dead-lettered instead of ignored — every redelivery would fill the DLQ with successes")
                .isNull();
        }

        // THE ASSERTION THE LEDGER EXISTS FOR. Without existsById this membership is ACTIVE, activated by a duplicate
        // delivery of an acknowledgement that was about the one above it.
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

        publish(TOPIC, "adwoa.twice@example.test", frame(UUID.randomUUID().toString(), email, Map.of("plan", SATISFIED_PLAN)));
        assertThat(awaitStatus(membershipId, MembershipStatus.ACTIVE).getStatus()).isEqualTo(MembershipStatus.ACTIVE);

        // A DIFFERENT event id, which is the whole point — the ledger cannot see this one coming.
        String secondPress = UUID.randomUUID().toString();

        try (KafkaConsumer<String, String> dlq = consumerFromNow(DLQ)) {
            publish(TOPIC, "adwoa.twice@example.test", frame(secondPress, email, Map.of("plan", SATISFIED_PLAN)));
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
            publish(TOPIC, "esi.afterpoison@example.test", frame(UUID.randomUUID().toString(), email, Map.of("plan", POISON_PLAN)));

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
            publish(TOPIC, "nobody@example.test", frame(eventId, "nobody@example.test", Map.of("plan", REFUSED_PLAN)));
            ConsumerRecord<String, String> dead = pollFor(
                dlq,
                record -> record.value() != null && record.value().contains(eventId),
                Duration.ofSeconds(10)
            );
            assertThat(dead).as("a refused acknowledgement was dropped instead of dead-lettered").isNotNull();
            // The bytes as sent, so an operator can see what hc-admin actually put on the wire rather than this
            // service's paraphrase of it.
            assertThat(read(dead).path("subject").path("email").asText()).isEqualTo("nobody@example.test");
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /** A patient with one membership awaiting a decision — the state hc-admin's acknowledgement is about. */
    private String givenAPendingMembership(String email, String patientId, String plan) {
        profileRepository.save(new Profile().email(email).patientId(patientId));
        return membershipRepository
            .save(new Membership().patientId(patientId).plan(plan).name(plan + " Plan").status(MembershipStatus.PENDING))
            .getId();
    }

    /**
     * hc-admin's frame, in the envelope item 18 and item 19 record: {@code subject.email} and a one-field payload.
     *
     * <p>Written as a literal string rather than by serializing {@link PatientEvent}, deliberately. This is a contract
     * with a repository that cannot be compiled against this one, and building it through our own record would make
     * the test agree with us by construction — a field renamed here would rename it on both sides at once and the
     * test would stay green while the contract moved.</p>
     */
    private static String frame(String eventId, String email, Map<String, Object> data) throws Exception {
        return MAPPER.writeValueAsString(
            Map.of(
                "eventId",
                eventId,
                "type",
                "PlanVerified",
                "version",
                1,
                "occurredAt",
                Instant.now().toString(),
                "source",
                "hcAdminService",
                "subject",
                Map.of("email", email),
                "data",
                data
            )
        );
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
