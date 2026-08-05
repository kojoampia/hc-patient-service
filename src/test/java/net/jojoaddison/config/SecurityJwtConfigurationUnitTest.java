package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Guards the startup check on the JWT signing key.
 *
 * <p>Until 2026-08-05 both backends shipped a committed 512-bit HMAC key in {@code application-prod.yml}. Removing it
 * is only half the fix: an environment that then forgets {@code JWT_BASE64_SECRET} must fail to start, not start with
 * a zero-length key and sign tokens anybody can forge. This asserts it fails, and that the message says what to
 * do — a startup error nobody can act on gets worked around, and the workaround is usually to put the key back.</p>
 */
class SecurityJwtConfigurationUnitTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "   " })
    void refusesToBuildAKeyWhenTheSecretIsMissing(String secret) {
        SecurityJwtConfiguration configuration = new SecurityJwtConfiguration();
        ReflectionTestUtils.setField(configuration, "jwtKey", secret);

        assertThatThrownBy(configuration::jwtEncoder)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("JWT_BASE64_SECRET")
            .hasMessageContaining("base64-secret");
    }

    @Test
    void buildsAnEncoderWhenTheSecretIsPresent() {
        SecurityJwtConfiguration configuration = new SecurityJwtConfiguration();
        // 512 bits, which is what HS512 requires; a shorter key is rejected by Nimbus rather than by us.
        ReflectionTestUtils.setField(
            configuration,
            "jwtKey",
            "G5oVDNDxRIh5PtBW0J+79wSUU4KuLJsDcXyo36DsTGIjSwVWjQBAXOXrCPDjf8RKqfghIRoBi2/H1IbqyAfasg=="
        );

        assertThatCode(configuration::jwtEncoder).doesNotThrowAnyException();
        assertThat(configuration.jwtEncoder()).isNotNull();
    }
}
