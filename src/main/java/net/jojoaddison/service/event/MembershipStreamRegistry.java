package net.jojoaddison.service.event;

import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.jojoaddison.security.PatientScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 * <h2>The connect flush, and why returning the emitter was not enough</h2>
 *
 * <p><strong>A response that has been built is not a response that has been written.</strong> Returning the emitter
 * from the handler puts the status line and headers into the servlet response's buffer and nothing sends them: Tomcat
 * holds the buffer until something flushes it or it fills, and an idle stream fills nothing. So the first bytes a
 * client saw were the first {@code :keep-alive} tick — measured on the quality stack on 2026-09-18 at 10.2s, 2.2s and
 * 19.2s direct to the api and 16.9s through the gateway, scattered across the heartbeat interval because that is what
 * they were waiting for. This class and {@code MembershipStreamResource} both documented the opposite, in as many
 * words, for as long as that code was deployed. Backlog item 63.</p>
 *
 * <p>{@link #subscribe} therefore writes one comment before the emitter leaves it. The emitter is not yet
 * initialised at that point, so the write is held as an early send attempt and performed by Spring the instant the
 * handler hands the emitter over — {@code ResponseBodyEmitter.initialize} drains those attempts through the message
 * converters and calls {@code flush()} on the response, which is the call that was missing. It happens on the request
 * thread, so "on connect" is literal rather than approximate.</p>
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

    /**
     * How long a stream may live before this instance closes it and makes the client come back.
     *
     * <h3>It is an authorization control, not housekeeping</h3>
     *
     * <p>{@link PatientScope#captureVisibility()} resolves who the caller may see <em>once</em>, on the request
     * thread, because the Kafka consumer thread has no token and no {@code X-Acting-As} header to ask again with. That
     * freeze is only defensible if it ends. Without a maximum age it does not: the emitter has no server timeout, the
     * heartbeat never stops, and the stream lives until the browser goes away — which for an open tab is days.</p>
     *
     * <p>The concrete failure: a patient revokes their care angel on Monday, the angel's tab stays open, and
     * Thursday's verification pushes that patient's membership id and status to somebody whose delegation ended three
     * days earlier. {@code PatientScope} re-reads the delegation on every <em>request</em> precisely so a revocation
     * takes effect on the next one — a stream with no end is that guarantee quietly suspended. The token's own expiry
     * is never re-checked mid-stream either, for the same reason.</p>
     *
     * <p>Thirty minutes bounds both, and bounds a third case that is nobody's fault: a caller who connects before
     * their {@code Profile} exists captures an empty scope and holds a permanently deaf stream that completing
     * onboarding never heals. Reconnecting fixes all three, and the clients have to hand-write reconnect logic anyway
     * — that is the cost of {@code fetch()} over {@code EventSource}, which backlog item 39 already accepted — so a
     * server-initiated close costs them nothing they were not already building.</p>
     *
     * <p>Enforced on the heartbeat, so a stream is retired at the first beat past its age rather than to the second.
     * That granularity is the heartbeat interval and is deliberate: a second timer to be exact about when a
     * thirty-minute window ends would be precision nothing needs.</p>
     */
    public static final int DEFAULT_MAX_AGE_SECONDS = 1800;

    /**
     * The comment written as the stream opens, and what makes the first byte arrive on connect.
     *
     * <p>Distinct from {@code keep-alive} deliberately. Both are comments and a client ignores both, so the text costs
     * nothing on the wire — but it is the only way a reader of a capture, or of
     * {@code MembershipStreamFirstByteIT}, can tell "the stream flushed when it opened" from "the first heartbeat
     * arrived", which are the two states item 63 exists to separate.</p>
     */
    public static final String CONNECTED_COMMENT = "connected";

    /**
     * The comment the heartbeat writes, named here because two tests now assert its exact text.
     *
     * <p>A literal in {@link #beat} and a literal in a test are two places one string lives, and the failure mode of
     * that is a test that waits for a line the server stopped sending. {@code MembershipStreamOnTheWireIT} loops until
     * it sees this specific line rather than any comment, which is what makes it evidence that the scheduler ran.</p>
     */
    public static final String KEEP_ALIVE_COMMENT = "keep-alive";

    private final Logger log = LoggerFactory.getLogger(MembershipStreamRegistry.class);

    /**
     * No server-side timeout on the emitter itself.
     *
     * <p>{@code new SseEmitter()} leaves the async request at the servlet container's default, which for Tomcat is 30
     * seconds — so an unconfigured emitter dies before the first heartbeat and the stream ends without anything having
     * gone wrong. Zero means "no timeout" to the servlet spec. Liveness is then the heartbeat's job, which is the right
     * place for it: a failed write is evidence the client has gone, where a timeout is only a guess that it might
     * have.</p>
     *
     * <p><strong>"No timeout" is not "no end".</strong> A container timeout is the wrong instrument for liveness and
     * is switched off; the stream's lifetime is bounded separately and for a different reason by
     * {@link #DEFAULT_MAX_AGE_SECONDS}. Removing that bound puts this back to a stream that ends only when the browser
     * does — read its javadoc before changing either.</p>
     */
    private static final long NO_SERVER_TIMEOUT = 0L;

    /** Held in a set rather than a map: two sessions of one patient are two streams, not one that replaces the other. */
    private final Set<Subscription> subscriptions = ConcurrentHashMap.newKeySet();

    private final Duration heartbeat;

    private final Duration maxAge;

    private final Clock clock;

    private final ScheduledExecutorService heartbeats;

    @Autowired
    public MembershipStreamRegistry(
        @Value("${hc.membership-stream.heartbeat-seconds:" + DEFAULT_HEARTBEAT_SECONDS + "}") long heartbeatSeconds,
        @Value("${hc.membership-stream.max-age-seconds:" + DEFAULT_MAX_AGE_SECONDS + "}") long maxAgeSeconds
    ) {
        this(heartbeatSeconds, maxAgeSeconds, Clock.systemUTC());
    }

    /**
     * For tests that need to move time rather than wait for it.
     *
     * <p>A clock rather than a tiny {@code max-age} so that both halves can be asserted — that a young stream
     * <em>survives</em> a beat matters as much as that an old one does not, and a registry configured to expire
     * everything immediately cannot show the first.</p>
     */
    MembershipStreamRegistry(long heartbeatSeconds, long maxAgeSeconds, Clock clock) {
        this.heartbeat = Duration.ofSeconds(heartbeatSeconds);
        this.maxAge = Duration.ofSeconds(maxAgeSeconds);
        this.clock = clock;
        this.heartbeats =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "membership-stream-heartbeat");
                // Daemon so a failure to shut down cannot hold the JVM open — this thread has nothing to finish.
                thread.setDaemon(true);
                return thread;
            });
        this.heartbeats.scheduleAtFixedRate(this::beat, heartbeatSeconds, heartbeatSeconds, TimeUnit.SECONDS);
    }

    /**
     * Opens a stream for one caller, and writes to it before handing it back.
     *
     * <p>The write is the point and is not decoration — see the class javadoc for what returning an unwritten emitter
     * actually did.</p>
     *
     * <p><strong>It goes through {@link #send} for uniformity, not because it can fail here.</strong> With no handler
     * attached the emitter only queues, and the one thing {@code ResponseBodyEmitter.send} throws — a state check on
     * an already-completed emitter — a freshly built one cannot trip, so the catch in {@code send} is unreachable on
     * this path. A client that has gone before the flush surfaces the failure out of Spring's {@code initialize}
     * instead, which this class never sees; the subscription lingers until the next write fails and retires it, so
     * the worst case is one dead entry for less than a heartbeat. That is bounded and self-healing, and is written
     * down here because the code reads as though the {@code try} were doing the work.</p>
     *
     * @param visibility the caller's scope, captured by {@code PatientScope} while the request was still on the stack.
     * @return the emitter to return from the endpoint, carrying one queued comment. Spring flushes it — with the
     *     status line and headers ahead of it — as it initialises the emitter on the request thread, so the client
     *     sees the stream open before any event exists.
     */
    public SseEmitter subscribe(PatientScope.Visibility visibility) {
        SseEmitter emitter = new SseEmitter(NO_SERVER_TIMEOUT);
        Subscription subscription = new Subscription(visibility, emitter, clock.instant());
        // Registered before the three callbacks are attached would be a race with an immediate completion; attached
        // first would leak a subscription that completes before it is in the set. Adding first and removing on every
        // terminal callback is the order that cannot leave a dead emitter in the set.
        subscriptions.add(subscription);
        emitter.onCompletion(() -> subscriptions.remove(subscription));
        emitter.onTimeout(() -> subscriptions.remove(subscription));
        emitter.onError(error -> subscriptions.remove(subscription));
        // Queued rather than written, because no handler is attached to the emitter until the endpoint returns it.
        // That is exactly the timing wanted: Spring performs it during initialisation, which is the first moment a
        // flush can reach the socket at all.
        //
        // The subscription is in the set before this line, so a beat landing in the window between the two would
        // queue ":keep-alive" ahead of ":connected" and a test asserting the first comment would go red once and
        // never again. Sub-microsecond against a 25-second interval, and not worth a second collection to close —
        // but named here so it is recognised as this race rather than as a regression.
        send(subscription, SseEmitter.event().comment(CONNECTED_COMMENT));
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

    /** The maximum age actually in force, so a test can assert it rather than assume the default. */
    public Duration maxAge() {
        return maxAge;
    }

    /**
     * Keeps the live streams alive and closes the ones that have run their course.
     *
     * <p>Retiring comes first: a stream past its age is closed rather than beaten at, so it is never kept alive for
     * one interval longer than it should be. See {@link #DEFAULT_MAX_AGE_SECONDS} for why a stream has an age at
     * all — it is what bounds the visibility decision {@code PatientScope} froze when the stream was opened.</p>
     */
    void beat() {
        Instant expiredBefore = clock.instant().minus(maxAge);
        for (Subscription subscription : subscriptions) {
            if (subscription.openedAt().isBefore(expiredBefore)) {
                retire(subscription);
                continue;
            }
            send(subscription, SseEmitter.event().comment(KEEP_ALIVE_COMMENT));
        }
    }

    /**
     * Closes a stream that has reached its maximum age, leaving the client to reconnect.
     *
     * <p>{@code complete()} rather than {@code completeWithError()}: nothing went wrong, and an error would have the
     * client's reconnect logic back off as though the server were unhealthy. Removed from the set before completing,
     * so a beat running concurrently cannot write to it afterwards.</p>
     */
    private void retire(Subscription subscription) {
        subscriptions.remove(subscription);
        log.debug("Retiring a membership stream at {}; the client reconnects and its scope is resolved again", maxAge);
        try {
            subscription.emitter().complete();
        } catch (Exception e) {
            // The client had already gone. The subscription is out of the set either way, which is all this wanted.
            log.debug("A membership stream being retired had already ended", e);
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

        /** When the scope above was resolved, which is what {@link #maxAge} bounds rather than any idea of activity. */
        private final Instant openedAt;

        private Subscription(PatientScope.Visibility visibility, SseEmitter emitter, Instant openedAt) {
            this.visibility = visibility;
            this.emitter = emitter;
            this.openedAt = openedAt;
        }

        private PatientScope.Visibility visibility() {
            return visibility;
        }

        private SseEmitter emitter() {
            return emitter;
        }

        private Instant openedAt() {
            return openedAt;
        }

        private synchronized void send(SseEmitter.SseEventBuilder payload) throws Exception {
            emitter.send(payload);
        }
    }
}
