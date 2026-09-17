package net.jojoaddison.service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.jojoaddison.config.ApplicationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * Asks the patient gateway what a {@code User.id} is, because nothing in this service knows.
 *
 * <h2>Why this service cannot answer the question itself</h2>
 *
 * <p>This is the only account identifier in the estate, and it is minted somewhere else. This service runs with
 * {@code skipUserManagement}, so it has no {@code User} collection; the gateway's token carries {@code auth} and
 * {@code email} and nothing that names an account; and the gateway never calls here. There are exactly two ways for
 * a {@code User.id} to reach this JVM — ask the gateway for it, or have the gateway put it in the token — and the
 * second is a change in a different repository. This class is the first. Backlog item 44.</p>
 *
 * <h2>Two reads, two credentials, and the split is the point</h2>
 *
 * <pre>
 *   accountIdOfCaller  -&gt; GET /api/account      with THE CALLER'S OWN token   (authenticated; names nobody else)
 *   accountDirectory   -&gt; GET /api/admin/users  with a service token          (ROLE_ADMIN; names everybody)
 * </pre>
 *
 * <p>The live path relays, exactly as hc-admin's {@code PatientServiceClient} relays: the three gateways share one
 * signing key, so a token this service received is accepted over there, and {@code /api/account} resolves the caller
 * from it and can name nobody else. <strong>An onboarding patient asking who they are needs no privilege at all</strong>,
 * which is why the expensive credential appears only in the backfill, where there is no caller to borrow from. See
 * {@link net.jojoaddison.security.ServiceAccountToken} for what that costs and how it is narrowed.</p>
 *
 * <p><strong>This class mints nothing and holds no credential.</strong> Every method takes the bearer token to use.
 * That is deliberate: a client that could mint its own identity would be one call site away from using the admin
 * token on the patient-facing path, which is the failure hc-admin's client writes two paragraphs warning against.</p>
 *
 * <h2>Nothing here throws, and nothing here logs an address</h2>
 *
 * <p>Every caller has a "could not ask" branch that is a correct outcome — a profile with no account id yet, or a
 * backfill that retries on the next start — so a failure is an empty {@link Optional} rather than an exception.
 * Failures are logged by exception <em>type</em>, never by message: Spring's {@code ResourceAccessException} quotes
 * the request URL, and that is how hc-admin leaked a patient's email address into an unauthenticated Loki. No URL
 * here carries one, and the rule is kept anyway so the next method added cannot be the one that does.</p>
 */
