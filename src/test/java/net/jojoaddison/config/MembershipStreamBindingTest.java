package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * The shape of the membership-stream bindings, read out of the YAML rather than out of a running context.
 *
 * <h2>Why a file test and not a binding test</h2>
 *
 * <p><strong>The property that matters most here is an absence, and a running context cannot show one.</strong> Every
 * other consumer binding in this service names the group {@code hc-patient-service}, so that one deployment consumes
 * each topic once. {@code membershipStreamEvents-in-0} must have <em>no</em> group: the work is "tell the browsers
 * attached to you", and a browser is attached to exactly one instance. Add a group and a frame goes to one instance
 * while the patients connected to the others are never told — intermittently, in proportion to the replica count, with
 * nothing failing and no test able to see it, because production runs one container and a test runs one context.</p>
 *
 * <p>That is the same argument {@code GatewayRoutePolicyTest} makes in the gateway, and hc-admin's lesson behind it: a
 * rule's absence is invisible to a behavioural test whenever the remaining behaviour decides the same way.</p>
 *
 * <h2>And the mirroring trap</h2>
 *
 * <p>{@code src/test/resources/config/application.yml} is the <em>same classpath resource</em> as the main one and
 * replaces it wholesale rather than merging, so a binding configured only in main is configured for production and for
 * nothing any test can see. That has already cost this repository three defects with a green suite. The two files are
 * required to agree on the destination here so that a fourth is caught by a unit test rather than by a patient.</p>
 */
class MembershipStreamBindingTest {

    private static final Path MAIN = Path.of("src", "main", "resources", "config", "application.yml");
    private static final Path TEST = Path.of("src", "test", "resources", "config", "application.yml");

    private static final String DESTINATION = "patient-membership-events";
    private static final String OUT = "membershipEvents-out-0";
    private static final String IN = "membershipStreamEvents-in-0";

    @Test
    void theConsumerBindingHasNoGroupSoEveryInstanceReceivesEveryFrame() {
        Map<String, Object> binding = binding(MAIN, IN);

        assertThat(binding)
            .as(
                "membershipStreamEvents-in-0 must NOT name a consumer group. A group makes replicas share the work, " +
                "and here the work is pushing to browsers that are attached to one instance each — so a group means " +
                "the patients connected to every other instance are silently never told. See MembershipStreamConsumer."
            )
            .doesNotContainKey("group");
    }

    @Test
    void everyOtherConsumerBindingStillHasOne() {
        // The rule above is a deliberate exception, and it only reads as one while the others are the other way. If
        // this ever fails, the exception has become the convention and the comment in application.yml is misleading.
        assertThat(binding(MAIN, "patientPlanEventsConsumer-in-0")).containsEntry("group", "hc-patient-service");
    }

    @Test
    void bothEndsPointAtTheSameTopicInBothFiles() {
        assertThat(binding(MAIN, OUT)).containsEntry("destination", DESTINATION);
        assertThat(binding(MAIN, IN)).containsEntry("destination", DESTINATION);
        // The test resource REPLACES the main one rather than merging with it. A destination that differs here is a
        // suite exercising a topic production does not use.
        assertThat(binding(TEST, OUT)).containsEntry("destination", DESTINATION);
        assertThat(binding(TEST, IN)).containsEntry("destination", DESTINATION);
    }

    @Test
    void theFunctionIsDeclaredInBothFilesOrTheConsumerNeverBinds() {
        // spring.cloud.function.definition names the @Bean method, and a missing name does not fail to start: the
        // context comes up, every request is served, and no patient is ever pushed anything again.
        assertThat(definition(MAIN)).contains("membershipStreamEvents");
        assertThat(definition(TEST)).contains("membershipStreamEvents");
    }

    @Test
    void productionStartsAtLatestSoARestartDoesNotReplayHistoryAtWhoeverConnectsFirst() {
        // The binding has no group, so the anonymous one is new on every start and `earliest` would push every status
        // change the topic still holds at the first browser to connect. The test config deliberately says `earliest`
        // for its own reasons — see the comment there — which is why this reads the main file.
        assertThat(kafkaConsumer(MAIN, IN)).containsEntry("startOffset", "latest");
    }

    /** A parser that silently matches nothing passes for ever. */
    @Test
    void theSweepFindsTheBindingsItIsChecking() {
        assertThat(binding(MAIN, IN)).isNotEmpty();
        assertThat(binding(TEST, IN)).isNotEmpty();
        assertThat(kafkaConsumer(MAIN, IN)).isNotEmpty();
    }

    /** {@code spring.cloud.stream.bindings.<name>} */
    private static Map<String, Object> binding(Path file, String name) {
        return child(path(file, "spring", "cloud", "stream", "bindings"), name);
    }

    /** {@code spring.cloud.stream.kafka.bindings.<name>.consumer} */
    private static Map<String, Object> kafkaConsumer(Path file, String name) {
        return child(child(path(file, "spring", "cloud", "stream", "kafka", "bindings"), name), "consumer");
    }

    private static String definition(Path file) {
        return String.valueOf(path(file, "spring", "cloud", "function").get("definition"));
    }

    /**
     * Walks a key path through whichever YAML document in the file carries it.
     *
     * <p>{@code application.yml} is several documents separated by {@code ---}, so a single {@code load} sees only the
     * first and would report every key missing.
     */
    private static Map<String, Object> path(Path file, String... keys) {
        assertThat(file).as("run from the module directory: %s", file.toAbsolutePath()).isRegularFile();
        for (Object document : documents(file)) {
            Map<String, Object> node = asMap(document);
            for (String key : keys) {
                node = child(node, key);
            }
            if (!node.isEmpty()) {
                return node;
            }
        }
        return Map.of();
    }

    private static Iterable<Object> documents(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            // Materialised inside the try: loadAll is lazy and would read from a closed stream otherwise.
            java.util.List<Object> all = new java.util.ArrayList<>();
            new Yaml().loadAll(in).forEach(all::add);
            return all;
        } catch (IOException e) {
            throw new IllegalStateException("could not read " + file, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static Map<String, Object> child(Map<String, Object> node, String key) {
        return asMap(node.get(key));
    }
}
