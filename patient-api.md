# Patient API — Plan

Single plan of record for `hc-patient-service` (`hcPatientService`). It consolidates the backend slices of the Health Connect patient blueprint/checklist (previously kept only in the dashboard repo) with the open items found while auditing this service.

- **Baseline verified:** 2026-08-03 against `pom.xml`, `.yo-rc.json`, `patient.jdl`, `src/main/java`, `src/test/java`, `src/main/resources/config`, and a full `./mvnw clean verify`. (Previous baseline 2026-07-30, before the patient-context entity work.)
- **Companion docs:** `CLAUDE.md` (what exists and how it is wired), `AGENTS.md` (standing quality/security/performance expectations), `.github/instructions/*.instructions.md` (authoritative REST/test rules).
- **Sibling plans:** `hc-patient-gateway/patient-gateway.md`, `hc-patient-dashboard/patient-web.md`.

Status legend: `[x]` done · `[~]` partial / diverges from plan · `[ ]` not started.

## What changed since the last baseline

### Patient-context entities (branch `feature/patient-context-entities`, 2026-08-03)

The dashboard's UI refactor needed domain objects that did not exist. Rather than invent them
client-side, the model was extended here and rendered into both repos' `.jhipster` configs.

- **`patient.jdl` is now the model of record** for the whole patient domain — twenty entities and
  eleven enums. It lives here because this service owns the domain; the dashboard consumes the same
  model through its own `.jhipster/*.json`, rendered from this file by `tools/render-entity-configs.mjs`.
  Regenerate both together; never hand-edit one side's entity JSON.
- **Six new entities:** `Professional`, `Visitation`, `Emergency`, `ActivityLog`, `CarePlanItem`,
  `Allergy` — each with document, repository, service, resource and a full CRUD `*ResourceIT`.
- **Eight extended:** `Profile`, `Membership`, `ClinicalCase` (adds `caseNumber`, `title`, `closedAt`,
  and `TREATMENT` to `CaseStatus`), `Stat`, `Medication`, `Report`, `Task`, `PersonalDocument`.
  **Vitals are `Stat`** — there is no separate Vital entity, by decision.
- **`?patientId=` on every patient-scoped collection endpoint**, backed by a `findByPatientId`
  repository method. Without it the dashboard had to fetch every patient's records and filter in the
  browser — slow, and a disclosure risk. The parameter is optional, so existing callers are unaffected.
- **`GET /api/profiles/email/{email}`** — the dashboard's entry point into a record. The gateway
  issues tokens keyed on email, so email is the only identifier the client holds before it has
  loaded anything; every other collection is then fetched by the `patientId` this returns.
- **Coverage is measured and gated.** Unit and integration runs each covered a different half of the
  service, so neither report meant much alone; `jacoco:merge` now produces one figure and
  `jacoco:check` fails the build below 80% instruction and 80% branch. Current: **93.7% / 81.7%**.
- **Verified:** `./mvnw clean verify` — 55 unit tests, 335 integration tests, 0 checkstyle
  violations, all coverage checks met.

Note the collection name for `ClinicalCase` is `clinicalcase`, not `clinical_case`; that is a live
collection and the JDL render deliberately preserves whatever each repo already declares.

### Java 25 and Spring Boot 4 (2026-08-04)

`java.version` is **25** (pinned with `maven.compiler.release`, Enforcer `[17,26)`, `eclipse-temurin:25`
images in both the Jib config and `deploy/docker/api.Dockerfile`), and the service is on **Spring Boot
4.0.6 / Spring Cloud 2025.1.1**, matching the gateway. Decision 3 below is settled by this.

`./mvnw clean verify` on JDK 25: **55 unit + 335 integration tests, 0 failures, 0 Checkstyle
violations**, merged coverage 93.6% instruction / 81.7% branch.

The migration, in the order the failures surfaced — the gateway's record in `patient-gateway.md` is
what made most of these quick to place:

- **Build.** The JHipster 8.x BOM manages Spring Boot 3 and cannot be imported next to Boot 4, so
  `spring-boot-dependencies` is imported directly and `jhipster-framework` 9.0.0 pinned on its own.
  `springdoc-openapi-bom` and `logstash-logback-encoder` needed pinning for the same reason;
  `spring-boot-loader-tools` is now versioned explicitly. Modernizer 3.3.0 → 3.5.0 (3.3.0 cannot read
  Java 25 bytecode), which flagged six `Arrays.asList`/`Collections.singleton*` call sites in tests —
  all genuinely equivalent swaps, so they were fixed rather than excluded.
