package net.jojoaddison.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.util.Base64;
import java.time.Duration;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import net.jojoaddison.config.TokenOriginValidator;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * The one token this service issues.
 *
 * <p>Two things are being pinned, and the second is the one worth having. That the token is valid and carries
 * {@code ROLE_ADMIN} is the easy half — <strong>the interesting half is that it is refused by a decoder configured
 * the way this estate intends to configure one</strong>, because it does not claim to come from the gateway. That
 * property is the whole safety argument in {@link ServiceAccountToken}'s comment, and a javadoc asserting behaviour
 * the code does not have is a defect this repository has catalogued more than once.</p>
 */
class ServiceAccountTokenTest {

    /** The committed development key, which is public by construction and the same in both repos. */
    private static final String KEY = "G5oVDNDxRIh5PtBW0J+79wSUU4KuLJsDcXyo36DsTGIjSwVWjQBAXOXrCPDjf8RKqfghIRoBi2/H1IbqyAfasg==";

    private final ServiceAccountToken tokens = new ServiceAccountToken(new NimbusJwtEncoder(new ImmutableSecret<>(secretKey())));

    @Test
    void itIsSignedWithTheSharedKeyAndCarriesAdministratorAndNothingElse() {
        Jwt token = decode(tokens.administratorForAccountBackfill());

        assertThat(token.getClaimAsString(SecurityUtils.AUTHORITIES_KEY)).isEqualTo(AuthoritiesConstants.ADMIN);
        assertThat(token.getSubject()).isEqualTo(ServiceAccountToken.SUBJECT);
        // No email claim, so were it ever replayed against THIS service PatientScope would resolve it to no patient.
        assertThat(token.getClaimAsString(SecurityUtils.EMAIL_KEY)).isNull();
    }

    @Test
    void itLivesForOneMinute() {
        Jwt token = decode(tokens.administratorForAccountBackfill());

        Duration life = Duration.between(token.getIssuedAt(), token.getExpiresAt());
        assertThat(life).isEqualTo(Duration.ofSeconds(ServiceAccountToken.TTL_SECONDS)).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void itDoesNotClaimToComeFromTheGatewayAndIsRefusedOnceOriginValidationIsOn() {
        Jwt token = decode(tokens.administratorForAccountBackfill());

        assertThat(token.getClaimAsString("iss")).isEqualTo("hc-patient-service").isNotEqualTo("hc-patient-gateway");
        assertThat(token.getAudience()).containsExactly("hc-patient");

        // The default trust list — ApplicationProperties.Security.Jwt.trustedIssuers — names the gateway and nothing
        // else. So switching validation on refuses this token rather than quietly honouring a self-minted
        // administrator, and re-admitting it is somebody adding this service to a trust list on purpose.
        OAuth2TokenValidatorResult asTheEstateWouldValidateIt = new TokenOriginValidator(List.of("hc-patient-gateway"), "hc-patient")
            .validate(token);
        assertThat(asTheEstateWouldValidateIt.hasErrors()).isTrue();
        assertThat(asTheEstateWouldValidateIt.getErrors()).extracting("errorCode").containsExactly("invalid_issuer");

        // The positive control, so the refusal above is a finding rather than a validator that refuses everything:
        // the same validator accepts the token once this service is trusted.
        assertThat(new TokenOriginValidator(List.of("hc-patient-service"), "hc-patient").validate(token).hasErrors()).isFalse();
    }

    private static Jwt decode(String token) {
        JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey()).macAlgorithm(SecurityUtils.JWT_ALGORITHM).build();
        return decoder.decode(token);
    }

    private static SecretKey secretKey() {
        byte[] keyBytes = Base64.from(KEY).decode();
        return new SecretKeySpec(keyBytes, 0, keyBytes.length, SecurityUtils.JWT_ALGORITHM.getName());
    }
}
