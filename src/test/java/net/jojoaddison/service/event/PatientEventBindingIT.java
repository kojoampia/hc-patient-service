package net.jojoaddison.service.event;

import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.core.env.Environment;

/**
 * That the event stream is wired to the topic it is supposed to be wired to.
 *
 * <p>Worth its own test because the failure it catches is silent. {@code StreamBridge.send} creates a destination on
 * the fly for a binding name it does not recognise, so a typo in either the binding name or the YAML produces a
 * working publisher writing to the wrong topic — every test still passes, and the events simply never arrive where
 * anybody is looking for them. The unit tests mock {@code StreamBridge} and cannot see any of this.</p>
 *
 * <p>The partition key matters just as much. Without {@code messageKeyExpression} the events scatter across
 * partitions, and "what happened to this patient, in what order" — the only question the stream is really for — stops
 * being answerable. Nothing about that failure is visible from a single event.</p>
 */
@IntegrationTest
class PatientEventBindingIT {

    @Autowired
    private BindingServiceProperties bindingServiceProperties;

    @Autowired
    private Environment environment;

    @Test
    void theProducerBindingPointsAtPatientEvents() {
        assertThat(bindingServiceProperties.getBindingDestination(PatientEventPublisher.BINDING))
            .as("the binding name in code and the one in application.yml must be the same string")
            .isEqualTo("patient-events");
    }

    @Test
    void everyEventAboutOnePatientLandsOnOnePartition() {
        String keyExpression = environment.getProperty(
            "spring.cloud.stream.kafka.bindings." + PatientEventPublisher.BINDING + ".producer.messageKeyExpression"
        );

        assertThat(keyExpression)
            .as("no messageKeyExpression for %s — events would scatter across partitions", PatientEventPublisher.BINDING)
            .isNotNull();
        assertThat(keyExpression)
            .as("the key expression must read the header the publisher sets")
            .contains(PatientEventPublisher.KEY_HEADER);
    }
}
