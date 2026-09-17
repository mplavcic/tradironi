# ADR 0007: Docker Compose-Based Deployment

- **Author:** Mateo Plavcic
- **Date:** 2026-09-16
- **Status:** Accepted
- **Tags:** deployment, backend, database

## Status

Accepted

## Context

Tradironi has four runtime pieces that must start together and in the right order: PostgreSQL, the Spring Boot backend, and Keycloak (itself backed by the same Postgres). The setup must be reproducible for new developers and run with a single command, with configuration separated from secrets.

## Decision

Orchestrate everything with **Docker Compose** (`compose.yaml`):

- **`db`** — `postgres:18`, healthchecked via `pg_isready`, tuned via a mounted `tools/postgres/postgresql.conf`, and seeded with `tools/postgres/init-keycloak-db.sql` (creates the Keycloak role + database). Named volumes for data and logs.
- **`backend`** — built from a multi-stage `backend/Dockerfile` (Maven 21 build stage → `eclipse-temurin:21-jre-alpine` runtime, runs as non-root `appuser`). Exposes 8080 (app) and 8081 (actuator), healthchecked against `/actuator/health`, `SPRING_PROFILES_ACTIVE=prod` forced.
- **`keycloak`** — `quay.io/keycloak/keycloak:26.0`, `start-dev` mode, `/auth` relative path, connects to the shared Postgres `keycloak` database, healthchecked via `/health/ready`.

Dependency ordering is driven by healthchecks (`depends_on: condition: service_healthy`). Secrets come from `.env` (`sample.env` is the committed template); the compose file binds host `DB_PORT`/`8180`/`8080`/`8081` and reads all values from environment variables.

## Rationale

- **Pro:** Single `docker compose up --build` brings up the full stack.
- **Pro:** Health-check-based ordering removes guesswork about startup timing.
- **Pro:** Non-root container user and separate actuator port follow least-privilege practices.
- **Pro:** `.env`/`sample.env` keeps secrets out of the repo.
- **Con:** The `prod` profile forced by compose means all real config must live in base `application.yaml` — there is no separate prod config file to rely on.
- **Con:** There is **no realm import file** — the `tradironi` realm is currently created manually through the Keycloak admin console, which is a manual, undocumented step.

## Alternatives Considered

- **docker-compose.yml with a separate prod profile file:** Rejected — kept as `compose.yaml` per modern Compose conventions; config stays in base YAML/test profile (see AGENTS.md).
- **Manual deployment (installed Postgres/Keycloak + jar):** Rejected — not reproducible; Compose gives a known-good topology.
- **Add a Keycloak realm export file (`--import-realm`):** Considered — would automate realm/roles creation; deferred because realm contents are still evolving, but recommended as a follow-up.

## Consequences

1. Full stack runs via `cp sample.env .env && docker compose up --build`.
2. `SPRING_PROFILES_ACTIVE=prod` is always set by compose — new configuration must be added to base `application.yaml` (or the `test` profile), not a separate prod file.
3. `.env` is gitignored; `sample.env` is the authoritative template and must stay complete.
4. Backend depends on `db` being healthy; Keycloak shares the same Postgres via the init-created database.
5. The `tradironi` realm and its roles must still be created manually in Keycloak (or a future realm-import file added).