- **`spring-boot-starter-aop` → `spring-boot-starter-aspectj`.**
- **Jackson 3.** `jackson-datatype-jsr310` and `-hppc` are gone (folded into `jackson-databind`, and
  never republished). `LoggingConfiguration` moved to `tools.jackson.databind.ObjectMapper` /
  `JacksonException`. The `com.fasterxml.jackson.annotation.*` annotations keep their old coordinates.
- **Spring Security 7.** `MvcRequestMatcher` and its `HandlerMappingIntrospector` are gone;
  `requestMatchers(String...)` now builds a `PathPatternRequestMatcher`, which is equivalent here
  because the service is deployed at the root context.
- **`MongoAutoConfiguration`** moved to `org.springframework.boot.mongodb.autoconfigure`.

Three of these fail _silently_ rather than loudly, and are the ones to remember:

- **`spring.data.mongodb.*` is no longer bound** — the prefix is `spring.mongodb.*`. Left alone the
  app falls back to `localhost:27017`. This bit in three places: both `application-*.yml`, the
  Testcontainers property in `TestContainersSpringContextCustomizerFactory` (so the suite would have
  run against whatever Mongo was on the developer's machine), and `SPRING_DATA_MONGODB_URI` in both
  of `deploy/`'s compose files.
- **`spring.jackson.serialization.write-durations-as-timestamps`** moved to
  `spring.jackson.datatype.datetime.*` when Jackson 3 shifted it from `SerializationFeature` to
  `DateTimeFeature`. On the old key `JacksonProperties` fails to bind, which takes the entire
  application context down — 335 tests errored on one stale line.
- **Test slices were split into per-technology modules.** `@AutoConfigureMockMvc` lives in
  `spring-boot-webmvc-test` under a new package, and `SecurityMockMvcAutoConfiguration` — the thing
  that applies `springSecurity()` to MockMvc, and therefore what makes `@WithMockUser` work — lives in
  `spring-boot-security-test`. Without the latter the suite runs and every secured request returns 401. `AuthenticationIntegrationTest` also has to import `ServletWebSecurityAutoConfiguration`
  by hand (Boot 4 renamed it from `SecurityAutoConfiguration`) because that context lists its
  configuration classes explicitly and so gets no auto-configuration.

## Open decisions that block work below

1. **Subscription plan prices.** The blueprint says Pear 1000 / Melon 2000 / Pawpaw 5000; the checklist said Pear 3000 / Pawpaw 4000 / Melon 5000. Nothing can be seeded until one set is chosen. Owner: product.
2. **Telemetry datastore.** The blueprint calls for a TimescaleDB `telemetry_db` alongside MongoDB. This service is Mongo-only today — no TimescaleDB image, dependency, or config exists anywhere in the workspace. Adding it means a second datastore plus a JPA/JDBC stack in a service that has neither. Alternative: keep vitals in MongoDB (time-series collections) and revisit if query load demands it.
3. ~~**Spring Boot 4 / Java 25 upgrade.**~~ Settled 2026-08-04: this service followed the gateway onto Spring Boot 4.0.6 and Java 25. Both now target the same Boot major and the same JDK — worth keeping that way, since the two share a JWT contract and the Jackson/Mongo breakages above all came from the halves drifting apart.
4. **Patient/angel roles.** The blueprint expects a `PATIENT` (or `ANGEL`) role. Only `ROLE_ADMIN`, `ROLE_USER`, `ROLE_ANONYMOUS` exist in `AuthoritiesConstants`, and roles are minted by the gateway — so this is a joint change with `hc-patient-gateway`.

## Baseline — already in place

- `[x]` JHipster microservice scaffold: Consul discovery/config, MongoDB, Kafka (Spring Cloud Stream), JWT resource-server validation, `skipUserManagement`.
- `[x]` Layered architecture with ArchUnit enforcement (`TechnicalStructureTest`).
- `[x]` RFC 7807 error handling via `web/rest/errors/ExceptionTranslator`, covered by `ExceptionTranslatorIT`.
- `[x]` Eleven documents with repository + resource + full CRUD `*ResourceIT`: `Profile`, `Address`, `ClinicalCase`, `Condition`, `Medication`, `Stat`, `Team`, `Task`, `Membership`, `Report`, `Metadata`.
- `[x]` Auditing base class `AbstractAuditingEntity`; Mongo ids are `String` (not UUID).
- `[x]` Local dependency compose files (`mongodb.yml`, `consul.yml`, `kafka.yml`, `services.yml`) plus Jib image build.
- `[x]` Kafka producer/consumer wired (`broker/KafkaProducer`, `broker/KafkaConsumer`).

## Phase A — finish the current entity model

- `[x]` Generate `PaymentOption` from `.jhipster/PaymentOption.json`: document, repository, resource, `PaymentOptionResourceIT`. (2026-08-03)
- `[~]` Generate `PersonalDocument` from `.jhipster/PersonalDocument.json` (same set) — generated 2026-08-03. Still open: whether document _content_ lives in Mongo or an object store. `url` is a placeholder for whichever is chosen.
- `[x]` Refresh the stale `entities` array in `.yo-rc.json` — now lists all twenty entities. (2026-08-03)
- `[ ]` Coordinate the rename with the dashboard: it still ships `hc-credential` and `hc-pay-option` CRUD screens. Track in `patient-web.md`.
- `[x]` **`MedCase` replaced by `ClinicalCase`** — a different entity, not a rename. The shape is the one
  the professional dashboard generates against
  (`hc-professional/web/.jhipster/ClinicalCase.json`): added `patientId`, `openedAt`, `brief`,
  `assignedProfessionalId`, `assignedRosterId`; `diagnoses` became `diagnosis`; free-text
  `recommendations` became a many-to-many to a new `Recommendation` entity; `closeDate`, `category`
  and the audit fields are gone, and the `CaseCategory` enum with them. Collection is `clinicalcase`,
  REST path `/api/clinical-cases`.
- `[x]` **`Recommendation` added** (`id`, `label`, `category`) with repository, service, resource at
  `/api/recommendations` — unpaged, per its `"pagination": "no"` — and a full CRUD integration test.
  It is the first entity here with a relationship, so the first use of `@DBRef`.
- `[ ]` **Migrate existing `med_case` documents.** This is not a collection rename: `clinicalcase`
  has fields the old documents have no source for. A migration has to decide, per document, what
  `patientId` is (the old entity never recorded one), map `diagnoses` to `diagnosis`, turn the
  free-text `recommendations` string into `Recommendation` documents plus `@DBRef`s, and drop
  `closeDate`/`category`/audit fields. `openedAt` can come from `open_date`, and `brief` has no
  source at all. If the deployed database has no `med_case` documents worth keeping — check before
  assuming — dropping the collection is the cheaper answer. This service still has no migration
  framework (Phase B tracks that).
- `[ ]` Decide whether `Stat` is the home for vitals or whether `VitalStatistic` (Phase C) supersedes it — do this before adding more fields to either.

### `Profile` vs the blueprint spec

The blueprint asked for first/last name, mobile, email, **long-lat**, **digital address**, **street address**, **ID type ∈ {PASSPORT, GHANA_CARD}**, and ID number. The current document (`.jhipster/Profile.json`) has `firstName`, `middleNames`, `lastName`, `membership`, `birthDate`, `sex`, `mobilePhone`, `phoneNumber`, `email`, `cardType`, `cardNumber`, `contacts`, `address`, `team` — all `String` except `birthDate`, and no relationships.

- `[ ]` Model identification properly: `cardType` is a free-text `String`, not an enum. If `{PASSPORT, GHANA_CARD}` is the real domain, add a `domain/enumeration` type (the pattern already exists for `CaseCategory`/`CaseStatus`) and migrate existing values.
- `[ ]` Decide the address shape: one `address` string today versus the blueprint's separate street address, digital address (e.g. Ghana Post GPS), and long-lat. Geo coordinates in particular need a real field type if they are ever queried.
- `[ ]` Reconcile the string references (`membership`, `team`, `contacts`, `address`) with the standalone `Membership`, `Team`, and `Address` documents — decide whether these are ids, denormalized labels, or should become relationships, and write the answer down. Do this before Phase B links `PatientSubscription` to `Profile`.

## Phase B — subscription domain

Blueprint prompt 2.1. Nothing exists yet: no `SubscriptionPlan`, `PatientSubscription`, or related repository/resource in any repo.

- `[ ]` `SubscriptionPlan` document (`name` ∈ {Pear, Melon, Pawpaw}, `monthlyPrice`, `weeklyVisits`, `includedServices`).
- `[ ]` Consume the `SubscriptionPlanCreated` Kafka event emitted by `hc-admin-ms` and project it into `SubscriptionPlan`. Requires a real topic/binding — today the only configured binding is the generated `sse-topic`. Agree the topic name, payload schema, and idempotency rules with the admin service first.
- `[ ]` `PatientSubscription` document mapping a `Profile` to its active plan (effective dates, status).
- `[ ]` Seed the default plans — blocked on decision 1.
- `[ ]` Introduce a migration mechanism. This service has none (the gateway uses Mongock); pick Mongock or a documented startup seeder before writing seed logic.
- `[ ]` REST surface for plan lookup and subscribe/change-plan, following the resource conventions in `.github/instructions/rest-patterns.instructions.md`.
- `[ ]` A unified onboarding endpoint that accepts the mobile app's basic-info + identification + plan-selection DTO in one call (blueprint prompt 3.2 has no backend counterpart today).

## Phase C — telemetry ingestion

Blueprint prompt 2.2. Blocked on decision 2 for storage; the event contract can be agreed in parallel.

- `[ ]` `VitalStatistic` model (`patientId`, `timestamp`, `metricType` ∈ {BP, HR, GLUCOSE}, `value`), indexed for time-range queries per patient.
- `[ ]` `TelemetryService` to validate and persist incoming readings, with an ingest endpoint or Kafka inbound binding (decide which; the dashboard's metric panels currently read from `Stat`).
- `[ ]` Publish `IoTDataReceivedEvent` to a `raw-telemetry` topic on each saved reading, so the professional service can react. Define the payload schema and topic configuration.
- `[ ]` Load expectations: document expected reading volume per patient per day before choosing collection design or retention.
- `[ ]` Integration tests using the existing Testcontainers Kafka setup, asserting both persistence and the emitted event.

## Phase D — platform hardening

- `[ ]` **Align the JWT signing key with the gateway.** The committed `base64-secret` in `application-dev.yml`/`application-prod.yml` differs from the gateway's, so a relayed token fails signature validation. Source both from one env var / Consul KV entry and remove the committed values.
- `[ ]` Paginate the generated `getAll*` endpoints that can grow unbounded (`Stat`, `Report`, `ClinicalCase`, `Metadata` first) — they currently return unpaged `List<Entity>`.
- `[ ]` Add indexes matching the query patterns actually used, once Phase B/C query shapes are known.
- `[ ]` Decide on caching: `cacheProvider` is `"no"`; if read load justifies it, add Spring Cache deliberately rather than per-service ad hoc.
- `[ ]` Rate limiting / abuse monitoring is not implemented in this service. Decide whether it belongs here or at the gateway.
- `[ ]` Wire CI: `.github/workflows/` is empty while `ci:backend:test` and the `ci:e2e:*` scripts exist unused. The dashboard repo publishes to GHCR — mirror that choice or state the difference.
- `[ ]` Remove or ignore the gitignored stale `bin/` copy of the project so it stops appearing in searches and IDE indexes.
- `[ ]` Revisit the `api-docs` profile gate: OpenAPI is disabled unless that profile is active, which means no schema is published in normal dev runs.

## Working agreement for items above

- Every new entity ships as a full slice: `.jhipster` config, document, repository, resource, `*ResourceIT` with the complete CRUD/validation matrix from `.github/instructions/backend-tests.instructions.md`.
- Stay on Spring MVC — no `Mono`/`Flux` in this service (decision 3 is about versions, not about going reactive).
- Respect the ArchUnit layer boundaries; a `service → web` dependency fails the build.
- Verify with `./mvnw verify`; select a single integration test with `./mvnw verify -Dit.test=XResourceIT` (`-Dtest=` cannot match `*IT` classes).
