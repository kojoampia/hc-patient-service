package net.jojoaddison.service.event;

import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * The instance-local end of the membership fan-out: everything on {@code patient-membership-events} arrives here and is
 * offered to the streams this JVM is holding open.
 *
 * <h2>This binding has no consumer group, and that is the whole design</h2>
 *
 * <p><strong>Every other binding in this service names {@code hc-patient-service} deliberately, so that one deployment
 * consumes each topic once. This one must do the opposite.</strong> A group is what makes replicas share the work; here
 * the work is "tell the browsers attached to <em>you</em>", and a browser is attached to exactly one instance. With a
 * group, a frame would be handed to one instance and the patients connected to the others would never hear about their
 * own membership — intermittently, in proportion to the replica count, and with nothing failing.</p>
 *
 * <p>Left without a group, Spring Cloud Stream gives the binding an anonymous group that is unique per instance and per
 * start, which is the pub/sub semantics this needs. It also means offsets are never committed and nothing is replayed
 * on restart — correct here: the stream deliberately does not replay, and a client that was disconnected re-fetches on
 * reconnect rather than being told what it missed (backlog item 39). {@code startOffset: latest} says the same thing
 * explicitly in {@code application.yml}, because "an anonymous group defaults to latest" is a binder detail and this
 * depends on it.</p>
 *
 * <h2>The method name IS the binding name</h2>
 *
 * <p>Spring Cloud Stream derives {@code membershipStreamEvents-in-0} from the {@code @Bean} method, and
 * {@code spring.cloud.function.definition} names it in YAML — so renaming the method does not fail to compile and does
 * not fail to start. The context comes up, every request is served, and no patient is ever pushed anything again.
 * {@code MembershipStreamRoundTripIT} is what can see that; the same trap and the same remedy as
 * {@link PlanVerificationConsumer}.</p>
 *
 * <p>And the {@code @Bean} method is deliberately not named after this class. Spring derives the bean name
 * {@code membershipStreamConsumer} for the component itself, and a {@code @Bean} method sharing its own class's bean
 * name is a factory-bean reference pointing at itself — the context refuses to start.</p>
 *
 * <h2>Nothing here refuses a frame</h2>
 *
 * <p>Unlike {@link PlanVerificationConsumer}, which dead-letters twelve ways an acknowledgement can be wrong, this
 * consumer throws nothing. The two topics are not comparable: that one carries another product's decision and dropping
 * it loses a patient their plan, while this one carries a notice whose worst failure is a browser that refreshes a
 * little later than it could have. There is no dead-letter queue on this binding for the same reason — a queue of
 * missed keep-alives is not a thing anybody would read.</p>
 */
@Component
public class MembershipStreamConsumer {

    private final Logger log = LoggerFactory.getLogger(MembershipStreamConsumer.class);

    private final MembershipStreamRegistry registry;

    public MembershipStreamConsumer(MembershipStreamRegistry registry) {
        this.registry = registry;
    }

    @Bean
    public Consumer<MembershipChangedEvent> membershipStreamEvents() {
        return this::apply;
    }

    void apply(MembershipChangedEvent event) {
        if (event == null || event.patientId() == null) {
            // The binder does not hand a function a null payload, so this is defensive. An unaddressed frame cannot be
            // delivered to anybody — Visibility.allows would refuse it for every patient and accept it for every
            // administrator, which is the one reading that must not happen by accident.
            log.warn("Discarding a membership frame with no patient to deliver it to");
            return;
        }
        registry.deliver(event);
    }
}
