package net.jojoaddison.service.event;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.jojoaddison.security.PatientScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Every server-sent-event stream this instance is currently holding open, and the two things that have to happen to
 * them: a membership change has to reach the right ones, and all of them have to be kept alive through an idle minute.
 *
 * <h2>The filter is not here. That is the point.</h2>
 *
 * <p>A stream is registered with a {@link PatientScope.Visibility} — the caller's own answer to "whose records may I
 * see", resolved by {@code PatientScope} on the request thread and handed over. Delivery asks that object and nothing
 * else. There is deliberately no per-patient rule written in this class: backlog item 39 names {@code PatientScope} as
 * the authority and refuses a second implementation of it, because the generated endpoint this replaces had exactly
 * that shape — {@code KafkaConsumer.register(principal.getName())} keyed emitters by login and then pushed every frame
 * to every one of them, so pointing membership events at it would have broadcast one patient's activation to every
 * connected session.</p>
 *
 * <h2>The heartbeat, and the number in it</h2>
 *
 * <p><strong>{@value #DEFAULT_HEARTBEAT_SECONDS} seconds, because the quality stack's nginx cuts an idle proxied
 * response at 60</strong> ({@code quality/host-site.conf}, {@code proxy_read_timeout 60s}). A membership can sit
 * {@code PENDING} for as long as the back office takes, so the ordinary state of this stream is idle — without a
 * heartbeat the common case is a connection silently dropped a minute in, and a client that believes it is listening.
 * The architect chose the heartbeat over widening the proxy timeout: the stream has to survive an idle minute with
 * nobody touching nginx, on the quality box and on production, whose nginx this workspace does not own either.</p>
 *
 * <p>An SSE <em>comment</em> rather than an event ({@code : keep-alive}), so a client never has to distinguish a
 * keep-alive from a change. It is also the reaper: a write to a browser that has gone away throws, and that is the only
 * way this instance learns a socket is dead — a client that vanishes without closing leaves nothing else to notice.</p>
 *
 * <h2>Why it owns a thread rather than using {@code @Scheduled}</h2>
 *
 * <p>One daemon thread, created here and stopped in {@link #shutdown()}. {@code @Scheduled} would need
 * {@code @EnableScheduling}, which this application does not have and which would switch on a shared scheduler for
 * everything else in it as a side effect; and a test of the interval would then need a Spring context to see any
 * behaviour at all. This way the whole class is exercisable without one.</p>
 */
@Component
public class MembershipStreamRegistry {

    /**
     * Roughly a third of the proxy's idle cut, which leaves room for two beats to be lost before the connection is.
     *
     * <p>Overridable with {@code hc.membership-stream.heartbeat-seconds} so a test can run the loop in a second
     * instead of twenty-five. Nothing sets it outside tests, and nothing should: the value is chosen against a proxy
     * timeout, not against a preference.</p>
     */
    public static final int DEFAULT_HEARTBEAT_SECONDS = 25;

    /**
     * What the heartbeat is measured against: {@code proxy_read_timeout 60s} in {@code quality/host-site.conf}.
     *
     * <p>Here so that {@code MembershipStreamRegistryTest} can assert the relationship between the two rather than
     * assert the literal 25, which would pass whatever the proxy did.</p>
     */
    public static final Duration IDLE_CUT = Duration.ofSeconds(60);

    private final Logger log = LoggerFactory.getLogger(MembershipStreamRegistry.class);

    /**
     * No server-side timeout on the emitter itself.
     *
     * <p>{@code new SseEmitter()} leaves the async request at the servlet container's default, which for Tomcat is 30
     * seconds — so an unconfigured emitter dies before the first heartbeat and the stream ends without anything having
     * gone wrong. Zero means "no timeout" to the servlet spec. Liveness is then the heartbeat's job, which is the right
     * place for it: a failed write is evidence the client has gone, where a timeout is only a guess that it might
     * have.</p>
     */
    private static final long NO_SERVER_TIMEOUT = 0L;

    /** Held in a set rather than a map: two sessions of one patient are two streams, not one that replaces the other. */
    private final Set<Subscription> subscriptions = ConcurrentHashMap.newKeySet();

    private final Duration heartbeat;

    private final ScheduledExecutorService heartbeats;

    public MembershipStreamRegistry(@Value("${hc.membership-stream.heartbeat-seconds:" + DEFAULT_HEARTBEAT_SECONDS + "}") long seconds) {
        this.heartbeat = Duration.ofSeconds(seconds);
        this.heartbeats =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "membership-stream-heartbeat");
                // Daemon so a failure to shut down cannot hold the JVM open — this thread has nothing to finish.
                thread.setDaemon(true);
                return thread;
            });
        this.heartbeats.scheduleAtFixedRate(this::beat, seconds, seconds, TimeUnit.SECONDS);
    }

    /**
     * Opens a stream for one caller.
     *
     * @param visibility the caller's scope, captured by {@code PatientScope} while the request was still on the stack.
     * @return the emitter to return from the endpoint. Its headers are on the wire as soon as the handler returns it,
     *     before any event exists — which is the property the generated endpoint did not have.
     */
    public SseEmitter subscribe(PatientScope.Visibility visibility) {
        SseEmitter emitter = new SseEmitter(NO_SERVER_TIMEOUT);
        Subscription subscription = new Subscription(visibility, emitter);
        // Registered before the three callbacks are attached would be a race with an immediate completion; attached
        // first would leak a subscription that completes before it is in the set. Adding first and removing on every
        // terminal callback is the order that cannot leave a dead emitter in the set.
        subscriptions.add(subscription);
        emitter.onCompletion(() -> subscriptions.remove(subscription));
        emitter.onTimeout(() -> subscriptions.remove(subscription));
        emitter.onError(error -> subscriptions.remove(subscription));
        log.debug("Opened a membership stream; {} now open on this instance", subscriptions.size());
        return emitter;
    }

    /**
     * Pushes one membership change to the streams entitled to see it, and to no others.
     *
     * <p>Called from the Kafka consumer thread, once per instance per frame. Every open stream is considered and
     * {@link PatientScope.Visibility#allows} decides each one — an unrestricted caller (an administrator or a
     * clinician) sees every patient's frames, for the same reason and by the same code that lets them read every
     * patient's records.</p>
     *
     * @param event the change, never null.
     */
    public void deliver(MembershipChangedEvent event) {
        int delivered = 0;
        for (Subscription subscription : subscriptions) {
            if (!subscription.visibility().allows(event.patientId())) {
                continue;
            }
            // The event id becomes the SSE id so a client can tell a redelivery from a new change. The name is what a
            // client dispatches on; the data is JSON, written by the same message converters the endpoint uses.
            if (send(subscription, SseEmitter.event().id(event.eventId()).name(event.type()).data(event, MediaType.APPLICATION_JSON))) {
                delivered++;
            }
        }
        log.debug("Membership {} reached {} of {} open streams", event.membershipId(), delivered, subscriptions.size());
    }

    /** How many streams this instance is holding open. For tests and for the log line above. */
    public int openStreams() {
        return subscriptions.size();
    }

    /** The interval actually in force, so a test can assert it rather than assume the default. */
    public Duration heartbeatInterval() {
        return heartbeat;
    }

    /** Sends the keep-alive comment to every open stream, dropping the ones that have gone away. */
    void beat() {
        for (Subscription subscription : subscriptions) {
            send(subscription, SseEmitter.event().comment("keep-alive"));
        }
    }

    /**
     * Writes to one stream, retiring it if the write fails.
     *
     * @return true when the bytes were handed to the emitter.
     */
    private boolean send(Subscription subscription, SseEmitter.SseEventBuilder payload) {
        try {
            // Serialised per subscription: the heartbeat thread and a consumer thread can reach the same emitter at
            // the same moment, and two interleaved writes produce a frame no SSE parser can read.
            subscription.send(payload);
            return true;
        } catch (Exception e) {
            // One broken client must not stop the others being told — the one behaviour worth keeping from the
            // generated consumer this replaces. A failed write means the browser has gone: retire the subscription
            // rather than beat at it for ever.
            log.debug("Dropping a membership stream whose client has gone", e);
            subscriptions.remove(subscription);
            try {
                subscription.emitter().completeWithError(e);
            } catch (Exception ignored) {
                // Already dead. Nothing to do and nothing to say.
            }
            return false;
        }
    }

    @PreDestroy
    void shutdown() {
        heartbeats.shutdownNow();
        // Completing rather than abandoning: a client that sees its stream end reconnects, where one left hanging on a
        // socket the JVM is closing gets no signal it can act on until TCP notices.
        subscriptions.forEach(subscription -> {
            try {
                subscription.emitter().complete();
            } catch (Exception ignored) {
                // Shutting down; a stream that will not close cleanly is not worth a line in the log.
            }
        });
        subscriptions.clear();
    }

    /**
     * One open stream and the scope it was opened under.
     *
     * <p>Identity-based equality, inherited from {@code Object} rather than written: two streams of the same patient
     * with the same scope are two different browsers and must both be kept.</p>
     */
    private static final class Subscription {

        private final PatientScope.Visibility visibility;
        private final SseEmitter emitter;

        private Subscription(PatientScope.Visibility visibility, SseEmitter emitter) {
            this.visibility = visibility;
            this.emitter = emitter;
        }

        private PatientScope.Visibility visibility() {
            return visibility;
        }

        private SseEmitter emitter() {
            return emitter;
        }

        private synchronized void send(SseEmitter.SseEventBuilder payload) throws Exception {
            emitter.send(payload);
        }
    }
}
