package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
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
import java.util.Optional;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * What a patient sees when they lose the race to link their own gateway account.
 *
 * <h2>The hazard, and why it needs a test of its own</h2>
 *
 * <p>{@code OnboardingService} asks {@code findOneByAccountId} and then writes, with no transaction around the pair —
 * Mongo runs standalone here. Change unit {@code 004} runs as an {@code ApplicationRunner}, so the service is already
 * serving while the backfill is linking profiles, and the two can reach for one account id at the same moment.
 * {@code ProfileAccountIdUniqueIndex} is what stops them both getting it; <strong>this is the test of what the loser
 * does about it</strong>, and the answer has to be that the patient is onboarded anyway.</p>
 *
 * <p>The race is made deterministic rather than hoped for: the account id genuinely <em>is</em> taken in the database,
 * and the repository is spied so that the check cannot see it — which is exactly what a concurrent writer looks like
 * from inside a check-then-act. Everything downstream of the check is real, including the index doing the refusing.</p>
 *
 * <p>The assertion is <strong>201</strong>, not an absence of stack trace. A database error escaping here would fail
 * the one request a new patient has to make, at the last of three writes, over a field nothing on that path reads.</p>
 */
@IntegrationTest
@AutoConfigureMockMvc
class OnboardingAccountRaceIT {

    private static final String API = "/api/onboarding";
    private static final String EMAIL = "ama@example.test";
    private static final String CONTESTED = "u-contested";

    private static final HttpServer GATEWAY = startStubGateway();

    @DynamicPropertySource
    static void pointTheServiceAtTheStubGateway(DynamicPropertyRegistry registry) {
        registry.add("application.gateway.base-url", () -> "http://127.0.0.1:" + GATEWAY.getAddress().getPort());
    }

    @Autowired
    private MockMvc restMockMvc;

    /**
     * Spied rather than mocked, so every call but the one stubbed below behaves exactly as it does in production —
     * including the {@code save} that the unique index refuses.
     */
    @MockitoSpyBean
    private ProfileRepository profileRepository;

    @BeforeEach
    void initTest() {
        profileRepository.deleteAll();
    }

    @Test
    void aPatientIsOnboardedEvenWhenAnotherWriterTakesTheirAccountIdFirst() throws Exception {
        // The account is genuinely taken, in the database, by another profile.
        profileRepository.save(new Profile().patientId("other").email("other@example.test").accountId(CONTESTED));

        // ...and the check does not see it. This is the whole race in one line: a concurrent writer is, from the
        // point of view of a check-then-act, indistinguishable from a check that answered stale.
        doReturn(Optional.empty()).when(profileRepository).findOneByAccountId(CONTESTED);

        restMockMvc
            .perform(post(API).with(patient()).contentType(MediaType.APPLICATION_JSON).content(identityJson()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accountId").doesNotExist());

        // The patient has their record, unlinked — the same state an unreachable gateway leaves, and one change unit
        // 004 repairs on the next start.
        Profile created = profileRepository.findOneByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(created.getAccountId()).isNull();
        assertThat(created.getPatientId()).isEqualTo(created.getId());

        // And the profile that held the account id still holds it. The index refused the second claim rather than
        // letting two profiles answer for one account.
        assertThat(profileRepository.findAll())
            .filteredOn(profile -> CONTESTED.equals(profile.getAccountId()))
            .singleElement()
            .satisfies(profile -> assertThat(profile.getEmail()).isEqualTo("other@example.test"));
    }

    // --- fixture --------------------------------------------------------------------------------------------------

    private static HttpServer startStubGateway() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                "/api/account",
                exchange -> {
                    byte[] body =
                        ("{\"id\":\"" + CONTESTED + "\",\"login\":\"ama\",\"email\":\"" + EMAIL + "\"}").getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
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
            "{\"firstName\":\"Ama\",\"lastName\":\"Mensah\",\"birthDate\":\"1990-04-02\",\"sex\":\"F\"," + "\"mobilePhone\":\"0244000000\"}"
        );
    }
}
