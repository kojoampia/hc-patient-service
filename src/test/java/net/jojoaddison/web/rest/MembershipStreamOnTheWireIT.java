package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.jojoaddison.HcPatientServiceApp;
import net.jojoaddison.config.AsyncSyncConfiguration;
import net.jojoaddison.config.EmbeddedKafka;
import net.jojoaddison.config.EmbeddedMongo;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * The same two claims as {@code MembershipStreamResourceIT}, made against a socket instead of a mock.
 *
 * <h2>Why this exists when a MockMvc test already asserts the headers</h2>
 *
 * <p><strong>Because "the framework committed the response" and "bytes reached the client" are different statements,
 * and the defect this endpoint replaces sat in the gap between them.</strong> {@code GET
 * /api/hc-patient-service-kafka/register} returns an {@code SseEmitter} exactly as this endpoint does — so a MockMvc
 * assertion about status and content type would have passed for it too, while on the quality stack no response line
 * arrived at all within eight seconds, direct to the api port as well as through the gateway. A test that cannot
 * distinguish the two cannot be the evidence for the one that matters.</p>
 *
 * <p>So this opens a real TCP connection to a real port, writes a real request with a real token, and reads the status
 * line and headers off the socket with <em>nothing having been published</em>. Then it keeps reading, and requires a
 * keep-alive comment to arrive — which is the other thing no mock can show, because a heartbeat is only a heartbeat if
 * it is written to a connection somebody is holding open.</p>
 *
 * <h2>The heartbeat is run at one second here and is twenty-five in production</h2>
 *
 * <p>The interval is a property so that this test takes three seconds rather than a minute. <b>What is therefore not
 * exercised is the production value</b>, which is asserted against the proxy timeout it was chosen for by
 * {@code MembershipStreamRegistryTest} — a relationship, not a literal, because a literal 25 would go on passing if
 * the proxy's {@code proxy_read_timeout} were ever lowered.</p>
 *
 * <p>⚠ <b>And the shortening cost something, which is backlog item 63.</b> The assertion below that the status line
 * arrives "on connect" was satisfied, for as long as cycle 1 was deployed, by a stream that flushed nothing until its
 * first heartbeat: with the beat at one second the wait was a second, and a second reads as immediate. <b>The instrument set the property it
 * was meant to check.</b> The claim about timing therefore no longer lives here — {@code MembershipStreamFirstByteIT}
 * overrides no stream property, measures time to first byte at the production heartbeat, and refuses to run if
 * anything has shortened it. What this class keeps is the rest: that the headers and the keep-alive reach a real
 * socket, and that the server closes the stream by itself.</p>
 *
 * <p>Raw sockets rather than an HTTP client on purpose: every client worth using buffers, follows and re-frames, and
 * the question here is what arrives and when. A reader on a socket with a read timeout answers it directly.</p>
 */
