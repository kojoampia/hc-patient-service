# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Health Connect Patient Service (`hcPatientService`) — a backend-only JHipster microservice that manages patient data for the Health Connect platform. No frontend client is generated (`skipClient: true`). Package root: `net.jojoaddison`. Server port `8081`.

Stack as actually configured in `pom.xml` / `.yo-rc.json`:

|                  |                                                                                                       |
| ---------------- | ----------------------------------------------------------------------------------------------------- |
| Java             | `java.version` 25 (`maven.compiler.release`); Maven Enforcer accepts JDK `[17,26)`                    |
| Framework        | Spring Boot 4.0.6, Spring Cloud 2025.1.1, `jhipster-framework` 9.0.0 — **Spring MVC, not WebFlux**    |
| Generator        | app scaffolded with JHipster 8.1.0; entities regenerated with 9.1.0 (`.yo-rc.json` `jhipsterVersion`) |
| Datastore        | MongoDB (`mongo:7.0.4` locally)                                                                       |
| Messaging        | Kafka via Spring Cloud Stream (`confluentinc/cp-kafka:7.6.0`)                                         |
| Discovery/config | Consul (`bitnami/consul:1.17.0`)                                                                      |
| Auth             | JWT validation only — `skipUserManagement: true`, no `User` domain here                               |
| Container image  | Jib, base `eclipse-temurin:25-jre`                                                                    |

It is one service in a larger microservice architecture: it registers with Consul and **will refuse to start if Consul is unreachable at `http://localhost:8500`**. Tokens are minted by the patient gateway (`hc-patient-gateway`); this service only validates them, so both must agree on the JWT secret.

Companion docs in this repo:

- `patient-api.md` — **the plan of record**: open decisions, phased backlog (entity completion, subscription domain, telemetry, platform hardening), and what is already done. Check it before starting new work.
- `AGENTS.md` — code-quality/architecture/security/performance guidelines that apply repo-wide.
- `.github/instructions/*.instructions.md` — authoritative REST and test rules.

Sibling plans: `hc-patient-gateway/patient-gateway.md`, `hc-patient-dashboard/patient-web.md`.

