package net.jojoaddison.service.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Task;
import net.jojoaddison.repository.TaskRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/**
 * That an ordinary repository save really does put a frame on {@code patient.event} — and that a delete does too.
 *
 * <p>Backlog item 45. This is the only test in the repository that exercises the whole chain: the persistence
 * listener being registered at all, the action being resolved, the envelope serializing, the key serializer matching
 * the key, and the frame reaching the topic. <strong>Every other test here can pass while nothing is published.</strong>
 * The unit tests mock {@code StreamBridge}, so nothing is serialized and the listener is never involved;
 * {@code EntityEventBindingIT} reads configuration, which can be perfectly correct while no listener exists; and the
 * publisher swallows its failures on a thread of its own, so a send that throws every time looks exactly like one
 * that works.</p>
 *
 * <p>It saves through {@link TaskRepository} rather than calling the publisher, which is the point: the claim being
 * tested is "an entity change produces a frame", and a test that publishes directly proves only that the publisher
 * publishes.</p>
 */
@IntegrationTest
class EntityEventRoundTripIT {

    private static final String TOPIC = "patient.event";

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private Environment environment;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void savingADocumentPutsOneAuditableFrameOnTheTopic() throws Exception {
        String brokers = environment.getRequiredProperty("spring.cloud.stream.kafka.binder.brokers");

        Task task = new Task();
        task.setName("round trip " + UUID.randomUUID());
        Task saved = taskRepository.save(task);
        String id = saved.getId();
        assertThat(id).isNotNull();

        try (KafkaConsumer<String, String> consumer = consumer(brokers)) {
            consumer.subscribe(List.of(TOPIC));

            ConsumerRecord<String, String> created = pollFor(consumer, id, "CREATED");
            assertThat(created).as("nothing arrived on %s within the timeout — is the listener registered?", TOPIC).isNotNull();

            // The partition key is the entity, so a document's own history stays ordered.
            assertThat(created.key()).as("the partition key is the entity id").isEqualTo(id);

            JsonNode frame = objectMapper.readTree(created.value());
            assertThat(frame.path("type").asText()).isEqualTo(EntityEvent.TYPE);
            assertThat(frame.path("source").asText()).isEqualTo("hcPatientService");
            assertThat(frame.path("eventId").asText()).isNotBlank();
            assertThat(frame.path("occurredAt").isMissingNode()).isFalse();

            // hc-admin item 124: the subject is the record. Asserted against the bytes because the estate's
            // disagreement was about what a consumer finds in the frame, not about what this service's objects hold.
            JsonNode subject = frame.path("subject");
            assertThat(subject.path("entityType").asText()).isEqualTo("Task");
            assertThat(subject.path("entityId").asText()).isEqualTo(id);
            assertThat(fieldNames(subject))
                .as("subject is { entityType, entityId } — never the actor, which is hc-patient's own old shape")
                .containsExactlyInAnyOrder("entityType", "entityId");

            JsonNode data = frame.path("data");
            assertThat(data.path("action").asText()).isEqualTo("CREATED");
            // No authenticated caller behind a repository save in a test context, so the actor is present and null
            // rather than absent — the shape a consumer can rely on.
            assertThat(data.path("actorAccountId").isNull()).as("an unnameable actor is an explicit null").isTrue();

            // ⛔ The rule most easily lost later, asserted against the bytes that actually go on the wire rather than
            // against the object. The name was written into the document and must not be anywhere in the frame.
            assertThat(fieldNames(data))
                .as("a frame may carry identifiers and metadata only — never what the record now holds")
                .containsExactlyInAnyOrder("action", "actorAccountId");
            assertThat(created.value()).as("the document's own field values must not reach the topic").doesNotContain(task.getName());
        }
    }

    @Test
    void deletingADocumentPutsAFrameOnTheTopicToo() throws Exception {
        // Deletes are the writes instrumentation misses. An audit trail that records every creation and no removal
        // asserts that everything ever created still exists.
        String brokers = environment.getRequiredProperty("spring.cloud.stream.kafka.binder.brokers");

        Task task = new Task();
        task.setName("round trip delete " + UUID.randomUUID());
        String id = taskRepository.save(task).getId();
        taskRepository.deleteById(id);

        try (KafkaConsumer<String, String> consumer = consumer(brokers)) {
            consumer.subscribe(List.of(TOPIC));

            ConsumerRecord<String, String> deleted = pollFor(consumer, id, "DELETED");
            assertThat(deleted).as("a delete produced no frame on %s", TOPIC).isNotNull();

            JsonNode frame = objectMapper.readTree(deleted.value());
            assertThat(frame.path("subject").path("entityType").asText()).isEqualTo("Task");
            assertThat(frame.path("subject").path("entityId").asText()).isEqualTo(id);
            assertThat(frame.path("data").path("action").asText()).isEqualTo("DELETED");
            assertThat(fieldNames(frame.path("data"))).containsExactlyInAnyOrder("action", "actorAccountId");
        }
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    /**
     * Reads until this test's own frame shows up, ignoring everything else on the topic.
     *
     * <p>Matching on the id <em>and</em> the action, because a save and a delete of one document both carry the id
     * and this suite asserts about each separately.</p>
     */
    private static ConsumerRecord<String, String> pollFor(KafkaConsumer<String, String> consumer, String entityId, String action) {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
            for (ConsumerRecord<String, String> record : records) {
                if (record.value() != null && record.value().contains(entityId) && record.value().contains(action)) {
                    return record;
                }
            }
        }
        return null;
    }

    private static KafkaConsumer<String, String> consumer(String brokers) {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", brokers);
        // Published first, then read from the beginning under a group nobody else uses — subscribing first and
        // seeking to the end races both the topic's auto-creation and the consumer's first rebalance.
        properties.put("group.id", "patient-event-round-trip-" + UUID.randomUUID());
        properties.put("auto.offset.reset", "earliest");
        properties.put("key.deserializer", StringDeserializer.class.getName());
        properties.put("value.deserializer", StringDeserializer.class.getName());
        return new KafkaConsumer<>(properties);
    }
}
