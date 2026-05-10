# Health Connect Patient Service

This is the **Health Connect Patient Data Micro Service** — a backend-only microservice (no frontend) that manages patient data for the Health Connect platform. It was generated using JHipster 8.1.0 and runs on **Spring Boot 4.0.3** with **JHipster BOM 9.0.0** and **Java 26**.

For JHipster documentation and help, visit [https://www.jhipster.tech/documentation-archive/v8.1.0](https://www.jhipster.tech/documentation-archive/v8.1.0).

This is a "microservice" application intended to be part of a microservice architecture, please refer to the [Doing microservices with JHipster][] page of the documentation for more information.
This application is configured for Service Discovery and Configuration with Consul. On launch, it will refuse to start if it is not able to connect to Consul at [http://localhost:8500](http://localhost:8500). For more information, read our documentation on [Service Discovery and Configuration with Consul][].

## Technology Stack

| Component         | Technology                              |
| ----------------- | --------------------------------------- |
| Language          | Java 26                                 |
| Framework         | Spring Boot 4.0.3 / JHipster BOM 9.0.0  |
| Database          | MongoDB                                 |
| Message Broker    | Apache Kafka (Confluent Platform 7.5.2) |
| Service Discovery | Consul                                  |
| Authentication    | JWT                                     |
| Build Tool        | Maven (via `./mvnw`)                    |
| Server Port       | 8081 (default)                          |

## Domain Entities

The service manages the following patient-related domain entities:

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
- **HCCredential** – health credentials
- **HCPayOption** – payment options
- **HCDocument** – associated documents

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

To configure CI for your project, run the ci-cd sub-generator (`jhipster ci-cd`), this will let you generate configuration files for a number of Continuous Integration systems. Consult the [Setting up Continuous Integration][] page for more information.

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
