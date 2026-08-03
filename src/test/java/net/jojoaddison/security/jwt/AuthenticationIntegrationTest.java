package net.jojoaddison.security.jwt;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.jojoaddison.config.SecurityConfiguration;
import net.jojoaddison.config.SecurityJwtConfiguration;
import net.jojoaddison.config.WebConfigurer;
import net.jojoaddison.management.SecurityMetersService;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import tech.jhipster.config.JHipsterProperties;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
// This context lists its configuration classes explicitly, so it gets no auto-configuration. Under Spring Boot 4 that
// leaves no HttpSecurity for SecurityConfiguration#filterChain to take as a parameter, so the one auto-configuration
// that supplies it is imported by hand. Boot 4 renamed the class from SecurityAutoConfiguration; the gateway needs the
// reactive twin, ReactiveWebSecurityAutoConfiguration.
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
@SpringBootTest(
    classes = {
        JHipsterProperties.class,
        WebConfigurer.class,
        SecurityConfiguration.class,
        SecurityJwtConfiguration.class,
        SecurityMetersService.class,
        JwtAuthenticationTestUtils.class,
    }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public @interface AuthenticationIntegrationTest {
}
