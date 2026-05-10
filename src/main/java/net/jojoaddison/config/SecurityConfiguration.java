package net.jojoaddison.config;

import net.jojoaddison.security.AuthoritiesConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;
import tech.jhipster.config.JHipsterProperties;

@Configuration
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfiguration {

    @SuppressWarnings("unused")
    private final JHipsterProperties jHipsterProperties;

    public SecurityConfiguration(JHipsterProperties jHipsterProperties) {
        this.jHipsterProperties = jHipsterProperties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, HandlerMappingIntrospector introspector) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authz ->
                // prettier-ignore
                authz
                    .requestMatchers(new MvcRequestMatcher(introspector, HttpMethod.POST, "/api/authenticate")).permitAll()
                    .requestMatchers(new MvcRequestMatcher(introspector, HttpMethod.GET, "/api/authenticate")).permitAll()
                    .requestMatchers(new MvcRequestMatcher(introspector, "/api/admin/**")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(new MvcRequestMatcher(introspector, "/api/**")).authenticated()
                    .requestMatchers(new MvcRequestMatcher(introspector, "/v3/api-docs/**")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(new MvcRequestMatcher(introspector, "/management/health")).permitAll()
                    .requestMatchers(new MvcRequestMatcher(introspector, "/management/health/**")).permitAll()
                    .requestMatchers(new MvcRequestMatcher(introspector, "/management/info")).permitAll()
                    .requestMatchers(new MvcRequestMatcher(introspector, "/management/prometheus")).permitAll()
                    .requestMatchers(new MvcRequestMatcher(introspector, "/management/**")).hasAuthority(AuthoritiesConstants.ADMIN)
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions ->
                exceptions
                    .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                    .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
