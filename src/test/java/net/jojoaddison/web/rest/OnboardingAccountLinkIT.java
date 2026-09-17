package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Onboarding stamping {@code Profile.accountId} — backlog item 44, the live half.
 *
 * <h2>Why this class configures a gateway when the rest of the suite does not</h2>
 *
 * <p>{@code src/test/resources/config/application.yml} leaves {@code application.gateway.base-url} blank, so no other
 * integration test opens a socket at startup — and a suite that supplies its own configuration cannot see a
 * configuration defect. This repository has one deployed-inert consumer to show for that exact gap. So this class
 * stands a real gateway up on a real port and points the property at it through {@link DynamicPropertySource},
 * which means the whole path is exercised: the servlet chain, {@code SecurityUtils.getCurrentRequestJwt}, the
 * {@code RestClient}, the message converter, and the write.</p>
 *
 * <p><strong>The relayed {@code Authorization} header is asserted, not assumed.</strong> The older
 * {@code getCurrentUserJWT} filters credentials on {@code instanceof String} and this service's resource-server chain
 * produces a decoded {@code Jwt}, so it returns empty on every real request — silently, because "no token" is
 * legitimate and every caller has a branch for it. A relay that never relays is indistinguishable from a deployment
 * that is not configured for one, and the only thing that can tell them apart is looking at what arrived.</p>
 */
@IntegrationTest
@AutoConfigureMockMvc
class OnboardingAccountLinkIT {

    private static final String API = "/api/onboarding";
    private static final String EMAIL = "ama@example.test";

    /** Started once for the class, because the property has to be known before the context is built. */
    private static final HttpServer GATEWAY = startStubGateway();

    private static final AtomicInteger STATUS = new AtomicInteger(200);
    private static final List<String> AUTHORIZATION_HEADERS_SEEN = new CopyOnWriteArrayList<>();

    @DynamicPropertySource
    static void pointTheServiceAtTheStubGateway(DynamicPropertyRegistry registry) {
        registry.add("application.gateway.base-url", () -> "http://127.0.0.1:" + GATEWAY.getAddress().getPort());
    }

    @Autowired
    private MockMvc restMockMvc;

    @Autowired
    private ProfileRepository profileRepository;

    @BeforeEach
    void initTest() {
        profileRepository.deleteAll();
        STATUS.set(200);
        AUTHORIZATION_HEADERS_SEEN.clear();
    }

    @Test
    void anOnboardingPatientIsLinkedToTheAccountTheySignedInWith() throws Exception {
        restMockMvc
            .perform(post(API).with(patient()).contentType(MediaType.APPLICATION_JSON).content(identityJson()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accountId").value("u-onboarded"));

        Profile stored = profileRepository.findOneByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(stored.getAccountId()).isEqualTo("u-onboarded");
        // patientId is untouched and still the profile's own id. accountId is a second identifier beside it, not a
        // replacement — moving the collections onto it is item 53 and removing this one is item 54.
        assertThat(stored.getPatientId()).isEqualTo(stored.getId());

        // The caller's own token went to the gateway, which is what lets /api/account — an endpoint that can name
        // nobody but the caller — answer at all. Nothing on this path needs an administrator.
        assertThat(AUTHORIZATION_HEADERS_SEEN).containsExactly("Bearer token");
    }

    @Test
    void aPatientIsOnboardedEvenWhenTheGatewayRefusesToSayWhoTheyAre() throws Exception {
        STATUS.set(401);

        restMockMvc
            .perform(post(API).with(patient()).contentType(MediaType.APPLICATION_JSON).content(identityJson()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accountId").doesNotExist());

        // This is the answer to "what happens when the gateway is unreachable at onboarding": the patient gets into
        // their own record, the profile is written unlinked, and change unit 004 links it on the next start. The
        // alternative — refusing — would make the one path a new patient has into their record depend on a sibling
        // service being up, to populate a field nothing on that path reads.
        Profile stored = profileRepository.findOneByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(stored.getAccountId()).isNull();
        assertThat(stored.getPatientId()).isEqualTo(stored.getId());
    }

    @Test
    void anAccountAlreadyLinkedToAnotherProfileIsNotClaimedTwice() throws Exception {
        profileRepository.save(new Profile().patientId("other").email("other@example.test").accountId("u-onboarded"));

        restMockMvc
            .perform(post(API).with(patient()).contentType(MediaType.APPLICATION_JSON).content(identityJson()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accountId").doesNotExist());

        assertThat(profileRepository.findOneByAccountId("u-onboarded")).map(Profile::getEmail).contains("other@example.test");
    }

    // --- fixture --------------------------------------------------------------------------------------------------

    private static HttpServer startStubGateway() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                "/api/account",
                exchange -> {
                    AUTHORIZATION_HEADERS_SEEN.add(exchange.getRequestHeaders().getFirst("Authorization"));
                    byte[] body =
                        "{\"id\":\"u-onboarded\",\"login\":\"ama\",\"email\":\"ama@example.test\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    int status = STATUS.get();
                    exchange.sendResponseHeaders(status, status == 200 ? body.length : -1);
                    if (status == 200) {
                        try (OutputStream out = exchange.getResponseBody()) {
                            out.write(body);
                        }
                    }
                    exchange.close();
                }
            );
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static RequestPostProcessor patient() {
        return jwt()
            .jwt(builder -> builder.claim(SecurityUtils.EMAIL_KEY, EMAIL))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.USER), new SimpleGrantedAuthority(AuthoritiesConstants.PATIENT));
    }

    private static String identityJson() {
        return (
            "{\"firstName\":\"Ama\",\"lastName\":\"Mensah\",\"birthDate\":\"1990-04-02\",\"sex\":\"F\"," +
            "\"mobilePhone\":\"0244000000\"," +
            "\"address\":{\"streetAddress\":\"5 Ankobra River Street\",\"town\":\"Accra\",\"region\":\"Greater Accra\"," +
            "\"digitalAddress\":\"GA-123-4567\",\"country\":\"Ghana\"}}"
        );
    }
}
