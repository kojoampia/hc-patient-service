# Patient API — Plan

Single plan of record for `hc-patient-service` (`hcPatientService`). It consolidates the backend slices of the Health Connect patient blueprint/checklist (previously kept only in the dashboard repo) with the open items found while auditing this service.

- **Baseline verified:** 2026-08-03 against `pom.xml`, `.yo-rc.json`, `patient.jdl`, `src/main/java`, `src/test/java`, `src/main/resources/config`, and a full `./mvnw clean verify`. (Previous baseline 2026-07-30, before the patient-context entity work.)
- **Companion docs:** `CLAUDE.md` (what exists and how it is wired), `AGENTS.md` (standing quality/security/performance expectations), `.github/instructions/*.instructions.md` (authoritative REST/test rules).
- **Sibling plans:** `hc-patient-gateway/patient-gateway.md`, `hc-patient-dashboard/patient-web.md`.

Status legend: `[x]` done · `[~]` partial / diverges from plan · `[ ]` not started.

## What changed since the last baseline

### Patient onboarding, care delegation and the first domain events (2026-08-19)

The largest change this service has taken. `docs/onboarding.md` is the plan of record and its §16 is
the contract; this is what landed here.

**Onboarding.** `POST /api/onboarding` is the one path that may run before a `Profile` exists —
because `PatientScope.requirePatientIdForWrite` refuses every clinical write to a caller it cannot
resolve to a patient, a newly registered person could not create the record that would grant them the
right to create it. It acts only on the token's email, ignores `email`/`patientId`/`id` in the
payload, and 409s once a record exists, so it succeeds exactly once per account. Steps 2–5 are named
endpoints (`/care-angel`, `/baseline`, `/current-state`, `/identification`) with a DTO each — the one
place this service has a DTO layer, and deliberately, because the payload must not be a `Profile`.

**Care delegation.** `CareDelegation` is the record an angel's access rests on, and `PatientScope`
reads it — never `Profile.careAngelEmail`, which is a display cache. `ROLE_ANGEL` grants nothing.
Which patient an angel is acting for arrives in an `X-Acting-As` header re-checked per request, so a
revocation bites on the next one rather than when a token expires. Reaching a patient's record from a
dormant `STANDBY` nomination takes three gates: the patient's advance consent, two _different_
professionals, and the nominee's own acceptance.

**Nothing patient-owned is deleted.** Sixteen resources had a `DELETE` guarded only by `isVisible`,
which is true for a patient's own records — so a patient could permanently remove their own profile,
conditions, allergies, medications and reports. All sixteen now require `ROLE_ADMIN`.

**Provenance is stated, not inferred.** `Condition`, `Allergy`, `Medication` and `Stat` carry
`source`, stamped from the caller on create and preserved on update. Nothing set it before, so a
client could post an allergy marked `PROFESSIONAL` and have it read as clinician-attested forever.

**`Profile.address` is a `@DBRef` to `Address`,** with `AddressAsDocumentMigration` — the first
Mongock change unit in this service — reshaping existing free-text values. `LenientAddressDeserializer`
keeps the old string form readable, which matters more than it sounds: `DevelopmentDataInitializer`
answers a failed read by loading _nothing at all_, so one stale address would have emptied the whole
seeded dataset.

**The first real domain events.** The journey publishes to `patient-events` (§8 of the plan), keyed
by lowercased email. No event carries clinical content and `PatientEventPublisher` throws if a
payload would. Publishing is best-effort and never fails the operation — which is also how a Kafka
key-serializer mismatch silently dropped every event while the suite stayed green, until
`PatientEventRoundTripIT` published and read one back.

### Duty rosters, shifts, and a seeded demo dataset (2026-08-11)

`deploy/professional-dashboard-demo-data.json` was an untracked file describing one clinician, their
duty roster, and seven patients with cases, visits, activity, medication and reports. It now seeds
into MongoDB on startup, and the two things in it the domain could not express now exist.

- **Two new entities: `DutyRoster` and `Shift`**, plus the `ShiftStatus` enum
  (ACTIVE/UPCOMING/COMPLETED). `ClinicalCase.assignedRosterId` has named nothing since the portal
  refactor introduced it; these are what it points at. Both are **staff reference data** and follow
  `Team`/`Professional`: repository-direct resources, no `patientId`, no `PatientScope`, readable by
  any authenticated caller and writable only by `ROLE_ADMIN`/`ROLE_PROFESSIONAL`. `ReferenceDataIT`
  covers that rule for them alongside the four entities it already covered.
