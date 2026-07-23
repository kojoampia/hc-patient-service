# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Health Connect Patient Service (`hcPatientService`) — a backend-only JHipster microservice (JHipster 8.1.0 generator, Spring Boot 4.0.3, JHipster BOM 9.0.0, Java 26) that manages patient data for the Health Connect platform. No frontend client is generated (`skipClient: true`). Package root: `net.jojoaddison`.

It is one service in a larger microservice architecture: it registers with Consul for service discovery/config and **will refuse to start if Consul is unreachable at `http://localhost:8500`**. MongoDB is the datastore. Kafka is used for async messaging. See `AGENTS.md` for code-quality/architecture/security/performance guidelines that apply repo-wide.

## Commands

### Prerequisites (must be running before `./mvnw`/dev boot)

```
npm run services:up          # MongoDB (27017) + Consul (8500) + Kafka (9092), all via docker compose
# or individually:
npm run docker:db:up
npm run docker:consul:up
npm run docker:kafka:up
```

### Run

```
./mvnw                       # dev profile
npm run app:start            # same, via npm
npm run backend:debug        # dev profile with remote debug on port 8000
```

### Build

```
./mvnw -Pprod clean verify           # production jar
./mvnw -Pprod,war clean verify       # production war
```

### Test

```
./mvnw verify                        # full test suite (unit + *IT integration tests)
npm run backend:unit:test            # same, with noisy loggers silenced
./mvnw -Dtest=ProfileResourceIT test # single test class
./mvnw -Dtest=ProfileResourceIT#createProfile test   # single test method
npm run backend:nohttp:test          # checkstyle / nohttp check
```

Integration tests (`*ResourceIT`) spin up embedded Mongo and Kafka via Testcontainers (see `@IntegrationTest` in `src/test/java/net/jojoaddison/IntegrationTest.java`) — they do not require the docker compose services to be running separately.

### Formatting

```
npm run prettier:check     # md/json/yml/html/java via Prettier
npm run prettier:format
```

Java is also formatted by the Spotless Maven plugin during the build; checkstyle rules live in `checkstyle.xml`.

## Architecture

Standard JHipster layered architecture, enforced at build time by an ArchUnit test (`src/test/java/net/jojoaddison/TechnicalStructureTest.java`):

```
config → web → service (optional) → security → repository (optional) → domain
```

- `web` may only be accessed by `config`.
- `service` may only be accessed by `web`, `config`.
- `security` may only be accessed by `config`, `service`, `web`.
- `repository` may only be accessed by `service`, `security`, `web`, `config`.
- `domain` sits at the bottom, accessible from every layer above.

Directory map (`src/main/java/net/jojoaddison/`):

- `web/rest` — `@RestController`s, one per entity (`ProfileResource`, `MedCaseResource`, etc.) plus `web/rest/errors` for the RFC-7807-style exception translation (`ExceptionTranslator`, `BadRequestAlertException`).
- `service` — business/persistence orchestration (currently only entities with extra logic get a service class, e.g. `ProfileService`, `MedCaseService`; simpler entities call their repository directly from the resource).
- `repository` — `Spring Data MongoRepository` interfaces only, one per entity, no query logic beyond what Spring Data derives.
- `domain` — Mongo document classes (`@Document`) plus `domain/enumeration` for enums (`CaseCategory`, `CaseStatus`).
- `security` — JWT auth utilities (`SecurityUtils`, `AuthoritiesConstants`).
- `config` — Spring configuration classes (`SecurityConfiguration`, `SecurityJwtConfiguration`, `DatabaseConfiguration`, `AsyncConfiguration`, `WebConfigurer`, etc.).
- `broker` — `KafkaConsumer`/`KafkaProducer`.
- `aop/logging` — logging aspect.

### REST/service/repository conventions

Full rules live in `.github/instructions/rest-patterns.instructions.md` and `.github/instructions/backend-tests.instructions.md` — read them before touching resource/service/repository/test code. Key points:

- Resource classes: `@RestController`, `@RequestMapping("/api/<entities>")`, constructor injection of service + repository, an `ENTITY_NAME` constant, `applicationName` injected via `@Value("${jhipster.clientApp.name}")`.
- POST rejects a body that already has an id (`idexists`); returns 201 + creation alert header.
- PUT `/{id}` rejects null id (`idnull`), path/body id mismatch (`idinvalid`), and unknown id (`idnotfound`); returns 200 + update alert header.
- PATCH `/{id}` accepts `application/json` and `application/merge-patch+json`, same id guards as PUT, delegates to the service's partial-update (merge non-null fields only — never overwrite existing values with null), uses `ResponseUtil.wrapOrNotFound(...)`.
- DELETE returns 204 + deletion alert header.
- Mongo repositories stay minimal (`interface XRepository extends MongoRepository<X, String>`); add query methods only when a concrete service needs them.
- No reactive types (`Mono`/`Flux`) — this service is Spring MVC, not WebFlux.

### Tests

- Unit tests: `*Test.java`. Integration tests: `*IT.java` (or `*IntTest.java`).
- REST integration tests live in `src/test/java/net/jojoaddison/web/rest/*ResourceIT.java`, use `@IntegrationTest @AutoConfigureMockMvc @WithMockUser`, `DEFAULT_*`/`UPDATED_*` field constants, `ENTITY_API_URL`/`ENTITY_API_URL_ID` constants, and reset state in `@BeforeEach` via `repository.deleteAll()`.
- Each resource IT covers the full CRUD + validation matrix (create, create-with-id 400, get all, get by id, get missing 404, put success/not-found/mismatch/missing-id, patch partial/full/not-found/mismatch/missing-id, delete) — see `.github/instructions/backend-tests.instructions.md` for the exact list when adding a new entity.
- Use repository-count helpers (`getRepositoryCount`, `assertIncrementedRepositoryCount`, etc.) and domain assertion helpers (`src/test/java/net/jojoaddison/domain/*Asserts.java`) instead of ad-hoc counting/assertions.
- Keep architecture tests (`TechnicalStructureTest`) and security/JWT tests (`security/jwt/*`) separate from endpoint CRUD tests — don't mix concerns in one class.

### Entities

Domain entities managed by this service (from `.yo-rc.json`): `Address`, `Condition`, `Medication`, `Stat`, `Team`, `Task`, `Membership`, `Report`, `Metadata`, `Profile`, `MedCase`. (`HCCredential`, `HCPayOption`, `HCDocument` were recently removed — check `git status`/`git log` before assuming they still exist.) Entity JHipster configs live in `.jhipster/*.json`; regenerating/modifying an entity should keep those files, the domain class, repository, resource, and `*ResourceIT` in sync.

## Constraints

- Java version must stay within JDK 17–26 (enforced by the Maven Enforcer plugin in `pom.xml`); Maven must be ≥ 3.2.5.
- Do not convert this service to reactive (`Mono`/`Flux`); it's Spring MVC + MongoDB throughout.
- Don't bypass the JHipster alert-header/exception-translation conventions in `web/rest/errors`.
- Follow ArchUnit layer boundaries in `TechnicalStructureTest` — a change that makes `service` depend on `web`, for example, will fail the build.
