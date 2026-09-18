package net.jojoaddison.web.rest;

import net.jojoaddison.security.PatientScope;
import net.jojoaddison.service.event.MembershipStreamRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * {@code GET /api/membership-events} — the patient's open line to their own membership.
 *
 * <p>An administrator verifies a plan, hc-admin publishes, this service's consumer activates the membership, and until
 * now the patient saw nothing until they closed the app and opened it again. Backlog item 39, and the fix is a push
 * rather than a poll or a refresh-on-resume, both of which were offered and declined.</p>
 *
 * <h2>Three things this endpoint does that a generated one does not</h2>
 *
 * <p><strong>It answers before there is anything to say.</strong> The emitter is returned from the handler
 * immediately, so the status line and headers are written on connect rather than when the first event happens — and
 * for a membership waiting on the back office, the first event may be hours away. The endpoint this replaces,
 * {@code GET /api/hc-patient-service-kafka/register}, did not: probed on the quality stack with a real token, no
 * response line arrived at all within eight seconds, direct to the api port as well as through the gateway. A client
 * cannot tell that from a network failure, so its retry loop is driven by the wrong signal.</p>
 *
 * <p><strong>It is scoped to one patient.</strong> {@link PatientScope} decides, here as everywhere else — see
 * {@link PatientScope#captureVisibility()} for why the decision is taken on this thread and carried rather than asked
 * again at delivery. The generated endpoint keyed emitters by login and then pushed every frame to all of them.</p>
 *
 * <p><strong>It says "do not buffer this" to the proxy in front of it — and that reaches the first proxy, not the
 * last.</strong> nginx buffers a proxied response by default, which for a stream means the patient is told in batches
 * or not at all, and {@code X-Accel-Buffering: no} is the only lever available from inside the application because
 * every nginx in the path belongs to the architect rather than to this repository.</p>
 *
 * <p>Where it gets to, stated exactly, because the obvious reading is wrong. The deployed path is <b>two</b> nginx
 * hops, not one: the host's {@code location /} proxies to the web container, and the web container's own nginx
 * proxies {@code /services/} onward to the gateway. The header survives Spring Cloud Gateway, which passes response
 * headers through untouched, and reaches the <em>web-container</em> nginx, which honours it. <b>It stops there.</b>
 * All {@code X-Accel-*} headers are on nginx's built-in hidden list, so that hop consumes the header and does not
 * forward it — the host nginx never sees it and buffers according to its own configuration. So this unbuffers the
 * inner hop only, and <b>the heartbeat, not this header, is what keeps the connection alive through both.</b></p>
 *
 * <p>Not exercised: no response has been observed through either hop. This is read from
 * {@code quality/host-site.conf}, {@code deploy/docker/web-nginx.conf} and nginx's documented behaviour — and the
 * installed vhost is known to drift from the one in the repository. Note also that the two hops set different idle
 * timeouts: {@code proxy_read_timeout} is 120s on the web container's {@code /services/} location and <b>60s</b> on
 * the host's {@code location /}, so the host is the binding one and the 60s in
 * {@link net.jojoaddison.service.event.MembershipStreamRegistry} is measured against the right number.</p>
 *
 * <h2>What a caller with no patient gets</h2>
 *
 * <p>A stream that is open, heartbeats, and carries nothing — not a 403. That is
 * {@link PatientScope#findScoped}'s posture written for a stream: a caller who cannot be resolved to a patient is
 * served an empty result rather than an error, so the endpoint says nothing about whether anybody else exists. It
 * follows that an unrestricted caller — an administrator or a clinician — sees every patient's frames, by the same
 * rule and the same code that lets them read every patient's records.</p>
 */
@RestController
@RequestMapping("/api/membership-events")
public class MembershipStreamResource {

    private final Logger log = LoggerFactory.getLogger(MembershipStreamResource.class);

    private final PatientScope patientScope;

    private final MembershipStreamRegistry registry;

    public MembershipStreamResource(PatientScope patientScope, MembershipStreamRegistry registry) {
        this.patientScope = patientScope;
        this.registry = registry;
    }

    /**
     * Opens the stream.
     *
     * <p>Wrapped in a {@code ResponseEntity} only so the two proxy headers can be set; the emitter is what makes it a
     * stream. {@code Cache-Control: no-cache} is the SSE convention and keeps an intermediary from serving a stale
     * body to the next connect.</p>
     *
     * @return an open {@code text/event-stream}.
     */
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream() {
        // On this thread, while the token and the X-Acting-As header still exist. A caller naming a patient they hold
        // no delegation for is refused here, by PatientScope, before any stream is opened.
        PatientScope.Visibility visibility = patientScope.captureVisibility();
        log.debug("Opening a membership stream for patient {}", visibility.patientId());
        return ResponseEntity
            .ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-cache")
            .header("X-Accel-Buffering", "no")
            .body(registry.subscribe(visibility));
    }
}
