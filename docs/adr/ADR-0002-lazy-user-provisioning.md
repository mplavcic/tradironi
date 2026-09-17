# ADR 0002: Lazy User Provisioning via UserSyncFilter

- **Author:** Mateo Plavcic
- **Date:** 2026-09-16
- **Status:** Accepted
- **Tags:** backend, security

## Status

Accepted

## Context

With Keycloak as the identity provider, the Keycloak user directory and the local `tradironi_user` table are separate stores. Local users need to exist in `tradironi_user` for application-level features that reference them. Keycloak users are created asynchronously (admin console, registration, federation), so there is no natural "user registered" event inside the application that could provision the local row eagerly.

## Decision

Provision users **lazily on first authenticated request**. `UserSyncFilter` (`shared.security.internal`, a `OncePerRequestFilter` placed after `BearerTokenAuthenticationFilter`) reads the JWT `sub` claim and calls `UserSyncService.syncUser(UUID keycloakId)`, which creates an `ACTIVE` `tradironi_user` row if none exists (`findByKeycloakId`).

Provisioning failures are logged as warnings and **swallowed** — the filter always continues the chain and never blocks or fails the request. Likewise, a malformed (non-UUID) `sub` is caught and logged.

## Rationale

- **Pro:** No coupling to Keycloak registration events, webhooks, or event listeners.
- **Pro:** Simple and reliable: the first real request for a user guarantees a local row.
- **Pro:** Idempotent (`syncUser` checks existence first) and safe under normal usage.
- **Con:** A user only appears in `tradironi_user` after their first authenticated request — admins/reports cannot see "registered but never active" users.
- **Con:** Swallowing provisioning errors means a failed insert is silently retried on the next request instead of surfacing immediately.

## Alternatives Considered

- **Provision on Keycloak registration:** Rejected — requires Keycloak event listeners/federation that add deployment and reliability complexity.
- **Provision eagerly on login via the authorization code flow:** Rejected — the resource server only sees JWTs, not login events.

## Consequences

1. Local `tradironi_user` rows are created on demand, keyed by the JWT `sub` as `keycloak_id`.
2. The `sub` claim is expected to be a valid UUID (Keycloak user IDs are UUIDs).
3. Requests must be routed through `UserSyncFilter` after authentication to guarantee provisioning.
4. `UserSyncService.syncUser` remains idempotent and transactional; new user-facing features can rely on the row existing after the first request.