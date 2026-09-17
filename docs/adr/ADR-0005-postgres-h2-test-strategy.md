# ADR 0005: PostgreSQL for Production, H2 (PostgreSQL Mode) for Tests

- **Author:** Mateo Plavcic
- **Date:** 2026-09-16
- **Status:** Accepted
- **Tags:** database, backend

## Status

Accepted

## Context

Production runs on PostgreSQL 18. Running tests against a real Postgres instance (via Docker or Testcontainers) is heavyweight for unit and integration tests, and requires Docker + a running DB on every developer machine. The project needed a fast, zero-configuration test database that still validates the real Flyway migrations and Hibernate dialect.

## Decision

Use **H2 in-memory** for the `test` profile, configured for PostgreSQL compatibility so the Flyway migrations run unmodified:

- URL: `jdbc:h2:mem:tradironi_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE` (`application-test.yaml`).
- `ddl-auto: none`, so tests exercise the real migrations via Flyway.
- `spring.flyway.create-schemas: true` so Flyway can create `tradironi_schema` in H2.
- All tests activate the profile with `@ActiveProfiles("test")`.

Production keeps PostgreSQL 18 via `application.yaml` datasource (env-driven `${DB_HOST}/${DB_PORT}/${DB_NAME}`).

## Rationale

- **Pro:** `./mvnw test` runs with no Docker, no DB, and no external setup.
- **Pro:** Fast in-memory tests; `DB_CLOSE_DELAY=-1` keeps the database alive for the JVM lifetime.
- **Pro:** `MODE=PostgreSQL` + `DATABASE_TO_LOWER=TRUE` match Postgres behavior closely enough for the current schema and SQL.
- **Con:** H2 is not a true Postgres — native SQL or Postgres-specific features can fail in tests, masking production behavior.
- **Con:** Dialect differences make the test suite a weaker guarantee for SQL-heavy features.

## Alternatives Considered

- **Testcontainers with real Postgres:** Rejected for default test runs — requires Docker and slower startup; kept as the escalation path when H2 cannot parse complex native SQL.
- **Single H2 shared instance without Postgres mode:** Rejected — deviates too far from the target schema/dialect.

## Consequences

1. Tests run Flyway migrations (V1+V2) against H2, validating DDL without infrastructure.
2. New migrations must stay compatible with H2 PostgreSQL mode (or the test must be adapted).
3. If complex native SQL cannot run on H2, revisit **Testcontainers** with a real PostgreSQL instance for those integration tests (note kept in the ADR template).
4. `ddl-auto: none` stays in effect everywhere; schema changes always go through Flyway.