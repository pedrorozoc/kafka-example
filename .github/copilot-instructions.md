# Bistro repository guidance

## Project overview

Bistro is a Java 25 / Spring Boot 4.2.0 REST application that runs as one executable JAR. It uses Maven Wrapper, PostgreSQL 17, Flyway, JPA, Kafka, Lombok, MapStruct, and Bean Validation.

The application is a modular monolith:
- `bistro.reservations` owns reservation entities, repositories, HTTP adapters, and reservation orchestration.
- `bistro.tables` owns the dining-table catalog and table assignment behavior.
- `bistro.notifications` handles notification side effects.
- `bistro.shared.config` contains shared infrastructure configuration.

The reservation flow is primarily event-driven: `POST /api/v1/reservations` creates a reservation and publishes `reservation-created`; `bistro.tables` assigns or rejects a table and publishes `table-assigned` or `table-unavailable`; listeners in `bistro.reservations` persist the final status and publish `reservation-confirmed` or `reservation-rejected`; `bistro.notifications` consumes the final events for email notifications.

## Build, run, and test

- Start local infrastructure: `docker compose up -d`
- Run the API locally: `export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/bistro`, `export SPRING_DATASOURCE_USERNAME=bistro`, `export SPRING_DATASOURCE_PASSWORD=local_bistro`, then `./mvnw spring-boot:run`
- Default API port: `8080`
- Build and run the complete verification suite: `./mvnw verify`
- Run one test class: `./mvnw -Dtest=CreateReservationHttpTest test`
- Run one test method: `./mvnw -Dtest=CreateReservationHttpTest#resolvesAndStoresBothResultsWithDifferentCodes test`
- Run the migration test directly: `./mvnw -Dtest=ReservationMigrationTest test`
- No separate lint tool or lint Maven goal is configured.

The full suite and integration tests require Docker for Testcontainers when PostgreSQL is not supplied through `BISTRO_TEST_DATABASE_URL`. If using an external test database, also set `BISTRO_TEST_DATABASE_USERNAME` and `BISTRO_TEST_DATABASE_PASSWORD`; the user must be allowed to create schemas.

Docker Compose exposes PostgreSQL on `127.0.0.1:55432`, pgAdmin on `8082`, Kafka on `9092`, Kafka UI on `8081`, and Mailpit on `1025`/`8025`.

## HTTP and persistence conventions

- Public endpoints belong under `/api/v1/`; use DTO records in `controller` rather than exposing JPA entities.
- Map entities and DTOs with MapStruct. MapStruct implementations are generated under `target/generated-sources/annotations`.
- Validate request fields with Bean Validation in request DTOs. The controller activates validation with `@Valid`.
- Handle API failures through `bistro.ApiExceptionHandler` using `ProblemDetail`; client-facing titles and details are Spanish.
- Reservation arrival is a local `LocalDateTime` with exact minute precision (`YYYY-MM-DDTHH:mm`). JSON deserialization is intentionally strict: no seconds, offsets, trailing JSON, or string/number type mismatches are accepted.
- `partySize` is an `Integer` constrained to 1 through 12. The custom deserializer accepts JSON numbers whose value is an integer, including forms such as `12.0` and `1.2e1`.
- Flyway migrations live in `src/main/resources/db/migration`. Add a new migration for schema changes; do not edit V1-V5 in place. V4 converts historical times and V5 adds the required `table_number`.
- Set `BISTRO_LEGACY_RESERVATION_ZONE` before applying V4 to an existing database. It defaults to `America/Bogota`.
- JPA uses `timestamp without time zone` for reservation arrival and stores confirmed-table occupation at table ID plus local minute.

## Code organization and design conventions

Keep exactly these package responsibilities inside each module:
- `controller`: HTTP adapters, request/response records, and MapStruct mappers.
- `service`: business logic and orchestration.
- `repository`: Spring Data repository interfaces.
- `model`: JPA entities and domain enums.

Do not introduce `domain`, `application`, `api`, or `infrastructure` packages, or alternate hexagonal-architecture layers.

Identifiers, class names, and method names are in English. Comments, log messages, exception messages, validation messages, and other client-facing text are in Spanish.

When a dependency crosses module boundaries and the implementation mechanism is volatile or replaceable, expose an interface from the provider's `service` package and keep its concrete implementation package-private. Stable, non-isolating dependencies may be used directly. This repository currently uses `bistro.tables.service.TableCatalog` for the `reservations` -> `tables` boundary.

Table selection and locking behavior is transaction-sensitive. Candidate tables are ordered and queried as a catalog; row locking is performed with Spring Data/JPA pessimistic locking. Preserve `READ COMMITTED` behavior and repeated occupation checks after locks are acquired.

Kafka topics and serializers are configured in `bistro.shared.config.KafkaTopicConfig` and `application.yaml`. Add a topic bean when introducing a new event type, and use the existing event record types for published payloads.

## Repository-specific feature documentation

For feature work, read the relevant files under `specs/001-reservation-lifecycle/`, especially `spec.md`, `plan.md`, `data-model.md`, `contracts/openapi.yaml`, and `quickstart.md`.

The project constitution at `.specify/memory/constitution.md` is governing repository guidance. It requires commit subjects in `classe-NN-slug` format, with `NN` as a two-digit class number, and requires README updates whenever local startup procedures change.
