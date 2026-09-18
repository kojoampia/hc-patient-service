package net.jojoaddison.service.event;

import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.security.PatientScope;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The parts of {@link MembershipStreamRegistry} that can be asserted without an HTTP connection: the heartbeat's
 * relationship to the proxy that made it necessary, and the bookkeeping that decides whether a stream is still there.
 *
 * <p>Delivery and filtering are deliberately <b>not</b> here. An {@code SseEmitter} that no handler has initialised
 * queues its writes instead of performing them, so a unit test of {@code deliver} would watch nothing happen and pass
 * either way — which is the shape of test this repository has had to undo four times. Those claims are made over a real
 * request in {@code MembershipStreamResourceIT}, and over a real socket in {@code MembershipStreamOnTheWireIT}.</p>
 */
class MembershipStreamRegistryTest {

    /** Whoever the caller is does not matter to the bookkeeping; the filter is asserted where it can be seen. */
    private static final PatientScope.Visibility AMA = new PatientScope.Visibility("patient-ama", false);

    /**
     * <b>The heartbeat is a number chosen against another file, and this is the assertion that keeps it honest.</b>
     *
     * <p>{@code quality/host-site.conf} sets {@code proxy_read_timeout 60s}, so an idle proxied stream is cut a minute
     * in — and an idle stream is this feature's ordinary state, because a membership sits {@code PENDING} for as long
     * as the back office takes. The architect chose a heartbeat over widening the proxy: nginx belongs to them and not
     * to this repository, on the quality box and on production alike.</p>
     *
     * <p>Written as a relationship rather than as {@code isEqualTo(25)}. A literal would go on passing if
     * {@code proxy_read_timeout} were lowered to thirty, which is precisely the change that would break this and
     * precisely the change nobody would think to come here for. Two beats inside the window means one may be lost to a
     * slow moment without costing the connection.</p>
     */
    @Test
    void twoHeartbeatsFitInsideTheProxysIdleCut() {
        long beat = MembershipStreamRegistry.DEFAULT_HEARTBEAT_SECONDS;
        long cut = MembershipStreamRegistry.IDLE_CUT.toSeconds();

        assertThat(beat).isPositive();
        assertThat(beat * 2).as("a heartbeat with no margin loses the connection the first time one beat is late").isLessThan(cut);
    }

    @Test
    void theIntervalInForceIsTheConfiguredOne() {
        assertThat(new MembershipStreamRegistry(MembershipStreamRegistry.DEFAULT_HEARTBEAT_SECONDS).heartbeatInterval().toSeconds())
            .isEqualTo(MembershipStreamRegistry.DEFAULT_HEARTBEAT_SECONDS);
    }

    @Test
    void eachSubscriberIsItsOwnStream() {
        MembershipStreamRegistry registry = new MembershipStreamRegistry(MembershipStreamRegistry.DEFAULT_HEARTBEAT_SECONDS);

        SseEmitter first = registry.subscribe(AMA);
        SseEmitter second = registry.subscribe(AMA);

        // Two tabs are two streams. Keyed by patient, the second connect would silently close the first — which is how
        // the endpoint this replaces behaved, keyed on login.
        assertThat(second).isNotSameAs(first);
        assertThat(registry.openStreams()).isEqualTo(2);
    }

    /**
     * <b>The heartbeat is the reaper, and this is the whole reason it is not only a keep-alive.</b>
     *
     * <p>A browser that goes away without closing leaves nothing for this instance to notice — the emitter is not
     * timed out (Tomcat's 30-second default would kill a healthy stream, so it is switched off deliberately) and no
     * callback fires. The next write is the only evidence, and dropping the stream on a failed write is what stops the
     * set growing for the life of the process.</p>
     *
     * <p>The emitter is completed first to produce that failure, because a completed emitter refuses a send — which is
     * a stand-in for a closed socket rather than the thing itself. A real disconnection is exercised by
     * {@code MembershipStreamOnTheWireIT} closing its socket; what this pins is that a failed write removes the
     * subscription rather than being logged and retried for ever.</p>
     */
    @Test
    void aStreamThatCannotBeWrittenToIsDroppedOnTheNextBeat() {
        MembershipStreamRegistry registry = new MembershipStreamRegistry(MembershipStreamRegistry.DEFAULT_HEARTBEAT_SECONDS);
        SseEmitter gone = registry.subscribe(AMA);
        registry.subscribe(new PatientScope.Visibility("patient-kofi", false));
        gone.complete();

        registry.beat();

        // One dropped, and — the half that matters — the other kept. A reaper that takes the healthy streams with it
        // would look identical from the count alone.
        assertThat(registry.openStreams()).isEqualTo(1);
    }

    @Test
    void shutdownClosesEveryStreamItIsHolding() {
        MembershipStreamRegistry registry = new MembershipStreamRegistry(MembershipStreamRegistry.DEFAULT_HEARTBEAT_SECONDS);
        registry.subscribe(AMA);
        registry.subscribe(new PatientScope.Visibility("patient-kofi", false));

        registry.shutdown();

        assertThat(registry.openStreams()).isZero();
    }

    /** Beating with nothing connected is the ordinary state of this loop, and it must not throw on the shared thread. */
    @Test
    void beatingWithNoSubscribersIsHarmless() {
        MembershipStreamRegistry registry = new MembershipStreamRegistry(MembershipStreamRegistry.DEFAULT_HEARTBEAT_SECONDS);

        org.assertj.core.api.Assertions.assertThatCode(registry::beat).doesNotThrowAnyException();
    }
}
