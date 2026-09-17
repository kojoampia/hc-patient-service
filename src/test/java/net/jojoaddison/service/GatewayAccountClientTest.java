package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.jojoaddison.config.ApplicationProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * {@link GatewayAccountClient} against a real socket.
 *
 * <h2>Why a stub server rather than a mocked RestClient</h2>
 *
 * <p>Half of what this class can get wrong is invisible to a mock: the query string the gateway's
 * {@code ALLOWED_ORDERED_PROPERTIES} will accept, whether the {@code Authorization} header is actually on the wire,
 * whether Jackson 3 binds the response into a {@code JsonNode} at all, and whether a refusal comes back as an empty
 * {@link Optional} instead of an exception. A mock would assert the calls this test was written expecting, which is
 * the shape of check this repository already has a catalogue of.</p>
 *
 * <p>The distinction these tests exist to pin is <strong>"could not ask" versus "there is nobody"</strong>. Both are
 * an empty result at a glance, and only one of them means the backfill should try again.</p>
 */
class GatewayAccountClientTest {

    private static final String TOKEN = "a.relayed.token";

    private HttpServer server;
    private final List<String> authorizationHeadersSeen = new CopyOnWriteArrayList<>();
    private final List<String> pathsSeen = new CopyOnWriteArrayList<>();
    private final List<String> queriesSeen = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startStubGateway() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void stopStubGateway() {
        server.stop(0);
    }

    // --- not configured -------------------------------------------------------------------------------------------

    @Test
    void aBlankBaseUrlIsTheOffSwitchAndOpensNoSocket() {
        GatewayAccountClient client = clientPointedAt("");

        assertThat(client.isConfigured()).isFalse();
        assertThat(client.accountIdOfCaller(TOKEN)).isEmpty();
        assertThat(client.accountDirectory(TOKEN)).isEmpty();
        assertThat(pathsSeen).isEmpty();
    }

    @Test
    void aBlankTokenIsNeverPutOnTheWire() {
        respond("/api/account", 200, "{\"id\":\"u-1\"}");

        assertThat(client().accountIdOfCaller("  ")).isEmpty();
        assertThat(pathsSeen).isEmpty();
    }

    // --- the caller's own account ---------------------------------------------------------------------------------

    @Test
    void theCallersOwnTokenIsRelayedVerbatimAndYieldsTheirAccountId() {
        respond("/api/account", 200, "{\"id\":\"u-42\",\"login\":\"ama\",\"email\":\"Ama@Example.test\"}");

        assertThat(client().accountIdOfCaller(TOKEN)).contains("u-42");
        assertThat(authorizationHeadersSeen).containsExactly("Bearer " + TOKEN);
        assertThat(pathsSeen).containsExactly("/api/account");
    }

    @Test
    void anAccountWithNoIdResolvesToNobodyRatherThanToBlank() {
        respond("/api/account", 200, "{\"login\":\"ama\",\"id\":\"   \"}");

        assertThat(client().accountIdOfCaller(TOKEN)).isEmpty();
    }

    @Test
    void aRefusalIsAnEmptyAnswerAndNotAnException() {
        respond("/api/account", 401, "");

        assertThat(client().accountIdOfCaller(TOKEN)).isEmpty();
    }

    @Test
    void anUnreachableGatewayIsAnEmptyAnswerAndNotAnException() {
        GatewayAccountClient client = client();
        server.stop(0);

        assertThat(client.accountIdOfCaller(TOKEN)).isEmpty();
    }

    // --- the account directory ------------------------------------------------------------------------------------

