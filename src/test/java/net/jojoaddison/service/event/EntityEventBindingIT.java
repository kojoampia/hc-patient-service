package net.jojoaddison.service.event;

import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.core.env.Environment;

/**
 * That the entity-change stream is wired to the topic the estate agreed on.
 *
 * <p>Backlog item 45. Worth its own test for the reason {@code PatientEventBindingIT} gives and one more.
 * {@code StreamBridge.send} creates a destination on the fly for a binding name it does not recognise, so a typo in
 * either the binding name or the YAML produces a working publisher writing to a topic nobody reads — every test
 * passes and the frames simply never arrive. hc-admin shipped exactly that with {@code roster-events}.</p>
 *
 * <p><strong>The destination is asserted as a literal, deliberately.</strong> {@code patient.event} is a
 * cross-product contract: hc-admin's consumer names this string, and a misspelling here is a real topic that is
 * silent on both sides at once — a producer writing where nobody reads looks exactly like a healthy producer. An
 * internal name could be derived; a name another product depends on earns an enumeration.</p>
 *
 * <p>⚠ It also asserts that {@code patient-events} is <em>still</em> where it was. Item 45 is an add: the lifecycle
 * stream keeps its consumer in hc-admin and its mail router in this subsystem's gateway, and retiring it is item 46.
 * A change that quietly repointed the old binding at the new topic would pass every other test in this repository.</p>
 */
@IntegrationTest
class EntityEventBindingIT {

    @Autowired
    private BindingServiceProperties bindingServiceProperties;

    @Autowired
    private Environment environment;

    @Test
    void theProducerBindingPointsAtPatientEvent() {
        assertThat(bindingServiceProperties.getBindingDestination(EntityEventPublisher.BINDING))
            .as("the binding name in code and the one in application.yml must be the same string")
            .isEqualTo("patient.event");
    }

    @Test
    void theLifecycleStreamIsUntouched() {
        assertThat(bindingServiceProperties.getBindingDestination(PatientEventPublisher.BINDING))
            .as("item 45 adds a topic; it does not repoint patient-events, which hc-admin and our own gateway read")
            .isEqualTo("patient-events");
    }

    @Test
    void everyChangeToOneDocumentLandsOnOnePartition() {
        String keyExpression = environment.getProperty(
            "spring.cloud.stream.kafka.bindings." + EntityEventPublisher.BINDING + ".producer.messageKeyExpression"
        );

        assertThat(keyExpression)
            .as("no messageKeyExpression for %s — a create and a delete could be audited out of order", EntityEventPublisher.BINDING)
            .isNotNull();
        assertThat(keyExpression)
            .as("the key expression must read the header the publisher sets")
            .contains(EntityEventPublisher.KEY_HEADER);
    }

    @Test
    void theKeySerializerMatchesTheKeyTheExpressionProduces() {
        // The failure this repo has already paid for once, on the binding next door: messageKeyExpression yields a
        // String, the binder's default key serializer is ByteArraySerializer, and the mismatch throws at SEND time.
        // Here that is doubly silent — the publisher swallows failures AND sends on a thread of its own.
        String keySerializer = environment.getProperty(
            "spring.cloud.stream.kafka.bindings." + EntityEventPublisher.BINDING + ".producer.configuration.key.serializer"
        );

        assertThat(keySerializer)
            .as("a String key needs StringSerializer, or every frame is lost at send time")
            .isEqualTo("org.apache.kafka.common.serialization.StringSerializer");
    }
}