@Service
public class GatewayAccountClient {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayAccountClient.class);

    /** The gateway's own account read. Authenticated, and it resolves the caller from their token. */
    private static final String ACCOUNT = "/api/account";

    /** JHipster's {@code UserResource}. {@code ROLE_ADMIN}, and the only email-to-id source in the estate. */
    private static final String ADMIN_USERS = "/api/admin/users";

    /**
     * Sorted by {@code id}, because an unsorted paged read over Mongo may repeat and skip rows between pages.
     * {@code id} is in the gateway's {@code ALLOWED_ORDERED_PROPERTIES}; a property that is not there makes the
     * endpoint answer 400, so this string is a contract with their side and not a preference.
     */
    private static final String ADMIN_USERS_PAGE = ADMIN_USERS + "?page={page}&size={size}&sort=id,asc";

    private static final int PAGE_SIZE = 100;

    /**
     * A ceiling on the paging loop, so a gateway that answers a full page forever cannot hang application startup.
     * 100 pages of 100 is 10,000 accounts, comfortably more than this product has; exceeding it is reported rather
     * than silently truncated, and the profiles it failed to reach simply stay outstanding for the next run.
     */
    private static final int MAX_PAGES = 100;

    /** Null when no gateway is configured, which {@link #isConfigured()} is the only reader of. */
    private final RestClient restClient;

    public GatewayAccountClient(RestClient.Builder builder, ApplicationProperties properties) {
        ApplicationProperties.Gateway config = properties.getGateway();
        String baseUrl = config.getBaseUrl() == null ? "" : config.getBaseUrl().trim();
        if (baseUrl.isEmpty()) {
            this.restClient = null;
            LOG.info("No patient gateway is configured (application.gateway.base-url); profiles will carry no account id");
            return;
        }
        int timeoutSeconds = config.getTimeoutSeconds();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSeconds)).build()
        );
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = builder.baseUrl(baseUrl).requestFactory(factory).build();
        LOG.info("Patient gateway account lookups -> {} (timeout={}s)", baseUrl, timeoutSeconds);
    }

    /**
     * Every account the gateway holds, keyed by lower-cased email.
     *
     * @param accountsSeen how many accounts were read, including the ones no key survived for.
     * @param ambiguousEmails how many addresses were held by more than one account; none of them is in the map.
     * @param complete false when the paging loop hit {@link #MAX_PAGES} before the gateway ran out of accounts.
     */
    public record AccountDirectory(Map<String, String> accountIdByEmail, int accountsSeen, int ambiguousEmails, boolean complete) {}

    /** Whether this deployment has a gateway to ask at all. */
    public boolean isConfigured() {
        return restClient != null;
    }

    /**
     * The {@code User.id} of whoever this token belongs to.
     *
     * <p>{@code GET /api/account} rather than a lookup by login or by address: it takes no subject, so it can only
     * ever answer about the caller. That is the same rule this product applies to its own endpoints — a read that
     * names a subject is administrative, a read that cannot is not — and it means the live path needs no privilege
     * this service would otherwise have to hold.</p>
     *
     * @param bearerToken the caller's own token, relayed verbatim.
     * @return the account id, or empty when the gateway is not configured, could not be reached, refused, or
     *         answered without one.
     */
    public Optional<String> accountIdOfCaller(String bearerToken) {
        if (restClient == null || bearerToken == null || bearerToken.isBlank()) {
            return Optional.empty();
        }
        try {
            ResponseEntity<JsonNode> response = restClient
                .get()
                .uri(ACCOUNT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                // No default status handler: an error is an outcome here, and letting RestClient throw would put
                // "the gateway said no" and "the gateway is not there" in one catch block.
                .onStatus(HttpStatusCode::isError, (request, errorResponse) -> {})
                .toEntity(JsonNode.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                LOG.warn(
                    "The gateway answered {} for GET {}; this profile will carry no account id yet",
                    response.getStatusCode().value(),
                    ACCOUNT
                );
                return Optional.empty();
            }
            return text(response.getBody(), "id");
        } catch (RestClientException e) {
            LOG.warn("The gateway could not be reached for GET {}: {} caused by {}", ACCOUNT, e.getClass().getSimpleName(), causeOf(e));
            return Optional.empty();
        }
    }

    /**
     * Every account, by address, for the one caller that has no caller: change unit {@code 004}.
     *
     * <p>Read whole rather than one lookup per profile. There is no by-email read on the gateway — {@code /api/admin/users/{login}}
     * takes a login, and this service does not hold logins — so a per-profile form would mean a page read per profile
     * anyway. One pass also means one service token for the whole backfill rather than one per record.</p>
     *
     * <p><strong>An address held by two accounts is in no map.</strong> Linking a profile to either would be picking
     * one on no evidence, and the identifier picked is the one hc-admin will use to name a person. They are counted
     * so the migration's log can say they were seen and passed over — the same reading
     * {@code SinglePendingMembershipMigration} gives an ownerless membership.</p>
     *
     * @param bearerToken a token holding {@code ROLE_ADMIN} at the gateway.
     * @return the directory, or empty when the gateway is not configured, could not be reached, or refused. Empty
     *         means <em>could not ask</em>, never <em>there are no accounts</em>.
     */
    public Optional<AccountDirectory> accountDirectory(String bearerToken) {
        if (restClient == null || bearerToken == null || bearerToken.isBlank()) {
            return Optional.empty();
        }
        Map<String, String> byEmail = new LinkedHashMap<>();
        int seen = 0;
        int ambiguous = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            Optional<JsonNode> body = readUserPage(bearerToken, page);
            if (body.isEmpty()) {
                return Optional.empty();
            }
            JsonNode users = body.orElseThrow();
            if (!users.isArray()) {
                LOG.warn("The gateway answered GET {} with {} rather than an array", ADMIN_USERS, users.getClass().getSimpleName());
                return Optional.empty();
            }
            for (JsonNode user : users) {
                seen++;
                Optional<String> id = text(user, "id");
                Optional<String> email = text(user, "email").map(value -> value.toLowerCase(Locale.ROOT));
                if (id.isEmpty() || email.isEmpty()) {
                    continue;
                }
                // put-then-check rather than containsKey: the second sighting has to remove the first, or the
                // earlier account wins an address neither of them can prove is theirs.
                String previous = byEmail.put(email.orElseThrow(), id.orElseThrow());
                if (previous != null && !previous.equals(id.orElseThrow())) {
                    byEmail.remove(email.orElseThrow());
                    ambiguous++;
                }
            }
            if (users.size() < PAGE_SIZE) {
                return Optional.of(new AccountDirectory(byEmail, seen, ambiguous, true));
            }
        }
        LOG.warn("Stopped reading {} after {} pages of {}; the directory is incomplete", ADMIN_USERS, MAX_PAGES, PAGE_SIZE);
        return Optional.of(new AccountDirectory(byEmail, seen, ambiguous, false));
    }

    private Optional<JsonNode> readUserPage(String bearerToken, int page) {
        try {
            ResponseEntity<JsonNode> response = restClient
                .get()
                .uri(ADMIN_USERS_PAGE, page, PAGE_SIZE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, errorResponse) -> {})
                .toEntity(JsonNode.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                // 401 and 403 are the ones worth recognising on sight: the service token is refused, which is what
                // enabling origin validation at the gateway would do. See ServiceAccountToken.
                LOG.warn("The gateway answered {} for GET {} page {}", response.getStatusCode().value(), ADMIN_USERS, page);
                return Optional.empty();
            }
            return Optional.ofNullable(response.getBody());
        } catch (RestClientException e) {
            LOG.warn(
                "The gateway could not be reached for GET {} page {}: {} caused by {}",
                ADMIN_USERS,
                page,
                e.getClass().getSimpleName(),
                causeOf(e)
            );
            return Optional.empty();
        }
    }

    /** A non-blank string field, or empty. Blank counts as absent, the reading this codebase applies everywhere. */
    private static Optional<String> text(JsonNode node, String field) {
        if (node == null) {
            return Optional.empty();
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return Optional.empty();
        }
        String asText = value.asString().trim();
        return asText.isEmpty() ? Optional.empty() : Optional.of(asText);
    }

    private static String causeOf(Exception e) {
        return e.getCause() == null ? "nothing further" : e.getCause().getClass().getSimpleName();
    }
}
