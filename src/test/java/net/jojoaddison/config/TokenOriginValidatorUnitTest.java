package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Covers the check that tells the three Health Connect products apart.
 *
 * <p>They share one HMAC signing key, so signature verification cannot distinguish them: a token minted by hc-admin
 * verifies here perfectly, carrying hc-admin's authorities. Issuer and audience are the only thing that can, and this
 * is what enforces them.</p>
 */
class TokenOriginValidatorUnitTest {

    private static final String ISSUER = "hc-patient-gateway";
    private static final String AUDIENCE = "hc-patient";

    private final TokenOriginValidator validator = new TokenOriginValidator(List.of(ISSUER), AUDIENCE);

    @Test
    void acceptsATokenFromThisSubsystem() {
        assertThat(validate(ISSUER, List.of(AUDIENCE)).hasErrors()).isFalse();
    }

    @Test
    void rejectsATokenFromASiblingProduct() {
        // The whole point. Same key, same signature, different product.
        OAuth2TokenValidatorResult result = validate("hc-admin-gateway", List.of("hc-admin"));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(e -> "invalid_issuer".equals(e.getErrorCode()));
    }

    @Test
    void rejectsATokenAddressedToAnotherSubsystem() {
        // A trusted issuer can still mint a token meant for somewhere else.
        OAuth2TokenValidatorResult result = validate(ISSUER, List.of("hc-professional"));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(e -> "invalid_audience".equals(e.getErrorCode()));
    }

    @Test
    void rejectsATokenWithNoIssuerAtAll() {
        // Every token minted before 2026-08-05 looks like this, which is precisely why the validator ships disabled.
        assertThat(validate(null, List.of(AUDIENCE)).hasErrors()).isTrue();
    }

    @Test
    void rejectsATokenWithNoAudienceAtAll() {
        assertThat(validate(ISSUER, null).hasErrors()).isTrue();
    }

    @Test
    void acceptsAnAdditionallyTrustedIssuerWhenConfigured() {
        // The migration path: rather than waiting for a sibling to change, its issuer can be added to the trust list.
        TokenOriginValidator lenient = new TokenOriginValidator(List.of(ISSUER, "hc-professional-gateway"), AUDIENCE);
        Jwt token = jwt("hc-professional-gateway", List.of(AUDIENCE));

        assertThat(lenient.validate(token).hasErrors()).isFalse();
    }

    private OAuth2TokenValidatorResult validate(String issuer, List<String> audience) {
        return validator.validate(jwt(issuer, audience));
    }

    private static Jwt jwt(String issuer, List<String> audience) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "HS512").claim("sub", "alice");
        if (issuer != null) {
            builder.claim("iss", issuer);
        }
        if (audience != null) {
            builder.audience(audience);
        }
        return builder.build();
    }

    @Test
    void toleratesAClaimSetWithNothingButASubject() {
        // Defensive: the validator reads two claims and must not throw on a token that has neither.
        Jwt bare = new Jwt("token", null, null, Map.of("alg", "HS512"), Map.of("sub", "alice"));

        assertThat(validator.validate(bare).hasErrors()).isTrue();
    }
}
