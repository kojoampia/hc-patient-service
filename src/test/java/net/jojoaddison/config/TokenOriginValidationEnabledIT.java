package net.jojoaddison.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.util.Base64;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The flag, switched on, through the whole HTTP stack.
 *
 * <p>{@code TokenOriginValidatorUnitTest} proves the decision table. This proves the wiring: that the validator is
 * actually attached to the decoder when the property is set, and — critically — that it is layered on top of the
 * default validators rather than replacing them. Handing a bare validator to {@code setJwtValidator} silently drops
 * the expiry check, which would be a considerably worse hole than the one being closed, and no unit test of the
 * validator itself could ever notice.</p>
 *
 * <p>Tokens are signed here with the same key the application is configured with, so signature verification passes
 * and the only thing under test is what the validators do afterwards.</p>
 */
@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "application.security.jwt.validate-origin=true" })
class TokenOriginValidationEnabledIT {

    @Autowired
    private MockMvc restMockMvc;

    @Value("${jhipster.security.authentication.jwt.base64-secret}")
    private String jwtKey;

    @Test
    void aTokenFromThisSubsystemIsAccepted() throws Exception {
        restMockMvc
            .perform(get("/api/allergies").header("Authorization", "Bearer " + token("hc-patient-gateway", "hc-patient", 3600)))
            .andExpect(status().isOk());
    }

    @Test
    void aTokenFromASiblingProductIsRejected() throws Exception {
        // Same signing key, valid signature, different product. This is the hole.
        restMockMvc
            .perform(get("/api/allergies").header("Authorization", "Bearer " + token("hc-admin-gateway", "hc-admin", 3600)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenWithoutTheClaimsIsRejected() throws Exception {
        // Every token minted before 2026-08-05. Exactly why the flag defaults to off.
        restMockMvc
            .perform(get("/api/allergies").header("Authorization", "Bearer " + token(null, null, 3600)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void expiryIsStillCheckedWithTheValidatorAttached() throws Exception {
        // The regression this class exists for: setJwtValidator REPLACES the validator, so attaching the origin
        // check without delegating to JwtValidators.createDefault() would stop expiry being enforced at all.
        restMockMvc
            .perform(get("/api/allergies").header("Authorization", "Bearer " + token("hc-patient-gateway", "hc-patient", -60)))
            .andExpect(status().isUnauthorized());
    }

    private String token(String issuer, String audience, long secondsUntilExpiry) {
        byte[] keyBytes = Base64.from(jwtKey).decode();
        SecretKey key = new SecretKeySpec(keyBytes, 0, keyBytes.length, MacAlgorithm.HS512.getName());
        Instant now = Instant.now();

        JwtClaimsSet.Builder claims = JwtClaimsSet
            .builder()
            .issuedAt(now.minus(120, ChronoUnit.SECONDS))
            .expiresAt(now.plus(secondsUntilExpiry, ChronoUnit.SECONDS))
            .subject("alice")
            .claim(SecurityUtils.AUTHORITIES_KEY, "ROLE_USER")
            .claim(SecurityUtils.EMAIL_KEY, "alice@example.com");
        if (issuer != null) {
            claims.issuer(issuer);
        }
        if (audience != null) {
            claims.audience(java.util.List.of(audience));
        }

        return new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(key))
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS512).build(), claims.build()))
            .getTokenValue();
    }
}
