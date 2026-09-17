# ADR 0004: Flyway Migration Management with Dedicated Schema

- **Author:** Mateo Plavcic
- **Date:** 2026-09-16
- **Status:** Accepted
- **Tags:** database, backend

## Status

Accepted

## Context

Database schema changes are the highest-risk area of the project. `ddl-auto` (Hibernate-managed DDL) is convenient but makes schema drift near-impossible to track and review across environments. A dedicated application schema (`tradironi_schema`) isolates application tables from the shared Keycloak schema (`public`) in the same Postgres instance.

## Decision

Manage the schema exclusively through **Flyway versioned migrations**, with the application data isolated under the `tradironi_schema` schema:

- `spring.flyway.default-schema: tradironi_schema`, `schemas: tradironi_schema` (`application.yaml`).
- `spring.jpa.properties.hibernate.default_schema: tradironi_schema`.
- `ddl-auto: none` in both `application.yaml` and `application-test.yaml` — Hibernate never issues DDL.
- Every migration is a new versioned file in `classpath:db/migration` and starts with `SET search_path TO tradironi_schema;`.
- The `flyway-database-postgresql` artifact is kept in `pom.xml` (required for Postgres 18 support).

Migrations so far: `V1__init_schema.sql` (original user schema), `V2__migrate_user_keycloak.sql` (dropped credential columns, added `keycloak_id UUID UNIQUE`).

## Rationale

- **Pro:** DDL is versioned, reviewable, and applied deterministically in every environment.
- **Pro:** Existing rows migrate in-place (V2 dropped unused credential columns while preserving data).
- **Pro:** The dedicated schema keeps app objects out of `public`, safe alongside Keycloak's own tables.
- **Pro:** Flyway runs the same migrations against the H2 test database, so tests exercise real DDL.
- **Con:** Changes require writing SQL — no `ddl-auto: update` convenience.
- **Con:** Applied migrations must never be edited; corrective changes need new versions.

## Alternatives Considered

- **Hibernate `ddl-auto: update`:** Rejected — opaque, environment-dependent, and unversioned schema drift.
- **Liquibase:** Rejected — Flyway is simpler for SQL-first setups and is already supported by Spring Boot out of the box.
- **Schema in `public`:** Rejected — would collide with Keycloak's tables in the same Postgres instance.

## Consequences

1. All schema changes go through new Flyway migrations under `db/migration`; each begins with `SET search_path TO tradironi_schema;`.
2. `V1` no longer reflects the current `tradironi_user` shape — the credential columns were removed by `V2` and must not be reintroduced.
3. Tests apply V1+V2 against in-memory H2 (`MODE=PostgreSQL`, `create-schemas: true`).
4. Keep `flyway-database-postgresql` in `pom.xml` for Postgres 18 compatibility.