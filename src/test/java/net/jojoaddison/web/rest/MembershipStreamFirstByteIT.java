package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import net.jojoaddison.HcPatientServiceApp;
import net.jojoaddison.config.AsyncSyncConfiguration;
import net.jojoaddison.config.EmbeddedKafka;
import net.jojoaddison.config.EmbeddedMongo;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.event.MembershipStreamRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.annotation.DirtiesContext;

/**
 * <b>How long the patient waits for the first byte, measured at the heartbeat production actually runs.</b>
 *
 * <h2>Why this is a second socket test and not two more assertions in the first one</h2>
 *
 * <p>{@code MembershipStreamOnTheWireIT} asserts that the status line and the two proxy headers arrive with nothing
 * published, which is the right claim — and it <strong>sets {@code hc.membership-stream.heartbeat-seconds=1}</strong>
 * so the suite takes seconds rather than a minute. Backlog item 63 is what that cost: the endpoint was flushing
 * nothing on connect and the first byte a client received was the first {@code :keep-alive} tick, so the test was
 * measuring exactly the delay it existed to rule out, at one second instead of twenty-five, and calling it "on
 * connect". <b>The instrument set the property it was meant to check.</b> Measured on the quality stack on
 * 2026-09-18, time to first byte was 10.2s, 2.2s and 19.2s on three samples direct to the api and 16.9s through the
 * gateway — scattered across the heartbeat interval, which is the signature of a response nobody flushed.</p>
 *
 * <p>So this class overrides <b>no</b> stream property. The heartbeat here is
 * {@value net.jojoaddison.service.event.MembershipStreamRegistry#DEFAULT_HEARTBEAT_SECONDS} seconds and the maximum
 * age is the production half hour, and the first assertion of the test is the registry confirming it — a test of
 * time-to-first-byte that lets its own heartbeat be shortened underneath it proves nothing, because shortening the
 * heartbeat is how the defect hid in the first place.</p>
 *
 * <h2>The two things that make the number mean something</h2>
 *
 * <p><b>The deadline is checked against the heartbeat rather than merely chosen.</b>
 * {@link #FIRST_BYTE_DEADLINE_MILLIS} is asserted to be an order of magnitude inside one beat, so it cannot later be
 * relaxed to a value a heartbeat could satisfy without that assertion failing first.</p>
 *
 * <p><b>The byte that is waited for is the connect comment specifically</b>, not "any byte" — a keep-alive is
 * twenty-five seconds away and the socket gives up before it, so a run that passes has read
 * {@code :}{@value net.jojoaddison.service.event.MembershipStreamRegistry#CONNECTED_COMMENT} and nothing else could
 * have produced it.</p>
 *
 * <p>The socket's read timeout is deliberately <b>longer</b> than the heartbeat. A timeout shorter than a beat would
 * make a regression here report as "no response at all", which is a different defect with a different cause; with the
 * timeout set past it, a regression instead fails with the elapsed milliseconds in the message and names the beat it
 * waited for.</p>
 */