- **`ShiftStatus` is not `ScheduleStatus`.** The latter is an appointment's lifecycle (confirmed,
  pending, attended, cancelled) and answers a different question — a shift is not attended or
  cancelled, it starts and it ends.
- **`DemoDataInitializer`** (`config/dbmigrations/`) seeds ten collections from
  `src/main/resources/config/demo-data/professional-dashboard-demo-data.json`, **under `dev` and
  `test` only**. It is an `ApplicationRunner`, not a Mongock change unit, for the reason the gateway
  learned the hard way: a change unit has no notion of a profile and runs exactly once. Seeding is
  additive and idempotent — every record carries a fixed id and is written only when absent, so
  records edited through the API survive a restart and a dropped collection is restored.
- **The clinician joins to a real login.** The demo file identifies its professional by
  `accountLogin: "doctor"`, which `Professional` has no field for; the gateway now seeds a matching
  `doctor` account and the join is `doctor@localhost`. That account is the only holder of
  `ROLE_PROFESSIONAL` anywhere — see `hc-patient-gateway/patient-gateway.md`.
- **Verified:** `./mvnw verify` — 117 unit tests, 450 integration tests, coverage gates met.

Open, and deliberately left so:

- [ ] **`DutyRoster.subscribedProfessionalIds` is not in `patient.jdl`.** JDL has no list-of-scalars
      type, so regenerating that entity silently drops the field. The alternative — a `@DBRef`
      many-to-many to `Professional` — was rejected as the second relationship in a domain that
      holds every other cross-entity reference as a bare String id. The field carries a comment
      saying it must be re-added by hand; if the domain ever grows a second such field, revisit this
      rather than repeat it.
- [ ] **Two demo fields are not seeded.** A patient's `lastActivityAt` (`ActivityLog` is the real
      source) and `isChild` (derivable from `dateOfBirth`). Nothing was invented to fill a gap — a
      case with no `caseNumber` in the file is stored without one.

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

1. ~~**Subscription plan prices.**~~ Settled 2026-08-19, by removing the question. This subsystem states no price: the dashboard renders Abofonsa's pre-formatted `priceAmount` through a gateway proxy at `/api/plans`, and choosing a tier writes a `Membership` carrying the plan code and nothing about money. Two products quoting different numbers for one tier stays fixed by never restating one.
2. **Telemetry datastore.** The blueprint calls for a TimescaleDB `telemetry_db` alongside MongoDB. This service is Mongo-only today — no TimescaleDB image, dependency, or config exists anywhere in the workspace. Adding it means a second datastore plus a JPA/JDBC stack in a service that has neither. Alternative: keep vitals in MongoDB (time-series collections) and revisit if query load demands it.
3. ~~**Spring Boot 4 / Java 25 upgrade.**~~ Settled 2026-08-04: this service followed the gateway onto Spring Boot 4.0.6 and Java 25. Both now target the same Boot major and the same JDK — worth keeping that way, since the two share a JWT contract and the Jackson/Mongo breakages above all came from the halves drifting apart.
4. ~~**Patient/angel roles.**~~ Settled 2026-08-19. The gateway grants `ROLE_USER` + `ROLE_PATIENT` at registration and `ROLE_ANGEL` to a nominated care angel's account. What the answer turned out to be is worth keeping: **neither role authorizes anything here.** A patient's access comes from `PatientScope` resolving their email to a `Profile`; an angel's comes from an `ACTIVE` `CareDelegation` re-read on every request. Authorizing on the role would mean a revoked angel kept access for as long as their token lived.

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
- `[x]` Refresh the stale `entities` array in `.yo-rc.json` — now lists all twenty-two entities, `DutyRoster` and `Shift` included. (2026-08-03, extended 2026-08-11)
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

## Phase E — what the portal is blocked on (2026-08-16)

Three items on the dashboard's parity list (`web/patient-web.md`, E3/E4) cannot be finished in that
repo: they need fields and an endpoint here. The dashboard shipped everything around them first, so
this is the remainder, not the start.

### Decisions taken 2026-08-16

Asked and answered before building, because each changes what gets written:

