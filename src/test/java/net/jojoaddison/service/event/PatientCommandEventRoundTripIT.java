package net.jojoaddison.service.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/**
 * That a command frame really reaches {@code patient.event} — key, type and values, read off the topic.
 *
 * <p>Backlog item 46, and this is the only test that can see the failure the migration is exposed to. The unit tests
 * mock {@code StreamBridge}, so nothing is serialized and the key expression is never evaluated;
 * {@code EntityEventBindingIT} reads configuration, which can be perfectly correct while a send throws; and this
 * publisher swallows its failures on a thread of its own, so a send that fails every time looks exactly like one that
 * works. The binding's {@code key.serializer} has already cost this repo every event on {@code patient-events} once
 * while every test passed.</p>
 *
 * <p><strong>The key is the assertion that matters most here.</strong> A command is keyed on the patient and a
 * notification on the record, through <em>one</em> {@code messageKeyExpression} — so the two families' keys are produced
 * by the same line of configuration and only a frame on the wire can show that both come out right.
 * {@link EntityEventRoundTripIT} holds the notification half.</p>
 */
@IntegrationTest
class PatientCommandEventRoundTripIT {

    private static final String TOPIC = "patient.event";

    @Autowired
    private EntityEventPublisher publisher;

    @Autowired
    private Environment environment;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void aCareDelegationChangeReachesTheTopicUnderThePatientsKey() throws Exception {
        String brokers = environment.getRequiredProperty("spring.cloud.stream.kafka.binder.brokers");
        String delegationId = "delegation-" + UUID.randomUUID();

        publisher.publishCareDelegationChanged(delegationId, "REVOKED_BY_ANGEL", "Round.Trip@Example.Test", "angel@example.test");

        try (KafkaConsumer<String, String> consumer = consumer(brokers)) {
            consumer.subscribe(List.of(TOPIC));

            ConsumerRecord<String, String> received = pollFor(consumer, delegationId);
            assertThat(received).as("nothing arrived on %s within the timeout", TOPIC).isNotNull();

            // Per-patient ordering, which is what the mail router depends on and what patient-events gives it today.
            assertThat(received.key()).as("a command's partition key is the lowercased patient email").isEqualTo("round.trip@example.test");

            JsonNode frame = objectMapper.readTree(received.value());
            assertThat(frame.path("type").asText()).isEqualTo(PatientEventType.CARE_DELEGATION_CHANGED);
            assertThat(frame.path("source").asText()).isEqualTo("hcPatientService");
            assertThat(frame.path("eventId").asText()).isNotBlank();
            assertThat(frame.path("occurredAt").isMissingNode()).isFalse();

            // The subject is the record in both families — hc-admin item 124. A PatientEvent-shaped subject here would
            // put two subject meanings under one topic name, which is the divergence the estate has just removed.
            JsonNode subject = frame.path("subject");
            assertThat(fieldNames(subject)).containsExactlyInAnyOrder("entityType", "entityId");
            assertThat(subject.path("entityType").asText()).isEqualTo("CareDelegation");
            assertThat(subject.path("entityId").asText()).isEqualTo(delegationId);

            // Everything the gateway's CareDelegationMailer reads, asserted against the bytes rather than the object.
            JsonNode data = frame.path("data");
            assertThat(data.path("change").asText()).isEqualTo("REVOKED_BY_ANGEL");
            assertThat(data.path("patientEmail").asText()).isEqualTo("round.trip@example.test");
            assertThat(data.path("angelEmail").asText()).isEqualTo("angel@example.test");
            assertThat(fieldNames(data)).containsExactlyInAnyOrder("change", "patientEmail", "angelEmail");
        }
    }

    @Test
    void aDeletionRequestChangeReachesTheTopicWithItsDueDate() throws Exception {
        String brokers = environment.getRequiredProperty("spring.cloud.stream.kafka.binder.brokers");
        String requestId = "request-" + UUID.randomUUID();
        Instant due = Instant.parse("2026-10-09T10:00:00Z");

        publisher.publishDeletionRequestChanged(requestId, "COMPLETED", "Round.Trip@Example.Test", due);

        try (KafkaConsumer<String, String> consumer = consumer(brokers)) {
            consumer.subscribe(List.of(TOPIC));

            ConsumerRecord<String, String> received = pollFor(consumer, requestId);
            assertThat(received).as("nothing arrived on %s within the timeout", TOPIC).isNotNull();
            assertThat(received.key()).isEqualTo("round.trip@example.test");

            JsonNode frame = objectMapper.readTree(received.value());
            assertThat(frame.path("type").asText()).isEqualTo(PatientEventType.DELETION_REQUEST_CHANGED);
            assertThat(frame.path("subject").path("entityType").asText()).isEqualTo("DeletionRequest");
            assertThat(frame.path("subject").path("entityId").asText()).isEqualTo(requestId);

            JsonNode data = frame.path("data");
            // COMPLETED is the frame DeletionAccountCloser acts on, and dueAt is what DeletionRequestMailer formats.
            assertThat(data.path("change").asText()).isEqualTo("COMPLETED");
            assertThat(data.path("dueAt").asText()).isEqualTo("2026-10-09T10:00:00Z");
            assertThat(data.path("patientEmail").asText()).isEqualTo("round.trip@example.test");
            assertThat(fieldNames(data)).containsExactlyInAnyOrder("change", "patientEmail", "dueAt");
        }
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    /** Reads until this test's own frame shows up, ignoring the entity changes every other test leaves on the topic. */
    private static ConsumerRecord<String, String> pollFor(KafkaConsumer<String, String> consumer, String entityId) {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
            for (ConsumerRecord<String, String> record : records) {
                if (record.value() != null && record.value().contains(entityId)) {
                    return record;
                }
            }
        }
        return null;
    }

    private static KafkaConsumer<String, String> consumer(String brokers) {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", brokers);
        // Published first, then read from the beginning under a group nobody else uses — subscribing first and seeking
        // to the end races both the topic's auto-creation and the consumer's first rebalance.
        properties.put("group.id", "patient-command-round-trip-" + UUID.randomUUID());
        properties.put("auto.offset.reset", "earliest");
        properties.put("key.deserializer", StringDeserializer.class.getName());
        properties.put("value.deserializer", StringDeserializer.class.getName());
        return new KafkaConsumer<>(properties);
    }
}
