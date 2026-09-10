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
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * half — their item 54, unbuilt — and item 19's scope fence is explicit that a fake publisher must not be built into
 * main code to make the consumer look alive. Nothing in {@code src/main} writes to this topic.</p>
 *
 * <h2>What this cannot prove, said plainly</h2>
 *
 * <p><b>Nothing end to end.</b> {@code patient-events-plan} appears in no file of any hc-admin repository, so the
 * frames below are this test's idea of what they will send, in the shape item 18 and item 19 recorded. What is proved
 * is that a frame <em>of that shape</em> is consumed, applied and announced, and that a frame of any other shape is
 * refused rather than half-applied. The day their publisher lands, the first thing to check is that their envelope is
 * the one asserted here — and if it is not, this test is where the disagreement shows up.</p>
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

        publish(TOPIC, "ama.verified@example.test", frame(UUID.randomUUID().toString(), email, Map.of("plan", VERIFIED_PLAN)));

        Membership activated = awaitStatus(membershipId, MembershipStatus.ACTIVE);
        assertThat(activated.getStatus())
            .as("hc-admin's acknowledgement did not reach the membership — binding, conversion or rule")
            .isEqualTo(MembershipStatus.ACTIVE);

        // The write announces, which is what dequeues the row on hc-admin's panel — item 27 built the seam precisely
        // so this consumer would inherit it, and this asserts it really goes out over a broker rather than that a
        // mock was called. NOT proof their queue dequeues: that needs their side running (see MembershipPlanEventIT).
        try (KafkaConsumer<String, String> consumer = consumer("patient-events")) {
            ConsumerRecord<String, String> announced = pollFor(
                consumer,
                record -> {
                    JsonNode data = read(record).path("data");
                    return VERIFIED_PLAN.equals(data.path("planCode").asText(null)) && "ACTIVE".equals(data.path("status").asText(null));
                }
            );

            assertThat(announced).as("no PlanChosen carrying ACTIVE followed the acknowledgement").isNotNull();
            // Still the patient's key. An approval filed under anybody else lands on a different partition from the
            // choice it approves.
            assertThat(announced.key()).isEqualTo("ama.verified@example.test");
            assertThat(read(announced).path("type").asText()).isEqualTo("PlanChosen");
            assertThat(read(announced).path("data").path("membershipId").asText()).isEqualTo(membershipId);
        }
    }

    @Test
    void aReplayOfTheSameEventIdChangesNothingASecondTime() throws Exception {
        String email = "Kofi.Replay@Example.Test";
        String patientId = "patient-kofi-replay";
        String membershipId = givenAPendingMembership(email, patientId, REPLAY_PLAN);

        // One event id, sent twice, which is exactly what at-least-once delivery does of its own accord.
        String eventId = UUID.randomUUID().toString();
        String acknowledgement = frame(eventId, email, Map.of("plan", REPLAY_PLAN));
        publish(TOPIC, "kofi.replay@example.test", acknowledgement);
        awaitStatus(membershipId, MembershipStatus.ACTIVE);
        publish(TOPIC, "kofi.replay@example.test", acknowledgement);

        // The second delivery must be ignored rather than refused: it writes nothing, announces nothing, and does not
        // dead-letter. Were it refused instead, the membership would be ACTIVE either way and the difference would be
        // invisible here — so the ledger is what is asserted, and it must hold exactly one row.
        assertThat(awaitLedgerSettled(eventId)).as("a replay was recorded twice, or recorded under a second id").containsExactly(eventId);
        assertThat(membershipRepository.findById(membershipId).orElseThrow().getStatus()).isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    void aPoisonFrameIsDeadLetteredAndTheNextGoodFrameIsStillConsumed() throws Exception {
        String email = "Esi.AfterPoison@Example.Test";
        String patientId = "patient-esi-after-poison";
        String membershipId = givenAPendingMembership(email, patientId, POISON_PLAN);

        // Not JSON at all, so it fails before any rule in this repository gets a look at it. This is the frame that
        // would stall the partition for ever if `autoCommitOnError` were turned off, and would vanish without trace
        // if there were no DLQ.
        String poison = "this-is-not-an-event-" + UUID.randomUUID();
        publish(TOPIC, "poison", poison);
        publish(TOPIC, "esi.afterpoison@example.test", frame(UUID.randomUUID().toString(), email, Map.of("plan", POISON_PLAN)));

        // THE ASSERTION THIS TEST EXISTS FOR: the frame behind the poison is still applied. A consumer that died, or a
        // partition that stopped, leaves this PENDING for ever.
        assertThat(awaitStatus(membershipId, MembershipStatus.ACTIVE).getStatus())
            .as("the binding did not survive a poison frame — the next good acknowledgement was never applied")
            .isEqualTo(MembershipStatus.ACTIVE);

        try (KafkaConsumer<String, String> dlq = consumer(DLQ)) {
            ConsumerRecord<String, String> dead = pollFor(dlq, record -> poison.equals(record.value()));
            assertThat(dead).as("the poison frame was discarded rather than dead-lettered — its bytes are gone").isNotNull();
        }
    }

    @Test
    void anAcknowledgementThisServiceRefusesIsDeadLetteredRatherThanDropped() throws Exception {
        // A refusal, not a malformed frame: well-formed, well-keyed, and naming somebody this service has never heard
        // of. It is an administrator's decision that has not taken effect, so the bytes have to be kept — a log line
        // is not recoverable and nothing in this stack alerts on one. This is the answer to "what lands in the DLQ
        // versus what is merely ignored": a refusal lands, a replay does not.
        String eventId = UUID.randomUUID().toString();
        publish(TOPIC, "nobody@example.test", frame(eventId, "nobody@example.test", Map.of("plan", REFUSED_PLAN)));

        try (KafkaConsumer<String, String> dlq = consumer(DLQ)) {
            ConsumerRecord<String, String> dead = pollFor(dlq, record -> record.value() != null && record.value().contains(eventId));
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
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
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
     * The ledger, once it has stopped changing.
     *
     * <p>Waits for the first row and then a little longer, because the assertion is about a row that must <b>not</b>
     * appear: reading immediately would pass whether the replay was suppressed or merely slow.</p>
     */
    private List<String> awaitLedgerSettled(String eventId) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline && planVerificationRepository.findById(eventId).isEmpty()) {
            Thread.sleep(200);
        }
        Thread.sleep(2000);
        return planVerificationRepository.findAll().stream().map(net.jojoaddison.domain.PlanVerification::getId).toList();
    }

    private static JsonNode read(ConsumerRecord<String, String> record) {
        try {
            return MAPPER.readTree(record.value());
        } catch (Exception e) {
            throw new IllegalStateException("not JSON: " + record.value(), e);
        }
    }

    /**
     * Reads until a matching record shows up, ignoring anything another test left on the topic.
     *
     * <p>Fifteen seconds rather than thirty, for the reason {@code MembershipPlanEventIT} gives: this suite's
     * {@code junit.jupiter.execution.timeout.default} is thirty, so a longer poll would be killed by the harness and
     * report {@code TimeoutException} instead of the assertion that says what actually went wrong.</p>
     */
    private static ConsumerRecord<String, String> pollFor(
        KafkaConsumer<String, String> consumer,
        java.util.function.Predicate<ConsumerRecord<String, String>> matches
    ) {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
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

    private KafkaConsumer<String, String> consumer(String topic) {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", brokers);
        properties.put("group.id", "plan-verification-round-trip-" + UUID.randomUUID());
        properties.put("auto.offset.reset", "earliest");
        properties.put("key.deserializer", StringDeserializer.class.getName());
        properties.put("value.deserializer", StringDeserializer.class.getName());
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties);
        consumer.subscribe(List.of(topic));
        return consumer;
    }
}
