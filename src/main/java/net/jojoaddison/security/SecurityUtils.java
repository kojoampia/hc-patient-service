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
