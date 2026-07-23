# Project Overview

## Code Quality and Style

- Follow SOLID principles and clean code practices.
- Use consistent naming conventions and code formatting.
- Implement comprehensive unit and integration tests using JUnit 5 and Mockito.
- Ensure proper documentation of code and APIs using JavaDoc and Swagger/OpenAPI.
- No null pointer deferences; use Optional where applicable.
- Handle exceptions gracefully and provide meaningful error messages.
- Use Lombok for boilerplate code reduction (getters, setters, constructors).
- Adhere to RESTful API design principles for all endpoints.
- Implement logging using SLF4J and Logback for all critical operations and exceptions.
- Follow resource leak prevention best practices, especially in file handling and database connections.

## Architecture and Design

- Use a layered architecture (Controller, Service, Repository) for separation of concerns.
- Implement domain-driven design principles for modeling patient data and related entities (Profile, Address, Condition, Medication, MedCase, Stat, Team, Task, Membership, Report, Metadata).
- Dependency injection should be used for all services and repositories to promote testability and maintainability.
- Use Kafka for asynchronous communication between services, especially for telemetry data and alerts.
- No static initialization blocks; use dependency injection for all configurations and services.
- Implement a robust error handling mechanism using `@ControllerAdvice` to return RFC 7807 compliant error responses for all exceptions.
- Immutable objects for data transfer objects (DTOs) and domain models where appropriate to ensure thread safety and maintainability.

## Security Considerations

- Implement authentication and authorization using Spring Security, with role-based access control per the authorities defined in `AuthoritiesConstants` (`ROLE_ADMIN`, `ROLE_USER`, `ROLE_ANONYMOUS`).
- Ensure all sensitive data (e.g., personal information, documents) is encrypted at rest and in transit.
- This service does not store user credentials itself (`skipUserManagement: true` in `.yo-rc.json`, no `User` domain/repository) — it only validates JWTs issued by another service, so credential hashing (bcrypt) is out of scope here; keep secrets/keys (e.g. `jwtSecretKey` in `.yo-rc.json`) out of source control and logs.
- Implement input validation and sanitization to prevent common vulnerabilities such as injection and cross-site scripting (XSS).
- Ensure proper CORS configuration for API consumers (there is no Angular/frontend client in this repo — CORS is disabled by default in `application.yml`, see the `cors` section).
- Regularly update dependencies to mitigate known security vulnerabilities.
- Implement rate limiting and monitoring to prevent abuse of the APIs and ensure system stability under load.
- Use HTTPS/TLS for all communications with this service (see `application-tls.yml`) to ensure data confidentiality and integrity.
- Ensure logs do not contain sensitive data or PII information and are properly secured to prevent unauthorized access.
- Implement comprehensive testing for security vulnerabilities, including penetration testing and vulnerability scanning as part of the development lifecycle.
- Ensure compliance with relevant data protection regulations (e.g., GDPR, HIPAA) in the handling of personal and health-related data.
- Use secure coding practices and conduct regular code reviews to identify and mitigate potential security issues early in the development process.

## Performance Optimization

- Use pagination and filtering for API endpoints that return large datasets to improve response times and reduce memory usage.
- Implement caching strategies (e.g., using Spring Cache) for frequently accessed data to reduce database load and improve response times.
- Optimize database queries using indexing and proper query design to ensure efficient data retrieval and manipulation.
- Use asynchronous processing for long-running tasks to improve responsiveness and user experience.
- Monitor application performance using tools like Spring Boot Actuator and implement necessary optimizations based on observed metrics and bottlenecks.
- Implement connection pooling for database connections to improve performance and resource management.
- Use efficient data structures and algorithms in service-layer logic (e.g. `ProfileService`, `MedCaseService`) to ensure optimal performance under load.
- Regularly profile the application to identify and address performance bottlenecks, especially in critical paths such as the Kafka consumer (`broker/KafkaConsumer`).
- Ensure that the application can scale horizontally by designing stateless services and using appropriate load balancing strategies to handle increased traffic and workload effectively.

## Technology Stack

- Java 25 LTS (Maven Enforcer permits JDK 17–26)
- Spring Boot 4 (JHipster BOM 9.0.0), Spring MVC (not reactive/WebFlux)
- Spring Web, Spring Data MongoDB, Spring Security (JWT), Spring Kafka / Spring Cloud Stream (Kafka binder)
- Spring Cloud Consul for service discovery and centralized config; Resilience4j for circuit breaking
- This service is backend-only (`skipClient: true` in `.yo-rc.json`) — no Angular/frontend code lives in this repo
- Docker (Jib-built images) and Docker Compose for containerization and local dependency services
- JUnit 5, Mockito, ArchUnit (layer boundaries), and Testcontainers (embedded MongoDB + Kafka) for testing
- SLF4J and Logback for logging
- Swagger/OpenAPI for API documentation
- Maven for build and dependency management; NPM only for dev tooling (Prettier, Husky, docker/npm-script shortcuts) — there is no frontend to package
- Git for version control; no GitHub Actions workflows are currently configured in `.github/workflows` (npm's `ci:*` scripts exist for a CI system to call, but none is wired up yet)
