package net.jojoaddison.service.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.jojoaddison.IntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/**
 * That an event actually reaches the topic, key and all.
 *
 * <p>This exists because neither of the other two kinds of test can see the failure it catches. The unit tests mock
 * {@code StreamBridge}, so nothing is serialized. {@code PatientEventBindingIT} reads configuration, which was
 * correct. And the publisher swallows failures by design — deliberately, because a broker outage must not cost a
 * patient their onboarding — so a send that throws every single time looks exactly like a send that works.</p>
 *
 * <p>It caught precisely that: {@code messageKeyExpression} produces a String key while the binder's default key
 * serializer is {@code ByteArraySerializer}, which fails at send time rather than at startup. Every event was being
 * lost and every test was passing.</p>
 */
@IntegrationTest
class PatientEventRoundTripIT {

    @Autowired
    private PatientEventPublisher publisher;

    @Autowired
    private Environment environment;

    @Test
    void anEventReachesTheTopicUnderThePatientsKey() {
        String brokers = environment.getRequiredProperty("spring.cloud.stream.kafka.binder.brokers");

        publisher.publish(
            PatientEventType.ONBOARDING_STARTED,
            "Round.Trip@Example.Test",
            "roundtrip",
            "patient-round-trip",
            Map.of("startedAt", "2026-08-19T10:00:00Z")
        );

        // Published first, then read from the beginning under a group nobody else uses. Subscribing first and seeking
        // to the end races both the topic's auto-creation and the consumer's first rebalance, which is a flaky test
        // rather than a strict one.
        try (KafkaConsumer<String, String> consumer = consumer(brokers)) {
            consumer.subscribe(List.of("patient-events"));

            ConsumerRecord<String, String> received = pollFor(consumer, "patient-round-trip");
            assertThat(received).as("nothing arrived on patient-events within the timeout").isNotNull();
            assertThat(received.key()).as("the partition key is the lowercased email").isEqualTo("round.trip@example.test");
            assertThat(received.value()).contains(PatientEventType.ONBOARDING_STARTED).contains("patient-round-trip");
        }
    }

    /** Reads until the event this test published shows up, ignoring anything another test left on the topic. */
    private static ConsumerRecord<String, String> pollFor(KafkaConsumer<String, String> consumer, String patientId) {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
            for (ConsumerRecord<String, String> record : records) {
                if (record.value() != null && record.value().contains(patientId)) {
                    return record;
                }
            }
        }
        return null;
    }

    private static KafkaConsumer<String, String> consumer(String brokers) {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", brokers);
        properties.put("group.id", "patient-events-round-trip-" + java.util.UUID.randomUUID());
        properties.put("auto.offset.reset", "earliest");
        properties.put("key.deserializer", StringDeserializer.class.getName());
        properties.put("value.deserializer", StringDeserializer.class.getName());
        return new KafkaConsumer<>(properties);
    }
}
