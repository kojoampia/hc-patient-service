package net.jojoaddison.security;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Utility class for Spring Security.
 */
public final class SecurityUtils {

    public static final MacAlgorithm JWT_ALGORITHM = MacAlgorithm.HS512;

    public static final String AUTHORITIES_KEY = "auth";

    /**
     * Claim carrying the account's email address, minted by the gateway.
     *
     * <p>This service has no user management of its own, so email is the only identifier it shares with the gateway.
     * It is the root of the authorization chain: email -> Profile -> patientId -> every query. Must stay in step with
     * {@code SecurityUtils.EMAIL_KEY} in the gateway.</p>
     */
    public static final String EMAIL_KEY = "email";

    private SecurityUtils() {}

    /**
     * The email address of the current user, as asserted by the gateway-issued token.
     *
     * <p>Empty when the request is unauthenticated, when the principal is not a JWT, or when the token predates this
     * claim — all of which must be treated as "no patient records", never as "all of them".</p>
     *
     * @return the email of the current user.
     */
    public static Optional<String> getCurrentUserEmail() {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        return Optional
            .ofNullable(securityContext.getAuthentication())
            .map(Authentication::getPrincipal)
            .filter(Jwt.class::isInstance)
            .map(Jwt.class::cast)
            .map(jwt -> jwt.getClaimAsString(EMAIL_KEY))
            .filter(email -> !email.isBlank());
    }

    /**
     * Get the login of the current user.
     *
     * @return the login of the current user.
     */
    public static Optional<String> getCurrentUserLogin() {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        return Optional.ofNullable(extractPrincipal(securityContext.getAuthentication()));
    }

    private static String extractPrincipal(Authentication authentication) {
        if (authentication == null) {
            return null;
        } else if (authentication.getPrincipal() instanceof UserDetails springSecurityUser) {
            return springSecurityUser.getUsername();
        } else if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        } else if (authentication.getPrincipal() instanceof String s) {
            return s;
        }
        return null;
    }

    /**
     * Get the JWT of the current user.
     *
     * @return the JWT of the current user.
     */
    public static Optional<String> getCurrentUserJWT() {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        return Optional
            .ofNullable(securityContext.getAuthentication())
            .filter(authentication -> authentication.getCredentials() instanceof String)
            .map(authentication -> (String) authentication.getCredentials());
    }

    /**
     * The raw bearer token this request arrived with, whatever shape the authentication is in.
     *
     * <h2>Why this exists beside {@link #getCurrentUserJWT()} rather than replacing it</h2>
     *
     * <p>Backlog item 44. {@link net.jojoaddison.service.GatewayAccountClient} relays the caller's own token to the
     * patient gateway, and {@code getCurrentUserJWT()} cannot supply it: it filters credentials on
     * {@code instanceof String}, and this service's resource-server chain produces a {@code JwtAuthenticationToken}
     * whose credentials are the <em>decoded</em> {@link Jwt}. So that method returns empty on every real request
     * here — silently, because "no token" is a legitimate state on an unauthenticated one and every caller has a
     * branch for it. A relay that never relays looks exactly like a deployment that is not configured for one.</p>
     *
     * <p>Both readings are kept rather than one, so this is safe to adopt anywhere the older method is called: the
     * {@code String} branch preserves whatever behaviour a caller has today, and the {@code JwtAuthenticationToken}
     * branch is the one that fires in production. hc-admin's {@code SecurityUtils} carries the same pair under the
     * same name, for the same reason.</p>
     *
     * @return the serialized token, or empty when there is no authentication or it carries no token.
     */
    public static Optional<String> getCurrentRequestJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        if (authentication.getCredentials() instanceof String token && !token.isBlank()) {
            return Optional.of(token);
        }
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return Optional.ofNullable(jwtAuthentication.getToken()).map(Jwt::getTokenValue).filter(token -> !token.isBlank());
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return Optional.ofNullable(jwt.getTokenValue()).filter(token -> !token.isBlank());
        }
        return Optional.empty();
    }

    /**
     * Check if a user is authenticated.
     *
     * @return true if the user is authenticated, false otherwise.
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && getAuthorities(authentication).noneMatch(AuthoritiesConstants.ANONYMOUS::equals);
    }

    /**
     * Checks if the current user has any of the authorities.
     *
     * @param authorities the authorities to check.
     * @return true if the current user has any of the authorities, false otherwise.
     */
    public static boolean hasCurrentUserAnyOfAuthorities(String... authorities) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (
            authentication != null && getAuthorities(authentication).anyMatch(authority -> Arrays.asList(authorities).contains(authority))
        );
    }

    /**
     * Checks if the current user has none of the authorities.
     *
     * @param authorities the authorities to check.
     * @return true if the current user has none of the authorities, false otherwise.
     */
    public static boolean hasCurrentUserNoneOfAuthorities(String... authorities) {
        return !hasCurrentUserAnyOfAuthorities(authorities);
    }

    /**
     * Checks if the current user has a specific authority.
     *
     * @param authority the authority to check.
     * @return true if the current user has the authority, false otherwise.
     */
    public static boolean hasCurrentUserThisAuthority(String authority) {
        return hasCurrentUserAnyOfAuthorities(authority);
    }

    /**
     * Every authority on the current token.
     *
     * <p>Needed because {@link ScopeOfPractice} answers questions about the caller's authorities <em>as a set</em> —
     * "may any of these write a medication" — which cannot be asked one string at a time without the caller already
     * knowing which strings to ask about, and that knowledge is what the table exists to hold.</p>
     *
     * @return the authorities, or an empty set when nobody is authenticated.
     */
    public static Set<String> getCurrentUserAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? Set.of() : getAuthorities(authentication).collect(Collectors.toSet());
    }

    private static Stream<String> getAuthorities(Authentication authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority);
    }
}