    @Test
    void theDirectoryIsPagedAndKeyedOnTheLowerCasedAddress() {
        // 100 on the first page is what makes the client ask for a second; a short page ends the loop.
        respond(
            "/api/admin/users",
            exchange -> {
                String query = exchange.getRequestURI().getQuery();
                if (query.contains("page=0")) {
                    return usersJson(IntStream.range(0, 100).mapToObj(i -> user("u-" + i, "Person" + i + "@Example.test")).toList());
                }
                return usersJson(List.of(user("u-last", "Kojo@JAC.net")));
            }
        );

        Optional<GatewayAccountClient.AccountDirectory> directory = client().accountDirectory(TOKEN);

        assertThat(directory).isPresent();
        assertThat(directory.orElseThrow().accountsSeen()).isEqualTo(101);
        assertThat(directory.orElseThrow().complete()).isTrue();
        assertThat(directory.orElseThrow().accountIdByEmail()).containsEntry("person0@example.test", "u-0");
        assertThat(directory.orElseThrow().accountIdByEmail()).containsEntry("kojo@jac.net", "u-last");
        assertThat(directory.orElseThrow().accountIdByEmail()).hasSize(101);
        // The sort is a contract with the gateway: an unsorted paged read over Mongo may repeat and skip rows, and
        // a property outside their ALLOWED_ORDERED_PROPERTIES makes the endpoint answer 400.
        assertThat(pathsSeen).hasSize(2);
        // getQuery() is the DECODED query, so this also pins that the comma survived as a comma. Spring encodes a
        // template's literal text under TEMPLATE_AND_VALUES; a `sort=id%2Casc` would be a different request.
        assertThat(queriesSeen).containsExactly("page=0&size=100&sort=id,asc", "page=1&size=100&sort=id,asc");
        assertThat(authorizationHeadersSeen).containsOnly("Bearer " + TOKEN);
    }

    @Test
    void anAddressHeldByTwoAccountsIsInNoMapAndIsCounted() {
        respond(
            "/api/admin/users",
            exchange ->
                usersJson(
                    List.of(user("u-1", "shared@example.test"), user("u-2", "shared@example.test"), user("u-3", "alone@example.test"))
                )
        );

        GatewayAccountClient.AccountDirectory directory = client().accountDirectory(TOKEN).orElseThrow();

        assertThat(directory.accountIdByEmail()).containsOnlyKeys("alone@example.test");
        assertThat(directory.ambiguousEmails()).isEqualTo(1);
        assertThat(directory.accountsSeen()).isEqualTo(3);
    }

    @Test
    void anEmptyGatewayIsAnEmptyDirectoryAndARefusedOneIsNoDirectoryAtAll() {
        respond("/api/admin/users", exchange -> "[]");
        GatewayAccountClient.AccountDirectory empty = client().accountDirectory(TOKEN).orElseThrow();
        assertThat(empty.accountIdByEmail()).isEmpty();
        assertThat(empty.accountsSeen()).isZero();

        // The distinction the backfill turns on: "there are no accounts" is a directory, "the service token was
        // refused" is not one, and only the second must leave the profiles outstanding for another run.
        pathsSeen.clear();
        respond("/api/admin/users", 403, "");
        assertThat(client().accountDirectory(TOKEN)).isEmpty();
    }

    // --- fixture --------------------------------------------------------------------------------------------------

    private GatewayAccountClient client() {
        return clientPointedAt("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private GatewayAccountClient clientPointedAt(String baseUrl) {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getGateway().setBaseUrl(baseUrl);
        properties.getGateway().setTimeoutSeconds(2);
        return new GatewayAccountClient(RestClient.builder(), properties);
    }

    private void respond(String path, int status, String body) {
        register(
            path,
            exchange -> {
                write(exchange, status, body);
            }
        );
    }

    private void respond(String path, BodyFor body) {
        register(path, exchange -> write(exchange, 200, body.apply(exchange)));
    }

    private void register(String path, HttpHandler handler) {
        try {
            // removeContext throws rather than answering "there was none", so a first registration has to catch.
            server.removeContext(path);
        } catch (IllegalArgumentException noSuchContext) {
            // nothing registered here yet
        }
        server.createContext(
            path,
            exchange -> {
                authorizationHeadersSeen.add(exchange.getRequestHeaders().getFirst("Authorization"));
                pathsSeen.add(exchange.getRequestURI().getPath());
                queriesSeen.add(exchange.getRequestURI().getQuery());
                handler.handle(exchange);
            }
        );
    }

    private static void write(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
        exchange.close();
    }

    private interface BodyFor {
        String apply(HttpExchange exchange) throws IOException;
    }

    private static String user(String id, String email) {
        return "{\"id\":\"" + id + "\",\"login\":\"" + id + "\",\"email\":\"" + email + "\",\"activated\":true}";
    }

    private static String usersJson(List<String> users) {
        return new ArrayList<>(users).stream().collect(Collectors.joining(",", "[", "]"));
    }
}
