package net.jojoaddison.config;

import static net.jojoaddison.security.SecurityUtils.AUTHORITIES_KEY;
import static net.jojoaddison.security.SecurityUtils.JWT_ALGORITHM;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import net.jojoaddison.management.SecurityMetersService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

@Configuration
public class SecurityJwtConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityJwtConfiguration.class);

    @Value("${jhipster.security.authentication.jwt.base64-secret}")
    private String jwtKey;

    /**
     * Origin-validation settings. Injected as typed properties rather than read with {@code @Value} because the
     * {@code application.*} prefix is bound strictly — an unknown key there fails context startup rather than being
     * ignored, so the binding has to be declared.
     */
    private final ApplicationProperties.Security.Jwt jwtProperties;

    public SecurityJwtConfiguration(ApplicationProperties applicationProperties) {
        this.jwtProperties = applicationProperties.getSecurity().getJwt();
    }

    @Bean
    public JwtDecoder jwtDecoder(SecurityMetersService metersService) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(getSecretKey()).macAlgorithm(JWT_ALGORITHM).build();
        if (jwtProperties.isValidateOrigin()) {
            // Layered on top of the default validators (expiry, not-before) rather than replacing them — passing a
            // bare validator to setJwtValidator would silently drop the expiry check, which is a far worse hole than
            // the one being closed.
            jwtDecoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefault(),
                    new TokenOriginValidator(jwtProperties.getTrustedIssuers(), jwtProperties.getAudience())
                )
            );
            LOG.info(
                "JWT origin validation is ON: issuers {} audience '{}'",
                jwtProperties.getTrustedIssuers(),
                jwtProperties.getAudience()
            );
        } else {
            LOG.info(
                "JWT origin validation is OFF. A token minted by any product sharing this signing key is accepted. " +
                "Enable with application.security.jwt.validate-origin=true once every issuer emits iss/aud."
            );
        }
        return token -> {
            try {
                return jwtDecoder.decode(token);
            } catch (Exception e) {
                if (e.getMessage().contains("Invalid signature")) {
                    metersService.trackTokenInvalidSignature();
                } else if (e.getMessage().contains("Jwt expired at")) {
                    metersService.trackTokenExpired();
                } else if (e.getMessage().contains("Malformed token")) {
                    metersService.trackTokenMalformed();
                }
                throw e;
            }
        };
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(getSecretKey()));
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthorityPrefix("");
        grantedAuthoritiesConverter.setAuthoritiesClaimName(AUTHORITIES_KEY);

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return jwtAuthenticationConverter;
    }

    private SecretKey getSecretKey() {
        // Fail here, loudly, rather than build a zero-length key and fail somewhere unreadable later. The committed
        // literal that used to live in application-prod.yml is gone (2026-08-05); production supplies the key through
        // JWT_BASE64_SECRET, and an environment that forgets to set it must not start rather than start insecurely.
        if (jwtKey == null || jwtKey.isBlank()) {
            throw new IllegalStateException(
                "jhipster.security.authentication.jwt.base64-secret is not set. Provide it via the " +
                "JWT_BASE64_SECRET environment variable (openssl rand -base64 64). There is deliberately no default " +
                "outside the dev and test profiles."
            );
        }
        byte[] keyBytes = Base64.from(jwtKey).decode();
        return new SecretKeySpec(keyBytes, 0, keyBytes.length, JWT_ALGORITHM.getName());
    }
}
