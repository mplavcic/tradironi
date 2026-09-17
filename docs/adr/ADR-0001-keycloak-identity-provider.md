# ADR 0001: Keycloak as the Single Identity Provider

- **Author:** Mateo Plavcic
- **Date:** 2026-09-16
- **Status:** Accepted
- **Tags:** backend, security, database

## Status

Accepted

## Context

Tradironi needs authentication and authorization. The initial schema (`V1__init_schema.sql`) modeled credentials and profile data directly in `tradironi_user` (`username`, `password`, `role`, `name`, `surname`, `email`). Self-managing passwords, password hashing, email verification, and role administration inside the application is security-sensitive, error-prone, and duplicates well-solved functionality.

## Decision

Delegate authentication and authorization entirely to **Keycloak** as an OIDC identity provider, and treat the backend as an OAuth2 **resource server** that only validates JWT access tokens.

The application never stores credentials. `tradironi_user` keeps only a `keycloak_id UUID UNIQUE` link to the Keycloak user (via the JWT `sub` claim), plus application-level fields. `V2__migrate_user_keycloak.sql` dropped the V1 credential/profile columns (`username`, `password`, `name`, `surname`, `email`, `role`).

The JWT issuer is configurable and defaults to `KC_ISSUER_URI=http://localhost:8180/realms/tradironi` (`application.yaml`).

## Rationale

- **Pro:** No credential handling, password hashing, or account-verification code to own — Keycloak provides it out of the box.
- **Pro:** Standard OIDC/JWT flow supported natively by Spring Security's OAuth2 resource server auto-configuration.
- **Pro:** Users, roles, and realm administration live in one place and can evolve independently of application code.
- **Con:** The backend cannot authenticate anyone if Keycloak is unreachable — Keycloak becomes critical infrastructure (deployed in `compose.yaml`).
- **Con:** Local development now requires a running Keycloak instance and a manually created realm.

## Alternatives Considered

- **Self-managed credentials in V1 schema (`username`+`password`):** Rejected — reinvents password hashing, verification, and admin tooling for no benefit.
- **Auth0 / other hosted IdPs:** Rejected — external dependency and vendor lock-in; Keycloak is self-hosted and already available for a local dev setup.

## Consequences

1. `tradironi_user` no longer contains login data; identity is a `keycloak_id` UUID unique reference.
2. v1 credential columns must not be reintroduced (documented in `AGENTS.md`).
3. Keycloak realm `tradironi` and its users/roles are required for any real authentication; issued JWTs must carry the expected `KC_ISSUER_URI`.
4. Existing Flyway migrations (V1+V2) already applied to any database must not be rewritten; future schema changes use new versioned migrations.