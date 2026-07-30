# Patient API — Plan

Single plan of record for `hc-patient-service` (`hcPatientService`). It consolidates the backend slices of the Health Connect patient blueprint/checklist (previously kept only in the dashboard repo) with the open items found while auditing this service.

- **Baseline verified:** 2026-07-30 against `pom.xml`, `.yo-rc.json`, `src/main/java`, `src/test/java`, `src/main/resources/config`.
- **Companion docs:** `CLAUDE.md` (what exists and how it is wired), `AGENTS.md` (standing quality/security/performance expectations), `.github/instructions/*.instructions.md` (authoritative REST/test rules).
- **Sibling plans:** `hc-patient-gateway/patient-gateway.md`, `hc-patient-dashboard/patient-web.md`.

Status legend: `[x]` done · `[~]` partial / diverges from plan · `[ ]` not started.

## Open decisions that block work below

1. **Subscription plan prices.** The blueprint says Pear 1000 / Melon 2000 / Pawpaw 5000; the checklist said Pear 3000 / Pawpaw 4000 / Melon 5000. Nothing can be seeded until one set is chosen. Owner: product.
2. **Telemetry datastore.** The blueprint calls for a TimescaleDB `telemetry_db` alongside MongoDB. This service is Mongo-only today — no TimescaleDB image, dependency, or config exists anywhere in the workspace. Adding it means a second datastore plus a JPA/JDBC stack in a service that has neither. Alternative: keep vitals in MongoDB (time-series collections) and revisit if query load demands it.
3. **Spring Boot 4 / Java 26 upgrade.** The gateway is already on Spring Boot 4.0.6 / Java 26; this service is on Spring Boot 3.4.5 with a Java 21 target (Enforcer `[17,27)`). Decide whether to follow, and on what schedule. Note the blueprint's "Java 25" should read 26.
4. **Patient/angel roles.** The blueprint expects a `PATIENT` (or `ANGEL`) role. Only `ROLE_ADMIN`, `ROLE_USER`, `ROLE_ANONYMOUS` exist in `AuthoritiesConstants`, and roles are minted by the gateway — so this is a joint change with `hc-patient-gateway`.

## Baseline — already in place

- `[x]` JHipster microservice scaffold: Consul discovery/config, MongoDB, Kafka (Spring Cloud Stream), JWT resource-server validation, `skipUserManagement`.
- `[x]` Layered architecture with ArchUnit enforcement (`TechnicalStructureTest`).
- `[x]` RFC 7807 error handling via `web/rest/errors/ExceptionTranslator`, covered by `ExceptionTranslatorIT`.
- `[x]` Eleven documents with repository + resource + full CRUD `*ResourceIT`: `Profile`, `Address`, `Condition`, `Medication`, `MedCase`, `Stat`, `Team`, `Task`, `Membership`, `Report`, `Metadata`.
- `[x]` Auditing base class `AbstractAuditingEntity`; Mongo ids are `String` (not UUID).
- `[x]` Local dependency compose files (`mongodb.yml`, `consul.yml`, `kafka.yml`, `services.yml`) plus Jib image build.
- `[x]` Kafka producer/consumer wired (`broker/KafkaProducer`, `broker/KafkaConsumer`).

## Phase A — finish the current entity model

- `[ ]` Generate `PaymentOption` from `.jhipster/PaymentOption.json`: document, repository, resource, `PaymentOptionResourceIT`.
- `[ ]` Generate `PersonalDocument` from `.jhipster/PersonalDocument.json` (same set). Confirm whether document _content_ is stored in Mongo or an object store before finalizing the shape.
- `[ ]` Refresh the stale `entities` array in `.yo-rc.json` (still lists the removed `HCCredential`/`HCPayOption`/`HCDocument`, omits `MedCase`/`PaymentOption`/`PersonalDocument`).
- `[ ]` Coordinate the rename with the dashboard: it still ships `hc-credential` and `hc-pay-option` CRUD screens. Track in `patient-web.md`.
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
- `[ ]` Paginate the generated `getAll*` endpoints that can grow unbounded (`Stat`, `Report`, `MedCase`, `Metadata` first) — they currently return unpaged `List<Entity>`.
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
