package net.jojoaddison.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties specific to Hc Patient Service.
 * <p>
 * Properties are configured in the {@code application.yml} file.
 * See {@link tech.jhipster.config.JHipsterProperties} for a good example.
 */
@ConfigurationProperties(prefix = "application", ignoreUnknownFields = false)
public class ApplicationProperties {

    private final Security security = new Security();

    private final Gateway gateway = new Gateway();

    public Security getSecurity() {
        return security;
    }

    public Gateway getGateway() {
        return gateway;
    }

    /**
     * How this service reaches the patient gateway.
     *
     * <p>This service has had no outbound HTTP at all until now — it validates tokens and serves documents. It grew
     * one because the gateway is the only thing in the estate that knows an account's {@code User.id}, and
     * {@code Profile.accountId} is that value (backlog item 44).</p>
     */
    public static class Gateway {

        /**
         * Where the patient gateway answers, or blank for "do not ask".
         *
         * <p><strong>Blank is the default and it is the off switch.</strong> Every use of this client is optional by
         * construction — {@code OnboardingService} writes a profile with no account id and change unit {@code 004}
         * says it has work outstanding — so an environment that has not configured a gateway degrades rather than
         * fails. It is blank here and set per profile: {@code application-dev.yml} points at the developer's
         * gateway, {@code application-prod.yml} at the compose service. It is deliberately blank for
         * {@code src/test/resources/config/application.yml}, so no integration test opens a socket at startup; the
         * tests that exercise the client point it at a stub of their own.</p>
         */
        private String baseUrl = "";

        /**
         * Connect and read timeout, in seconds.
         *
         * <p>Both, for the reason hc-admin's clients record: the connect timeout lives on the {@code HttpClient} and
         * the read timeout on the request factory, and setting only the second leaves the connect side unbounded — a
         * sibling that accepts nothing hangs a thread as effectively as one that answers nothing. Three seconds
         * rather than five: the live call sits inside {@code POST /api/onboarding}, which a patient is waiting on.</p>
         */
        private int timeoutSeconds = 3;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    /**
     * Security settings this application owns, as distinct from the ones JHipsterProperties owns.
     *
     * <p>They live here rather than under {@code jhipster.security.*} because JHipsterProperties binds with
     * {@code ignoreUnknownFields = false}: an extra key under its prefix is not ignored, it fails context startup with
     * an unbound-property error. This class has the same strictness, which is why the nested types below exist rather
     * than the properties being read with a bare {@code @Value}.</p>
     */
    public static class Security {

        private final Jwt jwt = new Jwt();

        public Jwt getJwt() {
            return jwt;
        }

        public static class Jwt {

            /**
             * Whether to reject tokens minted for a different Health Connect product.
             *
             * <p>Off by default, and that default is load-bearing — see {@link TokenOriginValidator}. Turning it on
             * rejects every token that lacks {@code iss}/{@code aud}, which is every token in flight at the moment it
             * is switched on, and every token a sibling product issues until it emits its own.</p>
             */
            private boolean validateOrigin = false;

            /** Issuers whose tokens this service accepts, once {@link #validateOrigin} is on. */
            private List<String> trustedIssuers = List.of("hc-patient-gateway");

            /** The audience a token must name to be accepted here. */
            private String audience = "hc-patient";

            public boolean isValidateOrigin() {
                return validateOrigin;
            }

            public void setValidateOrigin(boolean validateOrigin) {
                this.validateOrigin = validateOrigin;
            }

            public List<String> getTrustedIssuers() {
                return trustedIssuers;
            }

            public void setTrustedIssuers(List<String> trustedIssuers) {
                this.trustedIssuers = trustedIssuers;
            }

            public String getAudience() {
                return audience;
            }

            public void setAudience(String audience) {
                this.audience = audience;
            }
        }
    }
    // jhipster-needle-application-properties-property
    // jhipster-needle-application-properties-property-getter
    // jhipster-needle-application-properties-property-class
}