Note: Lombok is **not** on the classpath despite what older notes may say — entities use JHipster's generated getters/setters/fluent setters.

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
npm run java:docker                  # Jib image (add :arm64 variant on Apple Silicon)
```

### Test

```
./mvnw verify                        # full test suite (unit + *IT integration tests)
npm run backend:unit:test            # same, with noisy loggers silenced
./mvnw test -Dtest=SecurityUtilsUnitTest             # single unit test class (surefire)
./mvnw verify -Dit.test=ProfileResourceIT            # single integration test (failsafe)
./mvnw verify -Dit.test=ProfileResourceIT#createProfile
./mvnw verify -DskipITs              # unit tests only
npm run backend:nohttp:test          # checkstyle / nohttp check
```

Selecting an integration test needs `-Dit.test`, not `-Dtest`: surefire is configured to **exclude** `**/*IT*` and `**/*IntTest*`, and failsafe (bound to `integration-test`/`verify`) owns them. `./mvnw -Dtest=SomeResourceIT test` therefore runs nothing.

Integration tests (`*ResourceIT`) spin up embedded Mongo and Kafka via Testcontainers (see `@IntegrationTest` in `src/test/java/net/jojoaddison/IntegrationTest.java`) — they do not require the docker compose services to be running separately. Current suite: 14 `*IT` + 16 `*Test` classes.

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

- `web/rest` — `@RestController`s, one per entity (`ProfileResource`, `ClinicalCaseResource`, etc.) plus `web/rest/errors` for the RFC-7807-style exception translation (`ExceptionTranslator`, `BadRequestAlertException`).
- `service` — business/persistence orchestration; `service/dto` and `service/event` beneath it. `ProfileSearch` also
  lives here: `GET /api/profiles?search=` matches six fields by regex, so the term is **escaped there before it goes
  near the query**. Unescaped, a search for `.*` returns the entire patient directory to anyone who types two
  characters — an authorization boundary stepped around by a query language rather than by a missing check. There is **no general DTO/mapper layer** — domain documents are returned directly — with one deliberate exception: `service/dto` holds the onboarding step payloads, because those must _not_ be a `Profile`. A request body carrying `email`, `patientId` or `id` is how onboarding would become an account-takeover endpoint. `service/event` holds the `patient-events` publisher.
- `repository` — `Spring Data MongoRepository` interfaces only, one per entity, no query logic beyond what Spring Data derives.
- `domain` — Mongo document classes (`@Document`) plus `AbstractAuditingEntity` and `domain/enumeration` for enums (`CaseCategory`, `CaseStatus`).
- `security` — JWT auth utilities (`SecurityUtils`, `AuthoritiesConstants`), `PatientScope` (whose records), and
  `ScopeOfPractice` + `ClinicalDomain` (what kind of data a discipline may touch).
- `config` — Spring configuration classes (`SecurityConfiguration`, `SecurityJwtConfiguration`, `DatabaseConfiguration`, `AsyncConfiguration`, `WebConfigurer`, etc.), plus `config/dbmigrations/` — the package Mongock scans (`mongock.migration-scan-package`). It holds one change unit — `AddressAsDocumentMigration` (2026-08-19), which reshapes free-text `Profile.address` values into `Address` documents — and two `ApplicationRunner`s, both gated to `dev`/`test`. `DemoDataInitializer` seeds the professional-dashboard demo dataset from `src/main/resources/config/demo-data/`. `DevelopmentDataInitializer` (added 2026-08-15, shaped after hc-admin's class of the same name) seeds a record from a document supplied **from outside the image**, named by `hc.seed.location` — a Spring resource string, unset here, set by `hc-patient-quality` to the file it mounts. That document is keyed by profile at the root and holds plain arrays of domain objects per collection, so Jackson deserializes straight into the domain and a field this service does not have is a field the document cannot set. Setting `hc.seed.location` **stands `DemoDataInitializer` down**, because the two datasets describe the same subsystem with different people in it. Two departures from the hc-admin original, both because this service already promised otherwise: every active profile's block is applied rather than only the most specific (the quality stack runs `dev,test`, and "test wins" would seed nothing at all), and seeding is additive rather than overwriting. Seeding belongs in a runner rather than a change unit because a change unit has no notion of a Spring profile and runs exactly once — the gateway shipped publicly known credentials to production by making that mistake.
- `broker` — `KafkaConsumer`/`KafkaProducer`.
- `management` — metrics/health support.
- `aop/logging` — logging aspect.

### REST/service/repository conventions

Full rules live in `.github/instructions/rest-patterns.instructions.md` and `.github/instructions/backend-tests.instructions.md` — read them before touching resource/service/repository/test code. Key points:

- Resource classes: `@RestController`, `@RequestMapping("/api/<entities>")`, constructor injection of service + repository, an `ENTITY_NAME` constant, `applicationName` injected via `@Value("${jhipster.clientApp.name}")`.
- POST rejects a body that already has an id (`idexists`); returns 201 + creation alert header.
- PUT `/{id}` rejects null id (`idnull`), path/body id mismatch (`idinvalid`), and unknown id (`idnotfound`); returns 200 + update alert header.
- PATCH `/{id}` accepts `application/json` and `application/merge-patch+json`, same id guards as PUT, delegates to the service's partial-update (merge non-null fields only — never overwrite existing values with null), uses `ResponseUtil.wrapOrNotFound(...)`.
- DELETE returns 204 + deletion alert header.
- Mongo repositories stay minimal (`interface XRepository extends MongoRepository<X, String>`); add query methods only when a concrete service needs them.
- No reactive types (`Mono`/`Flux`) — this service is Spring MVC, not WebFlux. (The gateway repo is the reactive one.)

### Tests

- Unit tests: `*Test.java`. Integration tests: `*IT.java` (or `*IntTest.java`).
- REST integration tests live in `src/test/java/net/jojoaddison/web/rest/*ResourceIT.java`, use `@IntegrationTest @AutoConfigureMockMvc @WithMockUser`, `DEFAULT_*`/`UPDATED_*` field constants, `ENTITY_API_URL`/`ENTITY_API_URL_ID` constants, and reset state in `@BeforeEach` via `repository.deleteAll()`.
- Each resource IT covers the full CRUD + validation matrix (create, create-with-id 400, get all, get by id, get missing 404, put success/not-found/mismatch/missing-id, patch partial/full/not-found/mismatch/missing-id, delete) — see `.github/instructions/backend-tests.instructions.md` for the exact list when adding a new entity.
- Use repository-count helpers (`getRepositoryCount`, `assertIncrementedRepositoryCount`, etc.) and domain assertion helpers (`src/test/java/net/jojoaddison/domain/*Asserts.java`) instead of ad-hoc counting/assertions.
- Keep architecture tests (`TechnicalStructureTest`) and security/JWT tests (`security/jwt/*`) separate from endpoint CRUD tests — don't mix concerns in one class.

### Entities

Present as `@Document` classes with a repository — twenty-three. All but `CareDelegation` also have a generated resource and a CRUD `*ResourceIT`:

`ActivityLog`, `Address`, `Allergy`, `CareDelegation`, `CarePlanItem`, `ClinicalCase`, `Condition`, `DutyRoster`,
`Emergency`, `Medication`, `Membership`, `Metadata`, `PaymentOption`, `PersonalDocument`, `Professional`, `Profile`,
`Recommendation`, `Report`, `Shift`, `Stat`, `Task`, `Team`, `Visitation`.

`CareDelegation` (2026-08-19) is the exception to every convention here and deliberately so: **no generated CRUD
resource and no `DELETE`.** Its endpoints are one per state transition. A generic `PATCH` would let a care angel set
their own status to `ACTIVE`, which is the whole delegation model defeated by the one verb nobody thought about; and
the record of who could act for a patient, and between which dates, is the point of keeping it.

`ClinicalCase` **replaced** `MedCase` (it is not a rename — different fields, collection `clinicalcase`, and a
many-to-many to `Recommendation`); its contract comes from `hc-professional/web/.jhipster/ClinicalCase.json`.
`Recommendation` is new, and exists because that relationship needs it. The `CaseCategory` enum was removed with
`MedCase`; `CaseStatus` (URGENT/OPEN/TREATMENT/CLOSED) remains. There are now **two** relationships in this service:
`ClinicalCase` <-> `Recommendation`, and `Profile` -> `Address`, which became a `@DBRef` on 2026-08-19 because
onboarding needed a structured address. Everything else is standalone, referencing by plain String id, and a third
relationship should be argued for rather than assumed.

`ClinicalCase` gained `archivedAt`, `archivedById` and `archiveReason` on 2026-08-22. A nullable instant rather than
a boolean, because the question asked about an archived case afterwards is _who_ and _why_, and a boolean records
that it happened and loses both. The queries use `IsNull` rather than a boolean test, and that is load-bearing for
the data that already exists: every case written before those fields has no `archived_at` key at all, and in MongoDB
a null match also matches a missing field, so they all read as live with no migration. `GET /api/clinical-cases`
excludes archived cases unless `includeArchived=true`; `GET /{id}` still returns one, so a link keeps working.

`DutyRoster` and `Shift` are the newest (2026-08-11) and are what `ClinicalCase.assignedRosterId` points at; it
named nothing until they existed. Both are **staff reference data**, so they follow `Team`/`Professional` rather than
the patient entities: readable by any authenticated caller, writable only by `ROLE_ADMIN`/`ROLE_PROFESSIONAL`, no
`patientId` and no `PatientScope`. `ShiftStatus` (ACTIVE/UPCOMING/COMPLETED) is deliberately not `ScheduleStatus`,
which is an appointment's lifecycle. One caveat: `DutyRoster.subscribedProfessionalIds` is a `Set<String>` that
**does not exist in `patient.jdl`** — JDL has no list-of-scalars type — so regenerating that entity drops it. The
field carries a comment saying so.

`PaymentOption` and `PersonalDocument` (renamed from the removed `HCPayOption` and `IDocument`) are generated too, so
Phase A of `patient-api.md` is done. `HCCredential`, `HCPayOption`, `HCDocument`/`IDocument` no longer exist as code.

The `entities` array in `.yo-rc.json` is stale (still lists the removed `HCCredential`/`HCPayOption`/`HCDocument` and omits the renames). Trust `.jhipster/*.json` + the `domain` package over it. Regenerating or modifying an entity should keep the `.jhipster` config, domain class, repository, resource, and `*ResourceIT` in sync.

Note the frontend (`hc-patient-dashboard`) still ships `hc-credential`/`hc-pay-option` CRUD screens for the old names — coordinate renames across both repos.

## Onboarding, delegation and the rules that come with them

Built 2026-08-19. `docs/onboarding.md` is the plan of record and §16 is the contract. Five things constrain new work:

- **`PatientScope` is the whole authorization model, and it fails closed.** `POST /api/onboarding` is the single
  narrow path that may run before a `Profile` exists. Do not add a second.
- **A care angel's authority is an `ACTIVE` `CareDelegation`, never `ROLE_ANGEL`.** `PatientScope` reads the
  delegation, never `Profile.careAngelEmail`, which is a display cache — reading the cache would keep granting access
  after a revocation. Which patient an angel acts for arrives in an `X-Acting-As` header, re-checked per request.
- **Patient data is never deleted.** Sixteen resources require `ROLE_ADMIN` for `DELETE`. Archiving — the
  professional-only replacement — **exists for `ClinicalCase` since 2026-08-22** and for nothing else yet:
  `POST /api/clinical-cases/{id}/archive` and `/unarchive`, a reason required, and a **clinical** authority —
  `ROLE_PROFESSIONAL` or `ROLE_DOCTOR` (added 2026-08-24, because `hc-professional`'s gateway mints no
  `ROLE_PROFESSIONAL` and every clinician arriving from that stack was getting a 403). `ROLE_ADMIN` is excluded on
  purpose, which is why this is `@PreAuthorize` and not `requireWrite(DIAGNOSIS)` — `PatientScope` returns true for
  an admin before it consults `ScopeOfPractice`. It is a
  transition endpoint rather than a `PATCH` for the reason `CareDelegation` has none — a `PATCH` over `archivedAt`
  lets a client choose when a case was archived and by whom, and both are records rather than claims. `PUT` carries
  the stored archive state over from the existing record for the same reason; without that, the one verb that
  replaces a document wholesale is the way round the rule.
- **A caller's discipline decides what kind of data they may touch** (2026-08-22). `ScopeOfPractice` is one table
  mapping `ROLE_DOCTOR`/`NURSE`/`CARER`/`PARAMEDIC`/`PHARMACIST`/`THERAPIST`/`CHEMIST`/`TECHNICIAN` onto six
  `ClinicalDomain`s. **It is a starting position rather than a clinical ruling** and says so at the top; correcting
  it is a two-line change in one file, deliberately.
  - This service issues none of those roles. **`hc-professional`'s gateway does, and has no `ROLE_PROFESSIONAL` at
    all** — and the two stacks share a JWT signing key, so its tokens reach here. Before this, a doctor signing in
    there failed every `ROLE_PROFESSIONAL` check, resolved to no patient, and was served empty lists rather than a
    refusal.
  - `ROLE_PROFESSIONAL` still means everything. Narrowing it would change thirty checks at once, silently.
  - It composes with `PatientScope` and never replaces it: that decides _whose_ records, this decides _what kind_,
    and whose is settled first. Wired into the write paths of eleven resources; **reads are not filtered yet** —
    `canRead`/`requireRead` exist and are tested but nothing calls them.
- **`source` is stamped from the caller, never from the payload,** on create only. A value a client can choose is a
  claim rather than a record.
- **No event carries clinical content.** `PatientEventPublisher.assertNothingClinical` throws rather than stripping,
  so the refusal lands on whoever is adding the field.

Two things that will waste an afternoon if you meet them cold:

- `src/test/resources/config/application.yml` is the **same classpath resource** as the main one and replaces it
  wholesale. Anything configured only in main is untested. This caused three separate defects.
- Jackson 3 refuses to bind an absent property onto a primitive (`FAIL_ON_NULL_FOR_PRIMITIVES`) and reports only
  `"Failed to read request"`. Prefer boxed types in request records.

## Constraints

- Java must stay within the Enforcer range `[17,26)` (JDK 17–25); the compiler targets 25 via `maven.compiler.release`; Maven must be ≥ 3.2.5.
- Do not convert this service to reactive (`Mono`/`Flux`); it's Spring MVC + MongoDB throughout.
- Don't bypass the JHipster alert-header/exception-translation conventions in `web/rest/errors`.
- Follow ArchUnit layer boundaries in `TechnicalStructureTest` — a change that makes `service` depend on `web`, for example, will fail the build.
- `bin/` is a gitignored stale copy of the project (its own `pom.xml`, `README.md`, `src/`). Never edit or cite files under `bin/`.
- No CI workflows exist in `.github/workflows`; the `ci:*` npm scripts are there for a CI system to call but nothing is wired up.
