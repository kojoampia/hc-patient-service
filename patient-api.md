# Patient API — Plan

Single plan of record for `hc-patient-service` (`hcPatientService`). It consolidates the backend slices of the Health Connect patient blueprint/checklist (previously kept only in the dashboard repo) with the open items found while auditing this service.

- **Baseline verified:** 2026-08-03 against `pom.xml`, `.yo-rc.json`, `patient.jdl`, `src/main/java`, `src/test/java`, `src/main/resources/config`, and a full `./mvnw clean verify`. (Previous baseline 2026-07-30, before the patient-context entity work.)
- **Companion docs:** `CLAUDE.md` (what exists and how it is wired), `AGENTS.md` (standing quality/security/performance expectations), `.github/instructions/*.instructions.md` (authoritative REST/test rules).
- **Sibling plans:** `hc-patient-gateway/patient-gateway.md`, `hc-patient-dashboard/patient-web.md`.

Status legend: `[x]` done · `[~]` partial / diverges from plan · `[ ]` not started.

## What changed since the last baseline

### Scope of practice: a discipline decides what kind of data (2026-08-22)

- [x] `ScopeOfPractice` + `ClinicalDomain`: one table mapping the eight clinical roles onto six kinds
      of patient data, wired into the write paths of eleven resources through `PatientScope`.
- [~] **The table is a starting position, not a clinical ruling.** Written from the shape of the data
  rather than from anybody's scope of practice, and it says so at the top. The rows most likely to
  be wrong, flagged rather than buried: whether a carer may read the diagnosis (currently no),
  whether a therapist may write observations (no), whether a paramedic should write medications
  given at scene (no). Correcting it is a two-line change in one file, deliberately.
- [ ] **Reads are not filtered.** `canRead`/`requireRead` exist and are tested; nothing calls them, so
      today the table restricts writing only.
- [ ] `RecommendationResource` is not wired — it has no `patientId` and no `PatientScope`, being
      reference data joined to a case rather than a patient record.

The request was to _port_ hc-professional's gateway authority rules. **There were none to port.** That
gateway defines the nine roles and seeds them as Authority documents, and nothing anywhere enforces
any of them — its `SecurityConfiguration` only distinguishes `ADMIN` from `authenticated`, and a grep
for those roles in any `hasAuthority` or `@PreAuthorize` across that gateway _and_ its api returns
nothing. What exists there is a vocabulary; porting it verbatim would have given this service nine
constants that grant and restrict nothing, which is the situation `ROLE_ANGEL` is already in.

Underneath it was a live defect, and it is the reason this landed as a fix rather than a feature:
hc-professional's gateway has **no `ROLE_PROFESSIONAL` at all**, this service gated thirty places on
exactly that role, and the two stacks share a JWT signing key. So a doctor signing in there reached
this service holding `ROLE_DOCTOR`, failed every check, resolved to no patient, and was served empty
lists rather than a refusal — silent, and indistinguishable from a patient with no records. Inferred
from the code, pinned by a test, and since demonstrated on the quality stack once
`hc-patient-quality` seeded an account per discipline.

### Decisions taken 2026-08-24, not yet built

Answers given by the architect on the day of the `ROLE_PROFESSIONAL` removal. Recorded here because
each changes what the code should say and none of them is in it yet.

- [x] **A nurse may countersign an incapacity declaration; only a doctor may declare one.** Built
      2026-08-24. `CareDelegationResourceIT` covers both directions and the identity rule; 16 tests
      green. The javadoc says explicitly not to "fix" the apparent inconsistency by granting nurses
      `DIAGNOSIS` writes in `ScopeOfPractice` — that would let them author diagnoses everywhere,
      which is what the table refuses, and a countersignature is not a data-access question at all.
      `/activate` stays `ROLE_DOCTOR`, `/countersign` widens to doctor **or** nurse. The reasoning is
      the one this file already records under the removal: requiring two doctors is clinically
      strictest and practically unobtainable in home healthcare, where a second doctor may not be
      reachable at all. The diagnosis judgement — asserting the patient lacks capacity — stays with a
      doctor; the second signature confirms it rather than makes it. **The service check must stay
      "somebody other than the declarer"**, which is what makes two signatures mean two people.
      One line in `CareDelegationResource` plus a note in `ScopeOfPractice` explaining why countersign
      is the one place a nurse touches a `DIAGNOSIS`-adjacent act, and `CareDelegationResourceIT`
      coverage for doctor-declares/nurse-countersigns and for nurse-cannot-declare.