@SpringBootTest(
    classes = { HcPatientServiceApp.class, AsyncSyncConfiguration.class },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@EmbeddedMongo
@EmbeddedKafka
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MembershipStreamFirstByteIT {

    /**
     * The measurement is logged as well as asserted.
     *
     * <p>An assertion only speaks when it fails, and "it passed" is not a time to first byte. Item 63 was filed off
     * numbers rather than off a red test, and the number is what a later reader needs to see whether the margin has
     * been eaten.</p>
     */
    private static final Logger log = LoggerFactory.getLogger(MembershipStreamFirstByteIT.class);

    private static final String KWAME_EMAIL = "Kwame.FirstByte@Example.Test";
    private static final String KWAME_PATIENT_ID = "patient-kwame-first-byte";

    /**
     * What "on connect" is allowed to mean, and it is an order of magnitude inside a heartbeat on purpose.
     *
     * <p>The handler itself finishes in single-digit milliseconds — the api log shows {@code Exit: stream() with
     * result = <200 OK, SseEmitter@…>} three milliseconds after the request. Two seconds is therefore enormous slack
     * for a contended box and still nowhere near a beat, which is the only distinction this test needs to draw.</p>
     */
    private static final long FIRST_BYTE_DEADLINE_MILLIS = 2_000;

    /** Past the heartbeat, so a regression is reported as a measured delay rather than as a dead socket. */
    private static final int READ_TIMEOUT_MILLIS = 40_000;

    /**
     * Long enough for the server to have run the whole handler, short enough to cost nothing when it flushes nothing.
     *
     * <p>The warm-up exists because the first request through a freshly started context pays for filter-chain
     * initialisation, the JWT decoder and the first Mongo round trip, and none of that is what this test is about.
     * It deliberately does <em>not</em> wait for bytes: the point is to have executed the path server-side.</p>
     */
    private static final int WARM_UP_MILLIS = 3_000;

    @LocalServerPort
    private int port;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private MembershipStreamRegistry registry;

    @Autowired
    private JwtEncoder jwtEncoder;

    @BeforeEach
    void setUp() {
        profileRepository.deleteAll();
        profileRepository.save(new Profile().email(KWAME_EMAIL).patientId(KWAME_PATIENT_ID));
    }

    @Test
    void theFirstByteArrivesOnConnectRatherThanOnTheFirstHeartbeat() throws Exception {
        // THE INSTRUMENT ASSERTS ITS OWN CONFIGURATION FIRST. Everything below is a claim about a delay measured
        // against the heartbeat, and it is worth nothing if the heartbeat under it is not the one production runs.
        Duration heartbeat = registry.heartbeatInterval();
        assertThat(heartbeat)
            .as("this test overrides no stream property; a shortened heartbeat here would make its own result vacuous")
            .isEqualTo(Duration.ofSeconds(MembershipStreamRegistry.DEFAULT_HEARTBEAT_SECONDS));
        assertThat(FIRST_BYTE_DEADLINE_MILLIS * 10)
            .as("the deadline has been relaxed to something a heartbeat could satisfy, which is the defect itself")
            .isLessThanOrEqualTo(heartbeat.toMillis());
        assertThat(READ_TIMEOUT_MILLIS)
            .as("a socket that gives up before the first beat cannot tell a slow flush from no flush")
            .isGreaterThan((int) heartbeat.toMillis());

        warmUp();

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), READ_TIMEOUT_MILLIS);
            socket.setSoTimeout(READ_TIMEOUT_MILLIS);

            // Taken before the request leaves, so the elapsed time is an over-estimate of what the server took and an
            // upper bound on it is sound.
            Instant requestedAt = Instant.now();
            writeStreamRequest(socket);

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String statusLine = reader.readLine();
            long statusLineAfterMillis = ChronoUnit.MILLIS.between(requestedAt, Instant.now());

            assertThat(statusLine).startsWith("HTTP/1.1 200");
            assertThat(statusLineAfterMillis)
                .as("the status line waited for the scheduler; nothing is flushed when the stream opens")
                .isLessThan(FIRST_BYTE_DEADLINE_MILLIS);

            // Past the headers and on to the body. A comment is the only thing that can be there: nothing has been
            // published, and the first keep-alive is a whole heartbeat away — further off than this socket's patience.
            String comment = null;
            for (String line = reader.readLine(); line != null && comment == null; line = reader.readLine()) {
                if (line.startsWith(":")) {
                    comment = line;
                }
            }
            long commentAfterMillis = ChronoUnit.MILLIS.between(requestedAt, Instant.now());

            log.info(
                "Membership stream at a {} heartbeat: status line after {} ms, first body comment after {} ms",
                heartbeat,
                statusLineAfterMillis,
                commentAfterMillis
            );

            assertThat(comment)
                .as("the headers were committed but no body byte reached the client, which nginx may still be buffering")
                .isEqualTo(":" + MembershipStreamRegistry.CONNECTED_COMMENT);
            assertThat(commentAfterMillis)
                .as("the first body byte is a heartbeat tick rather than the connect flush")
                .isLessThan(FIRST_BYTE_DEADLINE_MILLIS);
        }
    }

    /**
     * Runs the whole request path once and throws the result away.
     *
     * <p>Swallows the read timeout rather than asserting on it: whether this connection produces bytes is the
     * question the test asks, not something the warm-up may answer.</p>
     */
    private void warmUp() throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), READ_TIMEOUT_MILLIS);
            socket.setSoTimeout(WARM_UP_MILLIS);
            writeStreamRequest(socket);
            try {
                socket.getInputStream().read();
            } catch (SocketTimeoutException expectedWhenNothingIsFlushed) {
                // The handler has run either way, which is all this wanted.
            }
        }
    }

    private void writeStreamRequest(Socket socket) throws Exception {
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
    }

    /**
     * A token this service will accept, minted with its own encoder and the test key — the same shape
     * {@code MembershipStreamOnTheWireIT} uses, because {@code PatientScope} reads only the {@code email} claim.
     */
    private String token() {
        JwtClaimsSet claims = JwtClaimsSet
            .builder()
            .subject("kwame")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
            .claims(map ->
                map.putAll(Map.of(SecurityUtils.EMAIL_KEY, KWAME_EMAIL, SecurityUtils.AUTHORITIES_KEY, AuthoritiesConstants.USER))
            )
            .build();
        JwsHeader header = JwsHeader.with(SecurityUtils.JWT_ALGORITHM).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
