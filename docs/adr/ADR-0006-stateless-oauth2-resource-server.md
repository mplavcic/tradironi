# ADR 0006: Stateless OAuth2 Resource Server Security Model

- **Author:** Mateo Plavcic
- **Date:** 2026-09-16
- **Status:** Accepted
- **Tags:** backend, security

## Status

Accepted

## Context

The backend authenticates via Keycloak JWTs (ADR 0001). Spring Security needs a model that fits a JWT-only, horizontally scalable setup: no server-side sessions to store, no CSRF surface, and role-based authorization that derives from the token's claims.

## Decision

Configure the backend as a **stateless OAuth2 resource server** (`shared.security.SecurityConfig`):

- **JWT-only authentication** via OAuth2 resource server; issuer from `KC_ISSUER_URI` (default `http://localhost:8180/realms/tradironi`).
- **CSRF disabled** and **session policy `STATELESS`** — no server-side session state.
- **Role mapping:** `KeycloakJwtRoleConverter` reads `realm_access.roles` from the JWT and maps each to `SimpleGrantedAuthority("ROLE_" + role.toUpperCase())`.
- **Authorization rules:** `OPTIONS /**`, actuator `health`, and actuator `info` are public; all other actuator endpoints require `ROLE_ADMIN`; every other request requires a valid JWT (`anyRequest().authenticated()`).
- **Actuator isolation:** management server on separate port **8081**; only `health` and `info` are exposed, with `show-details: when-authorized`.
- `@EnableMethodSecurity` enables `@PreAuthorize`-style method security.
- Spring Boot 4 import note: `EndpointRequest` comes from `org.springframework.boot.security.autoconfigure.actuate.web.servlet`.

## Rationale

- **Pro:** Stateless tokens mean no session affinity, easy horizontal scaling, and no CSRF risk from cookie-based flows.
- **Pro:** Keycloak's `realm_access.roles` → `ROLE_*` is the standard mapping; roles are administered in Keycloak, not in code.
- **Pro:** Actuator on a separate, admin-gated port keeps operational endpoints off the public API surface.
- **Con:** JWTs cannot be instantly revoked — revocation happens at Keycloak (or with short token lifetimes).
- **Con:** Roles are whatever Keycloak puts in the token; local role changes need a new token/accepted issuer config.

## Alternatives Considered

- **Session-based authentication with local DB users:** Rejected — contradicts ADR 0001 (Keycloak as IdP) and adds session infrastructure.
- **Signed cookies / opaque tokens:** Rejected — more state to manage and requires token introspection per request.
- **Expose all actuator endpoints on the app port:** Rejected — widens the attack surface; separate port + minimal exposure is cleaner.

## Consequences

1. All endpoints except public actuator `health`/`info` (and CORS preflight) require a valid Keycloak JWT.
2. `KeycloakJwtRoleConverter` is the single place mapping roles to authorities; realm roles must be created in Keycloak.
3. Actuator runs on 8081; anything beyond `health`/`info` needs `ROLE_ADMIN`.
4. Method security is enabled — new endpoints can use `@PreAuthorize` against the mapped authorities.
5. The `EndpointRequest` import must match the Spring Boot 4 package (`...actuate.web.servlet`).