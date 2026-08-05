package net.jojoaddison.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import net.jojoaddison.config.Constants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Covers {@link AuditStamp} and the email-claim reader it sits beside.
 *
 * <p>The fallbacks are the interesting part. Both must resolve to "nobody in particular" rather than to something a
 * caller supplied — {@code system} for the audit trail, empty for the patient scope — and both are reached by paths
 * an end-to-end test does not exercise: a Mongock migration or a Kafka consumer running with no security context, and
 * a token minted before the email claim existed.</p>
 */
class AuditStampUnitTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void stampsTheAuthenticatedLogin() {
        authenticateWithClaims(Map.of("sub", "alice", SecurityUtils.EMAIL_KEY, "alice@example.com"));

        assertThat(AuditStamp.currentUser()).isEqualTo("alice");
    }

    @Test
    void fallsBackToSystemWhenThereIsNoAuthentication() {
        // A migration or a Kafka consumer. Never the caller's value, because there is no caller.
        SecurityContextHolder.clearContext();

        assertThat(AuditStamp.currentUser()).isEqualTo(Constants.SYSTEM);
    }

    @Test
    void stampsTodaysDate() {
        assertThat(AuditStamp.today()).isEqualTo(LocalDate.now());
    }

    @Test
    void readsTheEmailClaim() {
        authenticateWithClaims(Map.of("sub", "alice", SecurityUtils.EMAIL_KEY, "alice@example.com"));

        assertThat(SecurityUtils.getCurrentUserEmail()).contains("alice@example.com");
    }

    @Test
    void treatsAMissingEmailClaimAsNobody() {
        authenticateWithClaims(Map.of("sub", "alice"));

        assertThat(SecurityUtils.getCurrentUserEmail()).isEmpty();
    }

    @Test
    void treatsABlankEmailClaimAsNobody() {
        // The gateway emits "" for an account with no email address rather than omitting the claim.
        authenticateWithClaims(Map.of("sub", "alice", SecurityUtils.EMAIL_KEY, "   "));

        assertThat(SecurityUtils.getCurrentUserEmail()).isEmpty();
    }

    @Test
    void treatsANonJwtPrincipalAsNobody() {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("alice", "x", List.of()));
        SecurityContextHolder.setContext(context);

        assertThat(SecurityUtils.getCurrentUserEmail()).isEmpty();
        // The login still resolves — it is only the claim that needs a bearer token.
        assertThat(AuditStamp.currentUser()).isEqualTo("alice");
    }

    @Test
    void treatsAnUnauthenticatedContextAsNobody() {
        SecurityContextHolder.clearContext();

        assertThat(SecurityUtils.getCurrentUserEmail()).isEmpty();
    }

    @Test
    void readsAPlainStringPrincipal() {
        // Spring hands a bare String principal for an anonymous or pre-authenticated request.
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
            new UsernamePasswordAuthenticationToken("service-account", "x", List.of()) {
                @Override
                public Object getPrincipal() {
                    return "service-account";
                }
            }
        );
        SecurityContextHolder.setContext(context);

        assertThat(SecurityUtils.getCurrentUserLogin()).contains("service-account");
    }

    @Test
    void reportsNoAuthoritiesAndNoAuthenticationOnAnEmptyContext() {
        SecurityContextHolder.clearContext();

        assertThat(SecurityUtils.isAuthenticated()).isFalse();
        assertThat(SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.ADMIN)).isFalse();
    }

    private static void authenticateWithClaims(Map<String, Object> claims) {
        Jwt jwt = new Jwt("token", null, null, Map.of("alg", "HS512"), claims);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        SecurityContextHolder.setContext(context);
    }
}