- [x] **Reviewed and three rows changed, 2026-08-24.** Carer gained `DIAGNOSIS` reads — a carer alone
      with a patient at home who does not know they are diabetic or epileptic may not recognise what
      they are watching happen, and that was judged to outweigh the disclosure. Therapist gained
      `MEDICATION` reads, which was an omission rather than a decision: anticoagulants and beta
      blockers change what is safe to do and how a pulse should be read. Paramedic gained `MEDICATION`
      writes, so a drug given in an emergency can be recorded rather than leaving the next clinician
      to prescribe against an incomplete history. **All three widened**, which is the direction this
      table's bias predicts: it refuses when unsure, so its errors accumulate where a clinician
      notices within minutes rather than where nobody ever does.
- [x] **A chemist is a _dispensing_ chemist, and that row was wrong. Fixed 2026-08-24.** It had been
      written as a copy of the technician's on the assumption that "chemist" meant a laboratory role,
      which left somebody who hands medicines to patients unable to read the medication record or the
      allergies — and nothing failed, they simply saw an empty list. `ClinicalDomain.MEDICATION` groups
      allergies with medications for precisely this case, in its own words: anyone who may dispense
      must be able to see what would harm the patient. Now reads and writes `MEDICATION`; the
      technician's row is unchanged, so the two finally differ.
- [x] **A dispensing chemist reads the diagnosis too, confirmed 2026-08-24.** Same reason the
      pharmacist does: dispensing safely means knowing what the medicine is _for_, and a dispenser who
      cannot see the indication cannot catch a medicine that is wrong for the condition. Reads only —
      dispensing is not diagnosing.
- [ ] **The chemist is now wider than the pharmacist, and that is the next thing to be wrong.** The
      chemist holds everything the pharmacist holds _and_ observations, so the less qualified role is
      the wider one. **The fault is almost certainly on the pharmacist's side**: renal function and
      weight change dosing, and a pharmacist can currently see neither. A test fails the day somebody
      fixes it, deliberately — widening the pharmacist is a clinical decision, not a symmetry
      argument, and it should not happen silently.
- [ ] **The model has no notion of a doctor's specialty** — a dermatologist and a psychiatrist are the
      same row, and both hold the whole record. The largest simplification left in the table.
- [x] ~~**`ScopeOfPractice` goes for clinical review, and until then it is treated as blocking rather
      than provisional.**~~ The table says at its top that it is a starting position written from the
      shape of the data rather than from anybody's scope of practice, and names carer, paramedic and
      pharmacist as the rows most likely wrong. Somebody with the standing to rule on scope of practice
      reviews all eight rows. **Deliverable for that review: one page per row — what it grants, what it
      refuses, and the concrete consequence of each being wrong in either direction**, because the two
      directions are not symmetric and the table's own note says so: a nurse wrongly locked out of
      vitals is discovered in minutes, a technician wrongly able to read diagnoses is a disclosure
      nobody notices.

- [x] **Reads are filtered as of 2026-08-24.** `requireRead` is wired into all 23 GET endpoints of the
      eleven scoped resources, each on the domain that resource already used for writes.
      `ScopeOfPracticeReadsIT` asserts the endpoints rather than the table — a test saying "a
      pharmacist cannot read DIAGNOSIS" against `ScopeOfPractice` restates the table in more words,
      while one saying `GET /api/clinical-cases` returns 403 is the thing that would have failed
      before. 559 ITs green. **Archiving is still outstanding**, and is the second half below.

