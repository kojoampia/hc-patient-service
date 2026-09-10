package net.jojoaddison.service.event;

import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * The one consumer bound to {@code patient-events-plan} — hc-admin's acknowledgement that an administrator decided a
 * patient's plan choice.
 *
 * <h2>The first topic this service consumes that it does not own</h2>
 *
 * <p>Everything else here <em>publishes</em>: {@code patient-events} is this subsystem's own stream and both its
 * producers are ours. This is the return leg of the exchange item 18 opened — a patient chooses a tier, this service
 * writes a {@code PENDING} {@link net.jojoaddison.domain.Membership} and announces it, an administrator decides on it
 * next door, and the decision comes back here. Backlog item 19.</p>
 *
 * <p><b>The method name IS the binding name.</b> Spring Cloud Stream derives {@code patientPlanEventsConsumer-in-0}
 * from the {@code @Bean} method, and {@code spring.cloud.function.definition} names it in YAML — so renaming the
 * method does not fail to compile and does not fail to start. The context comes up, every request is served, and
 * every acknowledgement hc-admin sends is silently never applied. {@code PlanVerificationConsumerBindingIT} is the
 * only thing that can see that, and it is copied from the gateway's {@code PatientEventConsumerBindingIT} for exactly
 * that reason.</p>
 *
 * <p>And this class is deliberately not called {@code PatientPlanEventsConsumer}: Spring would derive that same bean
 * name for the component itself, and a {@code @Bean} method sharing its own class's bean name is a factory-bean
 * reference pointing at itself. The context refuses to start — the gateway's {@code PatientEventMailRouter} records
 * the same trap.</p>
 *
 * <h2>It idles, and that is the design rather than a defect</h2>
 *
 * <p><b>hc-admin publishes nothing on this topic today</b> — their item 54 is unbuilt, and {@code patient-events-plan}
 * appears in no file of any of their five repositories (measured, not assumed). Item 19 chose this order on purpose:
 * a consumer nobody publishes to idles, loses nothing and drains the backlog the day the producer starts, whereas the
 * opposite experiment — item 18's publish-first — spent a fortnight as a half-loop. Do not add a producer here to
 * make it look alive; the only thing that writes to this topic is hc-admin, and in tests, a raw Kafka producer in the
 * test itself.</p>
 */
@Component
public class PlanVerificationConsumer {

    private final Logger log = LoggerFactory.getLogger(PlanVerificationConsumer.class);

    @Bean
    public Consumer<PatientEvent> patientPlanEventsConsumer() {
        return this::apply;
    }

    /**
     * Applies one acknowledgement.
     *
     * <p>The rules are backlog item 19's and land with the commit after this one; this commit is the binding, the
     * consumer group and the dead-letter queue, which are the parts that are configuration rather than behaviour and
     * are wrong in ways no unit test can see.</p>
     */
    void apply(PatientEvent event) {
        log.info("Received a plan acknowledgement on patient-events-plan: {}", event == null ? null : event.eventId());
    }
}