1. **Uploaded report files live in GridFS, in the MongoDB this service already uses.** No new
   service, no new port, no credentials, and one backup covers both the documents and their files.
   `spring-boot-starter-data-mongodb` is already on the classpath, so this is configuration rather
   than a dependency. The alternatives were a host volume — which the architect would have to
   provision on both machines, since [[the remote machines are not this workspace's]] — and an
   object store, which is a service to run on a capacity-constrained host for a demo feature.
2. **A report accepts PDF, JPEG, PNG and HEIC, up to 10 MB.** A photographed lab slip is what a
   patient actually has; PDF-only means they do not upload it at all. The type is decided from the
   _bytes_, not from the filename, and the filename is stored but never used as a path.
3. **A reading records `source` beside `recordedById`**, exactly as `ActivityLog` already does —
   `PATIENT`, `PROFESSIONAL` or `DEVICE`. A home glucose reading the patient took themselves then
   reads "You" through the attribution rule the portal already has, instead of naming a clinician
   who was not there.
4. **This goes end to end**: fields here, values in `quality/patient-demo-seed.json`, rendering in
   `web`. A field nobody can see is not a fix; D3 and D5 stay open on the parity list until the
   portal shows them.

### The work

All six done 2026-08-16. Two things the tests caught that the change itself had missed, both worth
keeping in mind for the next field added here: the generated **partial-update** copies fields one by
one, so a new field is silently dropped by PATCH until it is named there; and `ProfessionalResource`
**redacts by whitelist** for non-staff callers, so a new field is invisible to every patient until it
is named there too. The redaction one is the dangerous direction — it fails closed, quietly, and only
for the people the portal is for.

- `[x]` **C10a · `Report` gains a file.** `POST /api/reports/{id}/file` (multipart) and
  `GET /api/reports/{id}/file` (streams). Both go through `PatientScope` like every other endpoint
  here, so a file is visible exactly when its report is; the upload sets `url` to the download path
  so the portal's existing "Open file" button keeps working unchanged.
- `[x]` **C10b · type and size enforced from the bytes**, per decision 2, with the rejection saying
  which types are accepted rather than only that this one was not.
- `[x]` **D3 · `Professional.honorific`**, nullable. The audit assumed the seed already held it; it
  does not, and neither did the model. Deriving one from `role` would invent a title — a
  physiotherapist and a nurse are not "Dr." — so it is a field with a value, or it is nothing.
- `[x]` **D5 · `Stat.source` and `Stat.recordedById`**, per decision 3.
- `[x]` **Seed values** for both in `quality/patient-demo-seed.json` (separate repo, separate PR).
- `[x]` **Portal rendering** in `web` (separate repo, separate PR): the honorific wherever a
  professional is named, "Recorded … by …" on a vital, and **Upload a report**, which closes C10.

## Phase B — subscription domain

Blueprint prompt 2.1. Nothing exists yet: no `SubscriptionPlan`, `PatientSubscription`, or related repository/resource in any repo.

- `[ ]` `SubscriptionPlan` document (`name` ∈ {Pear, Melon, Pawpaw}, `monthlyPrice`, `weeklyVisits`, `includedServices`).
- `[ ]` Consume the `SubscriptionPlanCreated` Kafka event emitted by `hc-admin-ms` and project it into `SubscriptionPlan`. Requires a real topic/binding — today the only configured binding is the generated `sse-topic`. Agree the topic name, payload schema, and idempotency rules with the admin service first.
- `[ ]` `PatientSubscription` document mapping a `Profile` to its active plan (effective dates, status).
- `[ ]` Seed the default plans — blocked on decision 1.
- `[ ]` Introduce a migration mechanism. This service has none (the gateway uses Mongock); pick Mongock or a documented startup seeder before writing seed logic.
- `[ ]` REST surface for plan lookup and subscribe/change-plan, following the resource conventions in `.github/instructions/rest-patterns.instructions.md`.
- `[x]` ~~A unified onboarding endpoint~~ — built 2026-08-19, but **not** as one call. Five named
  endpoints with a DTO each, because the steps carry genuinely different payloads and one handler
  taking five shapes could only be typed as a map. There is no transaction to make a single call
  atomic anyway (standalone Mongo), so the journey is built to be resumable instead. Plan selection
  is deliberately _not_ part of it: it is a portal surface fed by Abofonsa, so a patient is never
  blocked mid-onboarding by another product being down. See `docs/onboarding.md` §16.

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
