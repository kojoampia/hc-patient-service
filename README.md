# Health Connect Patient Service

This is the **Health Connect Patient Data Micro Service** — a backend-only microservice (no frontend) that manages patient data for the Health Connect platform. It was originally generated using JHipster 8.1.0; entities have since been regenerated with the JHipster 9.1.0 generator (see `jhipsterVersion` in `.yo-rc.json`).

For JHipster documentation and help, visit [https://www.jhipster.tech/documentation-archive/v8.1.0](https://www.jhipster.tech/documentation-archive/v8.1.0).

This is a "microservice" application intended to be part of a microservice architecture, please refer to the [Doing microservices with JHipster][] page of the documentation for more information.
This application is configured for Service Discovery and Configuration with Consul. On launch, it will refuse to start if it is not able to connect to Consul at [http://localhost:8500](http://localhost:8500). For more information, read our documentation on [Service Discovery and Configuration with Consul][].

Authentication tokens are issued by the companion gateway (`hc-patient-gateway`); this service only validates them and has no user management of its own (`skipUserManagement: true`). The two repos ship **different** `base64-secret` values in `application-dev.yml`/`application-prod.yml`, so both must be pointed at the same secret (env var or Consul KV) before a gateway-issued token will be accepted here.

## Technology Stack

Values below come from `pom.xml` and `.yo-rc.json` — update them here whenever those change.

| Component         | Technology                                                   |
| ----------------- | ------------------------------------------------------------ |
| Language          | Java 25 target (`java.version`); Maven Enforcer allows 17–25 |
| Framework         | Spring Boot 4.0.6 / jhipster-framework 9.0.0 (Spring MVC)    |
| Database          | MongoDB (`mongo:7.0.4` locally)                              |
| Message Broker    | Apache Kafka (Confluent Platform 7.6.0)                      |
| Service Discovery | Consul (`bitnami/consul:1.17.0`)                             |
| Authentication    | JWT validation only (tokens minted by the gateway)           |
| Build Tool        | Maven (via `./mvnw`), Maven ≥ 3.2.5                          |
| Container image   | Jib, base `eclipse-temurin:25-jre`                           |
| Server Port       | 8081 (default)                                               |

## Domain Entities

Implemented as `@Document` classes with a repository, REST resource, and `*ResourceIT`:

- **Profile** – patient profile information
- **Address** – patient address details
- **Condition** – medical conditions
- **Medication** – prescribed medications
- **Stat** – patient statistics / vitals
- **Team** – care team members
- **Task** – care tasks and actions
- **Membership** – plan or program memberships
- **Report** – patient reports
- **Metadata** – extensible metadata
- **ClinicalCase** – clinical cases: who the case is about and who it is assigned to (`patientId`,
  `assignedProfessionalId`, `assignedRosterId`), a short `brief`, `status`, `symptoms`, `diagnosis`, `openedAt`,
  and a many-to-many to **Recommendation**. REST path `/api/clinical-cases`, collection `clinicalcase`
- **Recommendation** – labelled, categorised recommendations attached to clinical cases (`/api/recommendations`)

Configured in `.jhipster/` but not yet generated as Java code: **PaymentOption** and **PersonalDocument** (renamed from `HCPayOption` and `IDocument`).

`MedCase` was **replaced** by `ClinicalCase` — not renamed. The two share only `symptoms` and `status`:
`ClinicalCase` adds `patientId`, `openedAt`, `brief`, `assignedProfessionalId` and `assignedRosterId`, renames
`diagnoses` to `diagnosis`, turns free-text `recommendations` into a relationship to the new `Recommendation`
entity, and drops `closeDate`, `category` and the audit fields. The `CaseCategory` enum went with it. The shape is
defined by `hc-professional/web/.jhipster/ClinicalCase.json`, which the professional dashboard generates against.

`HCCredential`, `HCPayOption`, and `HCDocument`/`IDocument` have been removed from this service. The `entities` array in `.yo-rc.json` still lists the old names and is stale — treat `.jhipster/*.json` plus the `domain` package as the source of truth.

## Project Structure

Node.js is required for development tooling (Prettier, Husky, commit hooks). `package.json` provides a better development experience with linting scripts, Docker helpers, and build shortcuts.

In the project root, JHipster generates configuration files for tools like git, prettier, husky, and others.

`/src/*` structure follows default Java structure.

- `.yo-rc.json` — Yeoman configuration file; JHipster configuration is stored under the `generator-jhipster` key.
- `.jhipster/*.json` — JHipster entity configuration files
- `/src/main/docker` — Docker Compose configurations for the application and all dependent services

## Prerequisites

Before starting the application, ensure the following services are running:

| Service | Default Port | Start command              |
| ------- | ------------ | -------------------------- |
| MongoDB | 27017        | `npm run docker:db:up`     |
| Consul  | 8500         | `npm run docker:consul:up` |
| Kafka   | 9092         | `npm run docker:kafka:up`  |

Or start all required services at once:

```
npm run services:up
```

## Development

To start your application in the dev profile, run:

```
./mvnw
```

Alternatively, using the npm script:

```
npm run app:start
```

To run with remote debugging enabled on port 8000:

```
npm run backend:debug
```

For further instructions on how to develop with JHipster, have a look at [Using JHipster in development][].

## Building for production

### Packaging as jar

To build the final jar and optimize the hcPatientService application for production, run:

```
./mvnw -Pprod clean verify
```

To ensure everything worked, run:

```
java -jar target/*.jar
```

Refer to [Using JHipster in production][] for more details.

### Packaging as war

To package your application as a war in order to deploy it to an application server, run:

```
./mvnw -Pprod,war clean verify
```

### JHipster Control Center

JHipster Control Center can help you manage and control your application(s). You can start a local control center server (accessible on http://localhost:7419) with:

```
docker compose -f src/main/docker/jhipster-control-center.yml up
```

## Testing

### Spring Boot tests

To launch your application's tests, run:

```
./mvnw verify
```

Or using the npm script (suppresses verbose logging):

```
npm run backend:unit:test
```

To run a single class or method — note that integration tests (`*IT`) are selected with failsafe's `-Dit.test`, because surefire is configured to exclude `**/*IT*`:

```
./mvnw test -Dtest=SecurityUtilsUnitTest             # unit test
./mvnw verify -Dit.test=ProfileResourceIT            # one integration test
./mvnw verify -Dit.test=ProfileResourceIT#createProfile
./mvnw verify -DskipITs                              # unit tests only
```

Integration tests (`*IT`) start MongoDB and Kafka through Testcontainers via `@IntegrationTest`, so they do **not** need `npm run services:up` first — only a working Docker daemon.

### HTTP URL checks

```
npm run backend:nohttp:test
```

## Others

### Code quality using Sonar

Sonar is used to analyse code quality. You can start a local Sonar server (accessible on http://localhost:9001) with:

```
docker compose -f src/main/docker/sonar.yml up -d
```

Note: we have turned off forced authentication redirect for UI in [src/main/docker/sonar.yml](src/main/docker/sonar.yml) for out of the box experience while trying out SonarQube, for real use cases turn it back on.

You can run a Sonar analysis using the [sonar-scanner](https://docs.sonarqube.org/display/SCAN/Analyzing+with+SonarQube+Scanner) or the Maven plugin.

Then, run a Sonar analysis:

```
./mvnw -Pprod clean verify sonar:sonar -Dsonar.login=admin -Dsonar.password=admin
```

If you need to re-run the Sonar phase, please be sure to specify at least the `initialize` phase since Sonar properties are loaded from the sonar-project.properties file.

```
./mvnw initialize sonar:sonar -Dsonar.login=admin -Dsonar.password=admin
```

Additionally, instead of passing `sonar.password` and `sonar.login` as CLI arguments, these parameters can be configured from [sonar-project.properties](sonar-project.properties) as shown below:

```
sonar.login=admin
sonar.password=admin
```

For more information, refer to the [Code quality page][].

### Using Docker to simplify development (optional)

You can use Docker to improve your JHipster development experience. A number of Docker Compose configurations are available in the [src/main/docker](src/main/docker) folder to launch required third-party services.

For example, to start a MongoDB database in a Docker container, run:

```
docker compose -f src/main/docker/mongodb.yml up -d
```

To stop it and remove the container, run:

```
docker compose -f src/main/docker/mongodb.yml down
```

To start all required services (MongoDB, Consul, Kafka) together:

```
docker compose -f src/main/docker/services.yml up -d
```

#### Development Docker image

You can also fully dockerize your application. First build a Docker image of your app:

```
npm run java:docker
```

Or build an arm64 Docker image when using an arm64 processor (e.g., MacOS with Apple Silicon):

```
npm run java:docker:arm64
```

Then run the full application stack:

```
docker compose -f src/main/docker/app.yml up -d
```

For a dev-specific deployment (using the `devnet` Docker network):

```
npm run docker:dev:up
```

For more information refer to [Using Docker and Docker-Compose][], this page also contains information on the docker-compose sub-generator (`jhipster docker-compose`), which is able to generate docker configurations for one or several JHipster applications.

### Monitoring and Tracing (optional)

A Prometheus + Grafana monitoring stack and Zipkin distributed tracing are available:

```
docker compose -f src/main/docker/monitoring.yml up -d
docker compose -f src/main/docker/zipkin.yml up -d
```

## Continuous Integration (optional)

**No CI is wired up for this repository** — `.github/workflows/` is empty. The `ci:*` scripts in `package.json` (`ci:backend:test`, `ci:e2e:*`) exist as entry points for whichever system is adopted.

To generate configuration, run the ci-cd sub-generator (`jhipster ci-cd`), which produces files for a number of Continuous Integration systems. Consult the [Setting up Continuous Integration][] page for more information.

## Repository notes

- `bin/` is a gitignored stale copy of the project (it has its own `pom.xml`, `README.md`, and `src/`). Ignore it; edits there have no effect on the build.
- `patient-ms.log` is output from the workspace-level `start-patient.sh` helper, not a tracked artifact.
- Agent/assistant guidance lives in `CLAUDE.md`, `AGENTS.md`, and `.github/instructions/*.instructions.md`.

[JHipster Homepage and latest documentation]: https://www.jhipster.tech
[JHipster 8.1.0 archive]: https://www.jhipster.tech/documentation-archive/v8.1.0
[Doing microservices with JHipster]: https://www.jhipster.tech/documentation-archive/v8.1.0/microservices-architecture/
[Using JHipster in development]: https://www.jhipster.tech/documentation-archive/v8.1.0/development/
[Service Discovery and Configuration with Consul]: https://www.jhipster.tech/documentation-archive/v8.1.0/microservices-architecture/#consul
[Using Docker and Docker-Compose]: https://www.jhipster.tech/documentation-archive/v8.1.0/docker-compose
[Using JHipster in production]: https://www.jhipster.tech/documentation-archive/v8.1.0/production/
[Running tests page]: https://www.jhipster.tech/documentation-archive/v8.1.0/running-tests/
[Code quality page]: https://www.jhipster.tech/documentation-archive/v8.1.0/code-quality/
[Setting up Continuous Integration]: https://www.jhipster.tech/documentation-archive/v8.1.0/setting-up-ci/
[Node.js]: https://nodejs.org/
[NPM]: https://www.npmjs.com/