- [ ] ~~**Reads get filtered before archiving is extended**~~, and both are ahead of the rest of this
      backlog. They are the two cross-cutting authorization items here rather than features. Reads
      first because it is the disclosure risk: `canRead`/`requireRead` exist and are tested and
      **nothing calls them**, so the scope-of-practice model is enforced on writes only and a
      pharmacist can read a diagnosis today. Archiving second because it is a usability gap rather than
      an exposure — the DELETE lockdown covers sixteen resources and archiving replaces the delete for
      exactly one. Two PRs, reads then archiving.

### `ROLE_PROFESSIONAL` removed from the platform (2026-08-24)

- [x] **The blanket clinical authority is gone.** It was minted by `hc-patient`'s gateway alone and
      checked by this service alone — an authority this subsystem invented for itself and then required
      of everybody else. Every check that named it was a check no clinician from `hc-professional`, the
      portal that owns the case queue, could pass. Outside `hc-patient` the string survives only as prose
      describing this defect, plus a display-only `CredentialRole` enum in `hc-admin/app`; `hc-admin`'s
      gateway never had it either.
- [x] **What replaced it, and the distinction that decides which.** The twenty-four reference-data
      checks (`DutyRoster`, `Shift`, `Team`, `Professional`, `Metadata`, `Recommendation`) ask whether
      the caller is clinical staff at all, and take `ROLE_ADMIN` or any of `AuthoritiesConstants.CLINICAL`
      — no discipline has a better claim than another to read a duty roster. Archive, unarchive and
      `CareDelegation`'s activate and countersign turn on a clinical judgement and take `ROLE_DOCTOR`,
      because `ScopeOfPractice` grants `DIAGNOSIS` writes to doctor alone. **Replacing a blanket role
      with a blanket set everywhere would have kept the defect under a new name.**
- [x] **`ScopeOfPractice` lost its first row**, so `isClinical` now means "holds one of the eight
      disciplines" and nothing else. That is what `PatientScope.isUnrestricted()` consults, so a
      pre-cutover token carrying `ROLE_PROFESSIONAL` no longer gets cross-patient access either — asserted
      by literal string in three tests, because the constant is gone and the tokens are not.
- [x] **Two spellings of the set, and a test between them.** `CLINICAL` is a `Set` for Java;
      `CLINICAL_AUTHORITIES` is pre-quoted for `@PreAuthorize`, which takes a compile-time constant and
      cannot read a `Set`. `AuthoritiesConstantsUnitTest` asserts they name the same eight — without it, a
      ninth discipline added to one and not the other is a silent hole in twenty-four checks that still
      compiles and still deploys.
- [ ] **The migration is not this repo's alone.** `hc-patient/gateway` stops minting the authority and
      removes it from the accounts that hold it; `hc-patient/quality` reseeds its five clinicians onto
      real disciplines. Until those land, this service simply stops honouring a role those stacks still
      issue, which reads as a permissions regression rather than a removal.

### Archiving a clinical case (2026-08-22)

- [x] `POST /api/clinical-cases/{id}/archive` and `/unarchive`, `ROLE_PROFESSIONAL`, reason required.
- [x] **Now `ROLE_DOCTOR` alone, 2026-08-24.** Admitted alongside `ROLE_PROFESSIONAL` first, then left
      as the only authority when that role was removed from the platform (below). Doctor and not any
      other discipline because `ScopeOfPractice` grants `DIAGNOSIS` writes to doctor alone and
      `ClinicalDomain` maps `ClinicalCase` to `DIAGNOSIS`. Still `@PreAuthorize` rather than
      `requireWrite(DIAGNOSIS)`, deliberately: `PatientScope` returns true for `ROLE_ADMIN` before it
      consults `ScopeOfPractice`, so `requireWrite` would quietly admit the operational role this
      endpoint excludes on purpose. `ClinicalCaseArchiveIT` asserts doctor, admin, nurse and the removed
      blanket role.
