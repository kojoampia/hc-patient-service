package net.jojoaddison.config;

import org.springframework.context.annotation.Configuration;

/**
 * Jackson customizations for this application.
 *
 * <p>Neither of the two modules this class used to register is needed under Jackson 3, which arrived with Spring Boot
 * 4. Java date and time support ships inside {@code jackson-databind} ({@code tools.jackson.databind.ext.javatime})
 * and registers itself; {@code Optional} and the other types the old {@code Jdk8Module} covered are likewise built in.
 * Both former dependencies — {@code jackson-datatype-jsr310} and {@code jackson-datatype-jdk8} — were never
 * republished under Jackson 3's coordinates, so declaring them resolves nothing.</p>
 *
 * <p>The class is kept because integration tests import it explicitly
 * ({@code @SpringBootTest(classes = { ..., JacksonConfiguration.class, ... })}) and so future Jackson customizations
 * have an obvious home.</p>
 */
@Configuration
public class JacksonConfiguration {}
