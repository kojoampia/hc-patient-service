# Project Guidelines

## Code Style

- Use Maven Wrapper for Java tasks: `./mvnw`.
- Java uses 4-space indentation and is formatted by Spotless during Maven builds.
- JSON/YAML/HTML/Markdown formatting follows Prettier rules in `.prettierrc` and `.editorconfig`.
- Preferred formatting commands:
  - `npm run prettier:check`
  - `npm run prettier:format`

## Architecture

- This is a JHipster Spring Boot 4.0.6 microservice (`net.jojoaddison`) using MongoDB + Kafka, built on **Spring MVC — never reactive `Mono`/`Flux`** (the sibling gateway repo is the reactive one).
- No user management here (`skipUserManagement: true`): JWTs are issued by `hc-patient-gateway` and only validated in this service.
- Lombok is not a dependency — keep JHipster's generated accessors.
- Keep layer boundaries aligned with ArchUnit rules in `src/test/java/net/jojoaddison/TechnicalStructureTest.java`:
  - `config`
  - `web`
  - `service` (optional)
  - `security`
  - `repository` (optional)
  - `domain`
- Put REST endpoints in `src/main/java/net/jojoaddison/web/rest` and business logic in `src/main/java/net/jojoaddison/service`.
- Keep persistence in Spring Data Mongo repositories under `src/main/java/net/jojoaddison/repository`.

## Build And Test

- Development run:
  - `./mvnw`
  - or `npm run app:start`
- Build for production:
  - `./mvnw -Pprod clean verify`
  - `./mvnw -Pprod,war clean verify`
- Unit/integration test entry points:
  - `./mvnw verify`
  - `npm run backend:unit:test`
- Quality checks:
  - `npm run backend:nohttp:test`
  - `./mvnw -Pprod clean verify sonar:sonar -Dsonar.login=admin -Dsonar.password=admin`

## Onboarding and delegation (2026-08-19)

- `PatientScope` is the authorization model and fails closed. `POST /api/onboarding` is the single
  narrow path that may run before a `Profile` exists — do not add a second.
- A care angel's authority is an `ACTIVE` `CareDelegation`, never `ROLE_ANGEL`. Which patient they act
  for arrives in an `X-Acting-As` header, re-checked per request. `PatientScope` reads the delegation,
  never `Profile.careAngelEmail`, which is a display cache.
- Patient data is never deleted: `DELETE` on the sixteen patient-scoped resources requires `ROLE_ADMIN`.
- `source` on clinical entities is stamped from the caller, never from the payload.
- No `patient-events` payload may carry clinical content; `PatientEventPublisher` throws if one would.
- `CareDelegation` has no generated CRUD and no `DELETE` — one endpoint per state transition.

## Conventions

- The build targets Java 25 (`java.version`, pinned via `maven.compiler.release`); the Maven Enforcer accepts JDK 17-25 (`[17,26)`). Maven must be >= 3.2.5.
- Use profile-driven runs/builds (`dev` default, `prod` for release artifacts).
- Integration test naming follows Maven defaults:
  - Unit tests: `*Test.java`
  - Integration tests: `*IT.java` or `*IntTest.java`
- Prefer existing npm scripts in `package.json` when they exist instead of ad-hoc shell commands.

## Environment Prerequisites

- Consul is required at `http://localhost:8500`; app startup fails without it.
- MongoDB and Kafka are required dependencies for local development.
- Useful service helpers:
  - `npm run docker:consul:up`
  - `npm run docker:db:up`
  - `npm run docker:kafka:up`
  - `npm run services:up`

## Key References

- See `README.md` for operational workflows and Docker compose usage.
- See `pom.xml` for profiles, plugin behavior, Java/Maven constraints, and test plugin setup.
- See `package.json` for standard local commands used by this repository.
- Use file-scoped instructions in `.github/instructions/` for REST and test-specific rules.
- See `CLAUDE.md` for the verified stack/architecture summary and entity status, `patient-api.md` for the plan of record (open decisions and phased backlog), and `AGENTS.md` for quality/security/performance expectations.
- Ignore `bin/` — it is a gitignored stale copy of the project.
