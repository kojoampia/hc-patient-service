package net.jojoaddison.service.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
import org.yaml.snakeyaml.Yaml;

/**
 * That the inbound consumer is bound to hc-admin's channel, under a group, with somewhere for a poison frame to go —
 * and that nothing is still bound to the topic backlog item 47 retired.
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
 * <p><b>Item 47 added the negative half, and it is the one this file exists for now.</b> The migration from
 * {@code patient-events-plan} to {@code admin.event} is four strings in two YAML files plus a {@code @Bean} method
 * name, and every partial version of it fails silently: a destination changed in main but not in test leaves the suite
 * exercising a topic production no longer reads, and a {@code definition} left on the old name leaves a bean nothing
 * binds. The old destination must therefore be <em>absent</em>, asserted by reading both files rather than by asking a
 * context what it bound — a context can only be asked about bindings that exist.</p>
 *
 * <p>Backlog items 19 and 47.</p>
 */
@IntegrationTest
class PlanVerificationConsumerBindingIT {

    /**
     * Every string here is a literal on purpose. The binding name is derived from the {@code @Bean} method, the
     * destination is a four-product agreement, and the DLQ name is the topic an operator would go looking in. Written
     * through constants — {@code PlanVerificationConsumer.CHANNEL}, say — a rename would keep this test green while the
     * thing it names stopped existing.
     */
    private static final String BINDING = "adminEventConsumer-in-0";

    private static final String DESTINATION = "admin.event";

    private static final String DLQ = "admin.event.hc-patient-dlq";

    /** The topic item 47 removed. Named here so that "it is gone" is something a test can say. */
    private static final String RETIRED_DESTINATION = "patient-events-plan";

    private static final Path MAIN_CONFIG = Path.of("src", "main", "resources", "config", "application.yml");

    private static final Path TEST_CONFIG = Path.of("src", "test", "resources", "config", "application.yml");

    @Autowired
    private BindingServiceProperties bindingServiceProperties;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Environment environment;

    @Test
    void theConsumerFunctionExistsUnderTheNameTheConfigurationNames() {
        // `adminEventConsumer` is listed in spring.cloud.function.definition. A bean by any other name is not bound,
        // however correct the class behind it is.
        assertThat(context.containsBean("adminEventConsumer"))
            .as("spring.cloud.function.definition names adminEventConsumer; the @Bean method must keep that name")
            .isTrue();

        assertThat(context.getBean("adminEventConsumer")).isInstanceOf(Consumer.class);

        // And the name item 47 renamed away from is gone. A @Bean method left behind under the old name would bind a
        // second consumer to nothing at all, which is the failure this whole class exists for, spelled backwards.
        assertThat(context.containsBean("patientPlanEventsConsumer"))
            .as("the pre-item-47 bean name is still registered — two names for one consumer, one of them bound to nothing")
            .isFalse();
    }

    @Test
    void itIsBoundToTheChannelHcAdminPublishesOn() {
        assertThat(bindingServiceProperties.getBindingDestination(BINDING))
            .as("the destination is a four-product agreement — hc-admin's ConfigurationBindingTest pins the same literal")
            .isEqualTo(DESTINATION);
    }

    /**
     * ⭐ <b>That the retired topic appears in no configuration value, in either file.</b>
     *
     * <p>Read out of the YAML rather than out of the context, because <b>the property that matters here is an absence
     * and a running context cannot show one</b>: ask it for a binding on the old destination and it reports nothing
     * whether the binding was removed or never existed. {@code MembershipStreamBindingTest} makes the same argument for
     * a missing consumer group.</p>
     *
     * <p>Both files, because the test resource REPLACES the main one wholesale. A half-done migration that left the
     * suite on the old topic would have every other test in this class pass — which is item 32 exactly: a binding
     * exercised everywhere except where it had to work.</p>
     *
     * <p>It sweeps every scalar <b>value</b> in both files rather than naming the two paths a destination and a DLQ name
     * live at, for two reasons. Comments are excluded for free — both files deliberately keep the old topic's name in
     * prose, because a reader arriving at this binding needs to know it moved — and a value planted at a path this test
     * did not think of is caught rather than missed.</p>
     */
    @Test
    void theRetiredTopicAppearsInNoConfigurationValueInEitherFile() {
        for (Path config : List.of(MAIN_CONFIG, TEST_CONFIG)) {
            assertThat(configuredValues(config))
                .as(
                    "%s still configures %s somewhere — item 47 moved this consumer to %s, and hc-admin removes their " +
                    "producer only once our binding is gone and our lag on the old topic is zero",
                    config,
                    RETIRED_DESTINATION,
                    DESTINATION
                )
                .noneMatch(value -> value.contains(RETIRED_DESTINATION));
        }
    }

    /** A sweep that silently reads nothing passes for ever. */
    @Test
    void theSweepReadsTheFilesItIsChecking() {
        for (Path config : List.of(MAIN_CONFIG, TEST_CONFIG)) {
            assertThat(configuredValues(config))
                .as("%s: the YAML sweep found no destination at all, so its absence assertion means nothing", config)
                .contains(DESTINATION, DLQ);
        }
    }

    /**
     * Every scalar value in every document of one YAML file.
     *
     * <p>{@code application.yml} is several documents separated by {@code ---}, so a single {@code load} sees only the
     * first — the trap {@code MembershipStreamBindingTest} records at length. Here every document is swept and the
     * results merged, which is the right reading for an assertion about absence: a value that appears in <em>any</em>
     * document is a value that could be the one the runtime uses.</p>
     */
    private static List<String> configuredValues(Path file) {
        assertThat(file).as("run from the module directory: %s", file.toAbsolutePath()).isRegularFile();
        List<String> values = new ArrayList<>();
        try (InputStream in = Files.newInputStream(file)) {
            // Materialised inside the try: loadAll is lazy and would read from a closed stream otherwise.
            List<Object> documents = new ArrayList<>();
            new Yaml().loadAll(in).forEach(documents::add);
            documents.forEach(document -> collect(document, values));
        } catch (IOException e) {
            throw new IllegalStateException("could not read " + file, e);
        }
        return values;
    }

    private static void collect(Object node, List<String> into) {
        if (node instanceof Map<?, ?> map) {
            map.values().forEach(value -> collect(value, into));
        } else if (node instanceof Iterable<?> items) {
            items.forEach(item -> collect(item, into));
        } else if (node != null) {
            into.add(String.valueOf(node));
        }
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
        //
        // CAVEAT, because this assertion is weaker where it is usually run than where it gates. With
        // TESTCONTAINERS_REUSE_ENABLE=true — which this repo's own guide recommends for local runs — the broker
        // survives between runs, so a topic an EARLIER run provisioned is still here and this would pass even if the
        // binder had stopped creating it. In CI, where reuse is deliberately off, the broker is new and the
        // assertion is exact. Read a local pass as "still configured"; read a CI pass as "the binder read it".
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
