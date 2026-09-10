package net.jojoaddison.service.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.jojoaddison.IntegrationTest;
import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

/**
 * That the inbound consumer is bound to hc-admin's topic, under a group, with somewhere for a poison frame to go.
 *
 * <h2>Why configuration gets its own test here</h2>
 *
 * <p>Because every failure in it is silent. The binding name is derived from a {@code @Bean} method name and named
 * again in {@code spring.cloud.function.definition}: rename one and Spring Cloud Stream has no function to bind — the
 * context starts, the service serves, and every acknowledgement hc-admin sends is dropped on the floor with nothing
 * thrown and nothing logged at a level anybody watches. The gateway's {@code PatientEventConsumerBindingIT} was
 * written for the same failure on the outbound half of the same stream, and this is its counterpart.</p>
 *
 * <p><b>It asserts against the test configuration, which in this repository REPLACES
 * {@code src/main/resources/config/application.yml} wholesale rather than merging with it.</b> So "both files carry
 * this binding" is itself part of what is under test: a binding configured only in main is configured for production
 * and for nothing any test can see. That asymmetry has produced three separate defects here.</p>
 *
 * <p><b>The DLQ is asserted on the broker as well as in the YAML, and the second assertion is the one that means
 * something.</b> Reading a property back out of {@code Environment} proves only that this test and this repository's
 * configuration agree on a spelling — the sibling {@code PatientEventBindingIT} has the same limit, and under it a
 * property written at a path the binder never consults would read as configured for ever. The Kafka binder
 * provisions the dead-letter topic while binding the consumer, so the topic's existence on a real broker is evidence
 * the binder actually read the property. This repo's first DLQ is worth proving rather than declaring.</p>
 *
 * <p>Backlog item 19.</p>
 */
@IntegrationTest
class PlanVerificationConsumerBindingIT {

    /**
     * Every string here is a literal on purpose. The binding name is derived from the {@code @Bean} method, the
     * destination is a two-product agreement, and the DLQ name is the topic an operator would go looking in. Written
     * through constants, a rename would keep this test green while the thing it names stopped existing.
     */
    private static final String BINDING = "patientPlanEventsConsumer-in-0";

    private static final String DESTINATION = "patient-events-plan";

    private static final String DLQ = "patient-events-plan.hc-patient-dlq";

    @Autowired
    private BindingServiceProperties bindingServiceProperties;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Environment environment;

    @Test
    void theConsumerFunctionExistsUnderTheNameTheConfigurationNames() {
        // `patientPlanEventsConsumer` is listed in spring.cloud.function.definition. A bean by any other name is not
        // bound, however correct the class behind it is.
        assertThat(context.containsBean("patientPlanEventsConsumer"))
            .as("spring.cloud.function.definition names patientPlanEventsConsumer; the @Bean method must keep that name")
            .isTrue();

        assertThat(context.getBean("patientPlanEventsConsumer")).isInstanceOf(Consumer.class);
    }

    @Test
    void itIsBoundToTheTopicHcAdminPublishesOn() {
        assertThat(bindingServiceProperties.getBindingDestination(BINDING))
            .as("the destination is a two-product agreement — item 19 settled it, and a rename is a coordinated deploy")
            .isEqualTo(DESTINATION);
    }

    @Test
    void everyInstanceDoesNotGetItsOwnCopy() {
        // Without a consumer group each running replica receives every acknowledgement and each one attempts the same
        // PENDING → ACTIVE write. The group is what makes that one delivery across the deployment.
        assertThat(bindingServiceProperties.getBindingProperties(BINDING).getGroup())
            .as("a missing group turns every replica into a second writer racing the first")
            .isEqualTo("hc-patient-service");
    }

    @Test
    void aPoisonFrameHasSomewhereToGoThatIsNotTheHeadOfThePartition() throws Exception {
        // This repo's first DLQ. Without it the binder logs the failure and commits the offset, so a frame this
        // service cannot apply is discarded and an administrator's decision quietly never takes effect; with
        // autoCommitOnError turned off instead, the container redelivers the same poison for ever and every later
        // patient's verification queues behind it. Both are worse than keeping the bytes and moving on.
        String prefix = "spring.cloud.stream.kafka.bindings." + BINDING + ".consumer.";

        assertThat(environment.getProperty(prefix + "enableDlq", Boolean.class))
            .as("no DLQ configured: a frame that cannot be applied would be silently discarded")
            .isTrue();
        assertThat(environment.getProperty(prefix + "dlqName"))
            .as("the topic an operator reads after a refusal — <destination>.<who-dead-lettered-it>, as hc-admin names theirs")
            .isEqualTo(DLQ);

        // And that the binder read it. The dead-letter topic is provisioned while the consumer binds, so its absence
        // here would mean the two properties above are being written somewhere nothing consults.
        String brokers = environment.getRequiredProperty("spring.cloud.stream.kafka.binder.brokers");
        try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", brokers))) {
            Set<String> topics = admin.listTopics().names().get(Duration.ofSeconds(20).toSeconds(), java.util.concurrent.TimeUnit.SECONDS);

            assertThat(topics).as("the consumer never bound: hc-admin's topic does not exist on the broker").contains(DESTINATION);
            assertThat(topics)
                .as("enableDlq is set but the binder provisioned no dead-letter topic — check the property path")
                .contains(DLQ);
        }
    }
}