- [x] `archivedAt` / `archivedById` / `archiveReason` on `ClinicalCase`; `GET` excludes archived
      unless `includeArchived=true`, and `GET /{id}` still returns one.
- [x] `PUT` and `PATCH` cannot reach the archive fields. Found reviewing the diff: `PUT` replaces the
      document wholesale, so without carrying the stored state over, anybody who may edit a case could
      archive it by sending a field — and choose whose name went on it.
- [x] **Ten of the other fifteen got archiving on 2026-08-24.** `ActivityLog`, `Allergy`,
      `CarePlanItem`, `Condition`, `Emergency`, `Medication`, `Report`, `Stat`, `Task`, `Visitation` —
      every resource that maps to a `ClinicalDomain`. The behaviour lives once in `ArchiveSupport`
      behind an `Archivable` interface, rather than in ten copies where the tenth drifts. The
      authority is **derived** from each entity's domain rather than named per endpoint, so archiving
      can never be wider than editing, and `ArchiveEveryClinicalRecordIT` asserts that property for
      all ten.
- [ ] **The five administrative resources are deliberately not done**: `Address`, `Membership`,
      `PaymentOption`, `PersonalDocument`, `Profile`. None maps to a `ClinicalDomain`, and retiring
      one is a different act — archiving a `Profile` deactivates a patient, archiving a
      `PaymentOption` is billing housekeeping. Copying the clinical pattern onto them would have
      answered a question nobody has asked. **What is needed first is the decision about what
      archiving one of these means**, not more code.

Archiving had been the _named_ replacement for that lockdown since it landed, and had never existed —
`PatientScopeEveryEndpointIT` said so in a comment, and `hc-professional/web` implemented it in a
client-side `Set` with "No archive endpoint specced" written beside it. **That client is still
unwired**; a case retired there is still in every other clinician's queue and returns on reload.

### Searching the patient directory (2026-08-22)

- [x] `GET /api/profiles?search=` over first name, middle names, last name, email, mobile phone and
      `patientId`, through `findScopedPage` so a scoped caller's search narrows within their scope
      rather than escaping it.
- [x] `ProfileSearch.escape`. The term is interpolated into a `$regex`, which makes it code rather
      than data: unescaped, `.*` returns the whole directory, `(a+)+$` backtracks exponentially over
      every profile, and `(024) 555` — how people write phone numbers — arrives as a syntax error.
- [ ] No index. A regex scan is fine at the current size and will not stay fine; a text index or a
      normalised search field is the next step if the directory keeps growing.

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
  any authenticated caller and writable only by `ROLE_ADMIN` or a clinical discipline. `ReferenceDataIT`
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
  `doctor` account and the join is `doctor@localhost`. That account held `ROLE_PROFESSIONAL` until
  2026-08-24 and now holds `ROLE_DOCTOR` — see `hc-patient-gateway/patient-gateway.md`.
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

- `[x]` ~~**Align the JWT signing key with the gateway.**~~ **Done 2026-08-05**, recorded here 2026-08-21 after the
  claim was found still standing in three plan files and in `docs/CLAUDE.md`. `application-dev.yml` now carries the
  SAME committed key as `hc-patient-gateway` — public by construction and labelled as such, so `./mvnw` works with no
  setup in either repo — and `application-prod.yml` carries `${JWT_BASE64_SECRET:}` with **no default**, so a
  production JVM that is not given one fails rather than silently minting tokens nobody can validate. `.yo-rc.json`
  holds an empty `jwtSecretKey` in both repos.

  Verified by comparing the values, not the comments: dev matches, prod matches, `.yo-rc.json` matches, and neither
  repo's `src/test/resources/config/application.yml` sets it — which matters, because that file REPLACES the main one
  wholesale rather than merging, so a key set only in main is a key no test can see.

  **The remaining risk is a partial injection**, not a mismatched default: one service handed a
  `JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET` the other was not. `deploy/prod-server/observability/hc-patient-rules.yaml`
  already alerts on the 401 pattern that produces, and says to compare the variable across both containers first.

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
