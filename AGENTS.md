# Project Overview

Repo-wide guidelines for `hc-patient-service` (`hcPatientService`) — the backend-only Health Connect patient microservice.

Read in this order: `CLAUDE.md` for the verified stack/architecture summary, `patient-api.md` for the plan of record (open decisions and the phased backlog), then this file for the standing quality expectations. `.github/instructions/*.instructions.md` holds the authoritative REST/test rules.

Statements below are split between **current** (true of the code today) and **target** (what to move toward). Do not treat a target as a description of existing code — anything in the target column that is actually scheduled appears as a tracked item in `patient-api.md`.

## Code Quality and Style

- Follow SOLID principles and clean code practices.
- Use consistent naming conventions and code formatting; Java is formatted by Spotless during the Maven build, everything else by Prettier (`npm run prettier:check|format`).
- Implement unit and integration tests with JUnit 5, Mockito, and Testcontainers.
- Document non-obvious code and APIs with JavaDoc; the OpenAPI description is served by springdoc (`springdoc-openapi-starter-webmvc-api`) and is **only enabled when the `api-docs` profile is active** — `application.yml` disables `springdoc.api-docs` otherwise.
- No null pointer dereferences; use `Optional` where applicable (services already return `Optional` from `findOne`/`partialUpdate`).
- Handle exceptions through the JHipster `web/rest/errors` translation layer so responses stay RFC 7807-shaped.
- **Lombok is not used and is not on the classpath.** Entities keep JHipster's generated getters/setters plus fluent setters — do not introduce Lombok annotations without adding the dependency and agreeing to it repo-wide.
- Log with SLF4J/Logback; keep the existing debug-log-on-service-entry convention.
- Follow resource-leak prevention practices, especially around file handling and Mongo/Kafka clients.

## Architecture and Design

- Layered architecture (`web/rest` → `service` → `repository` → `domain`) with boundaries enforced by ArchUnit in `TechnicalStructureTest`.
- Domain-driven modelling of patient data. Entities currently implemented: `Profile`, `Address`, `Condition`, `Medication`, `ClinicalCase`, `Stat`, `Team`, `Task`, `Membership`, `Report`, `Metadata`. `PaymentOption` and `PersonalDocument` exist only as `.jhipster/*.json` configs so far.
- Constructor injection for all services and repositories; no static initialization blocks.
- **Current:** only `ProfileService` and `ClinicalCaseService` exist, and there is no DTO/mapper layer — resources return domain documents directly.
- **Target:** when a resource grows logic beyond straight persistence, add a service; introduce DTOs (immutable where practical) only when the wire shape must diverge from the document.
- Kafka (Spring Cloud Stream) for asynchronous cross-service communication, e.g. telemetry and alerts; producer/consumer live in `broker`.
- Error handling stays centralized in `web/rest/errors` (`ExceptionTranslator`, `BadRequestAlertException`) rather than per-controller `@ExceptionHandler`s.

## Security Considerations

- Spring Security with role-based access control per `AuthoritiesConstants` (`ROLE_ADMIN`, `ROLE_USER`, `ROLE_ANONYMOUS`).
- This service does not store credentials (`skipUserManagement: true`, no `User` domain) — it only **validates** JWTs minted by `hc-patient-gateway`, so password hashing is out of scope here.
- **Known gap:** the committed dev/prod `jhipster.security.authentication.jwt.base64-secret` in `src/main/resources/config/application-*.yml` differs from the gateway's committed secret, so a gateway-issued token will fail signature validation unless both are overridden from the same source (env var / Consul KV). Never rely on the committed values, and keep real secrets out of git and logs.
- Sensitive data (personal information, documents) must be encrypted in transit (`application-tls.yml`) and at rest at the datastore level.
- Validate and sanitize all input (`@Valid` on request bodies, Bean Validation constraints on documents).
- CORS is commented out (disabled) in `application-dev.yml` — there is no browser client talking to this service directly; traffic arrives through the gateway. Enable it only with a deliberate origin list.
- Keep dependencies patched; treat Mongo query construction and any file upload path as untrusted input.
- Ensure logs contain no PII; the CRLF log converter is already configured to defend against log injection.
- Handling health data means GDPR/HIPAA-style obligations apply to any new field, log line, or export.
- **Target:** rate limiting and abuse monitoring are not implemented in this service today.

## Performance Optimization

- Paginate and filter endpoints that can return large collections. **Current:** generated `getAll*` endpoints return unpaged `List<Entity>`; add pagination when a collection can grow unbounded.
- **Target:** no cache provider is configured (`cacheProvider: "no"`). Adding Spring Cache means adding and justifying the dependency.
- Index Mongo collections for the query patterns actually used; keep repositories free of ad-hoc query logic.
- Use asynchronous processing (`AsyncConfiguration`) for long-running work.
- Monitor with Spring Boot Actuator (`/management/**`, Prometheus endpoint enabled) and profile before optimizing.
- Keep services stateless so instances can scale horizontally behind the gateway.
- Watch the Kafka consumer (`broker/KafkaConsumer`) — it sits on a hot path for telemetry.

## Technology Stack

- Java: compiled for 21 (`java.version` in `pom.xml`); Maven Enforcer allows JDK `[17,27)`, i.e. 17–26. Maven ≥ 3.2.5.
- Spring Boot 3.4.5 with JHipster BOM (`jhipster-dependencies`) 8.11.0 — **Spring MVC, not reactive/WebFlux**.
- Spring Web, Spring Data MongoDB, Spring Security (JWT resource server), Spring Cloud Stream Kafka binder.
- Spring Cloud Consul for discovery and centralized config; Resilience4j for circuit breaking; MapStruct available (1.6.3) though no mappers exist yet.
- Backend-only (`skipClient: true`) — no Angular code in this repo; the dashboard lives in `hc-patient-dashboard`.
- Docker Compose for local dependencies (`mongo:7.0.4`, `bitnami/consul:1.17.0`, `confluentinc/cp-kafka:7.6.0`); images built with Jib on `eclipse-temurin:26-jre`.
- JUnit 5, Mockito, ArchUnit 1.4.2, Testcontainers (embedded MongoDB + Kafka).
- SLF4J + Logback.
- Maven for build/dependencies; npm only for dev tooling (Prettier, Husky, docker/script shortcuts).
- Git for version control. **No GitHub Actions workflows exist** in `.github/workflows`; the `ci:*` npm scripts are entry points waiting for a CI system.
- `bin/` is a gitignored stale copy of the project — ignore it entirely.