@SpringBootTest(
    classes = { HcPatientServiceApp.class, AsyncSyncConfiguration.class },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@EmbeddedMongo
@EmbeddedKafka
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(
    properties = {
        "hc.membership-stream.heartbeat-seconds=1",
        // Thirty minutes in production. Three here, so the server-initiated close can be watched rather than argued
        // about — and still several heartbeats, so the keep-alive test below is not racing the retirement.
        "hc.membership-stream.max-age-seconds=3",
    }
)
class MembershipStreamOnTheWireIT {

    private static final String AMA_EMAIL = "Ama.Wire@Example.Test";
    private static final String AMA_PATIENT_ID = "patient-ama-wire";

    /** Comfortably longer than the three-second maximum age, and far short of a suite anybody would notice. */
    private static final int READ_TIMEOUT_MILLIS = 15_000;

    /** The keep-alive is due after one second; this is slack, not the claim. */
    private static final long KEEP_ALIVE_DEADLINE_MILLIS = 5_000;

    @LocalServerPort
    private int port;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private JwtEncoder jwtEncoder;

    @BeforeEach
    void setUp() {
        profileRepository.deleteAll();
        profileRepository.save(new Profile().email(AMA_EMAIL).patientId(AMA_PATIENT_ID));
    }

    @Test
    void theStatusLineAndHeadersArriveOnConnectAndAKeepAliveFollows() throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), READ_TIMEOUT_MILLIS);
            socket.setSoTimeout(READ_TIMEOUT_MILLIS);

            OutputStream out = socket.getOutputStream();
            out.write(
                ("GET /api/membership-events HTTP/1.1\r\n" +
                    "Host: 127.0.0.1:" +
                    port +
                    "\r\n" +
                    "Accept: text/event-stream\r\n" +
                    "Authorization: Bearer " +
                    token() +
                    "\r\n" +
                    "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII)
            );
            out.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            // THE ASSERTION THIS CLASS EXISTS FOR. Nothing has been published and nothing will be; if the status line
            // does not arrive by itself, the read below blocks until the socket timeout and the test fails there.
            String statusLine = reader.readLine();
            assertThat(statusLine).as("no response line arrived — the defect the replaced endpoint had").startsWith("HTTP/1.1 200");

            List<String> headers = new ArrayList<>();
            for (String line = reader.readLine(); line != null && !line.isEmpty(); line = reader.readLine()) {
                headers.add(line.toLowerCase(java.util.Locale.ROOT));
            }
            assertThat(headers).anyMatch(header -> header.startsWith("content-type: text/event-stream"));
            assertThat(headers).anyMatch(header -> header.startsWith("x-accel-buffering: no"));

            Instant connectedAt = Instant.now();
            String keepAlive = null;
            for (String line = reader.readLine(); line != null && keepAlive == null; line = reader.readLine()) {
                if (line.startsWith(":")) {
                    keepAlive = line;
                }
            }

            assertThat(keepAlive).as("an idle stream sends nothing, so nginx cuts it at proxy_read_timeout").isNotNull();
            assertThat(ChronoUnit.MILLIS.between(connectedAt, Instant.now()))
                .as("the keep-alive has to come round faster than the proxy gives up")
                .isLessThan(KEEP_ALIVE_DEADLINE_MILLIS);
        }
    }

    /**
     * <b>The server ends the stream by itself, and the client's socket sees it.</b>
     *
     * <p>The visibility decision {@code PatientScope} takes at connect cannot be re-asked from the Kafka consumer
     * thread, so it is frozen for the life of the stream — and a stream that ends only when the browser does leaves
     * that freeze running for days. A revoked care angel with an open tab would still be pushed their former
     * patient's membership id and status. The maximum age is what bounds it, and this is the assertion that the
     * bound reaches a real connection rather than only a set in memory.</p>
     *
     * <p>{@code readLine()} returning null is end-of-stream: the response was completed and the connection closed by
     * the server, with the client having asked for nothing and done nothing wrong. Three seconds here, thirty minutes
     * in production — {@code MembershipStreamRegistryTest} is what holds the real value to a range.</p>
     */
    @Test
    void theServerClosesTheStreamOnceItHasRunItsMaximumAge() throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), READ_TIMEOUT_MILLIS);
            socket.setSoTimeout(READ_TIMEOUT_MILLIS);

            OutputStream out = socket.getOutputStream();
            // BEFORE the request goes out, and that is not fussiness. The registry stamps openedAt while handling the
            // request, so any instant taken after the response has been read is already later than the age being
            // measured — the first version of this test took it after the status line and failed at 2963ms against a
            // 3000ms bound, reporting correct behaviour as a defect. Measured from here, the elapsed time is an
            // over-estimate of the stream's age, so a lower bound on it is sound.
            Instant requestedAt = Instant.now();
            out.write(
                ("GET /api/membership-events HTTP/1.1\r\n" +
                    "Host: 127.0.0.1:" +
                    port +
                    "\r\n" +
                    "Accept: text/event-stream\r\n" +
                    "Authorization: Bearer " +
                    token() +
                    "\r\n" +
                    "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII)
            );
            out.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            assertThat(reader.readLine()).startsWith("HTTP/1.1 200");

            // Drain to end of stream. If the maximum age were not enforced this would block on a stream that
            // heartbeats for ever, and the test would fail on the socket timeout rather than pass quietly.
            while (reader.readLine() != null) {
                // Headers, then keep-alives, until the server completes the response.
            }

            long lived = ChronoUnit.MILLIS.between(requestedAt, Instant.now());
            // The lower bound says it was not closed for some unrelated reason the moment it opened; the upper bound
            // says it was closed at all. Retirement lands on a heartbeat, so the real window is the age plus up to one
            // beat — this asserts the range rather than pretending to a precision the design does not claim.
            assertThat(lived).as("the stream was closed before it had lived its configured age").isGreaterThanOrEqualTo(3_000L);
            assertThat(lived).as("the stream outlived its maximum age — nothing is forcing a reconnect").isLessThan(READ_TIMEOUT_MILLIS);
        }
    }

    /**
     * A token this service will accept, minted with its own encoder and the test key.
     *
     * <p>The gateway is the issuer in every real deployment; here there is no gateway, and the {@code email} claim is
     * the only part of the token {@code PatientScope} reads.</p>
     */
    private String token() {
        JwtClaimsSet claims = JwtClaimsSet
            .builder()
            .subject("ama")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
            .claims(map -> map.putAll(Map.of(SecurityUtils.EMAIL_KEY, AMA_EMAIL, SecurityUtils.AUTHORITIES_KEY, AuthoritiesConstants.USER)))
            .build();
        JwsHeader header = JwsHeader.with(SecurityUtils.JWT_ALGORITHM).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
