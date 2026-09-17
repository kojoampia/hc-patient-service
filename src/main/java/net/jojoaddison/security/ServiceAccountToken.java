package net.jojoaddison.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Mints the one token this service issues, for the one job that has no caller to borrow a token from.
 *
 * <h2>Read this before adding a second call site</h2>
 *
 * <p>This service validates tokens; it is not an issuer, and it should stay that way. The only thing here that needs
 * a token of its own is change unit {@code 004}, the {@code Profile.accountId} backfill: it runs at startup, there is
 * no request and no caller, and the one endpoint in the estate that maps an address to a {@code User.id} is
 * {@code GET /api/admin/users}, which is {@code ROLE_ADMIN}. Every other cross-stack read this product makes relays
 * the caller's own token instead — see {@link net.jojoaddison.service.GatewayAccountClient}, whose live path uses
 * {@code /api/account} precisely so that it needs no privilege at all.</p>
 *
 * <p><strong>What this costs, said plainly.</strong> The three gateways share one HMAC signing key, so a token minted
 * here with {@code ROLE_ADMIN} is an administrator in hc-patient, hc-admin and hc-professional alike. That is not a
 * new capability — a symmetric key that can verify is a key that can sign, and this service has held it since it was
 * generated, with a {@code JwtEncoder} bean sitting unused in {@code SecurityJwtConfiguration} since the scaffold —
 * but it is the first time anything uses it, and a capability nobody exercises is a capability nobody has to reason
 * about. Four things narrow it, and all four are load-bearing:</p>
 *
 * <ol>
 *   <li><strong>{@link #TTL} is sixty seconds.</strong> Long enough for a paged read of the account directory on a
 *       cold gateway, short enough that a token captured in flight is worthless by the time anybody looks at it.</li>
 *   <li><strong>It is minted only when there is work to do.</strong> {@code 004} counts the profiles with no account
 *       id first and returns before asking for a token when the answer is zero — which is every start after the
 *       backfill has run. A steady-state deployment mints none of these, ever.</li>
 *   <li><strong>It is never logged, returned, or persisted.</strong> The value lives in a local variable and an
 *       {@code Authorization} header. {@code GatewayAccountClient} logs exception <em>types</em> for the same
 *       reason.</li>
 *   <li><strong>It does not claim to come from the gateway.</strong> {@link #ISSUER} is this service's own name, not
 *       {@code hc-patient-gateway}, so the moment anybody enables {@code application.security.jwt.validate-origin}
 *       with its default trusted-issuer list, this token is <em>refused</em> rather than quietly honoured. The
 *       backfill then logs a 401 and retries on the next start, and re-enabling it is somebody adding
 *       {@code hc-patient-service} to a trust list deliberately rather than inheriting it. A token that lied about
 *       its issuer would survive that switch, which is the whole argument for not writing one.</li>
 * </ol>
 *
 * <p>The alternative that was not taken: a service account with a login and a password, authenticated through
 * {@code POST /api/authenticate}. It needs a second secret distributed to three environments, and a real account with
 * {@code ROLE_ADMIN} that exists for as long as the deployment does — strictly worse than a credential that lives for
 * a minute and is derived from a key both ends already share.</p>
 */
@Component
public class ServiceAccountToken {

    /** Long enough to page the account directory on a cold gateway, and no longer. */
    static final long TTL_SECONDS = 60;

    /**
     * Deliberately <em>not</em> {@code hc-patient-gateway}. See the class comment: naming this service is what makes
     * origin validation refuse the token rather than accept it.
     */
    static final String ISSUER = "hc-patient-service";

    /** What the gateway's {@code TokenOriginValidator} requires of a token presented to it. */
    static final String AUDIENCE = "hc-patient";

    /** Not a login. Nothing resolves it to an account, here or at the gateway, and an access log should say so. */
    static final String SUBJECT = "system:profile-account-backfill";

    private final JwtEncoder jwtEncoder;

    public ServiceAccountToken(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    /**
     * A sixty-second {@code ROLE_ADMIN} token for the {@code accountId} backfill, and for nothing else.
     *
     * <p>No {@code email} claim, on purpose. Were this token ever replayed against <em>this</em> service,
     * {@code PatientScope} would resolve it to no patient — which changes nothing for an administrator, who is
     * unrestricted anyway, and costs nothing to write.</p>
     *
     * @return the serialized token. Never log it.
     */
    public String administratorForAccountBackfill() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet
            .builder()
            .issuer(ISSUER)
            .audience(List.of(AUDIENCE))
            .subject(SUBJECT)
            .issuedAt(now)
            .expiresAt(now.plus(TTL_SECONDS, ChronoUnit.SECONDS))
            // A space-delimited string, which is the shape the gateway's JwtGrantedAuthoritiesConverter reads and
            // the shape its own AuthenticateController writes. One authority, so there is nothing to delimit.
            .claim(SecurityUtils.AUTHORITIES_KEY, AuthoritiesConstants.ADMIN)
            .build();
        JwsHeader header = JwsHeader.with(SecurityUtils.JWT_ALGORITHM).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
