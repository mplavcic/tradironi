# ADR 0003: Spring Modulith Module Architecture

- **Author:** Mateo Plavcic
- **Date:** 2026-09-16
- **Status:** Accepted
- **Tags:** backend, architecture

## Status

Accepted

## Context

As the domain grows, unconstrained package dependencies lead to tight coupling and fragile changes. The project needed explicit, enforced module boundaries rather than relying on developer discipline alone.

## Decision

Use **Spring Modulith** to structure the application into modules, with the dependency direction enforced both by `@ApplicationModule` declarations and by automated architecture tests.

Current modules:

- `user` — leaf module, zero inter-module dependencies. Public API: `UserSyncService`, `UserStatusEnum`. Implementation in `.internal` (`User` JPA entity, `UserRepository`).
- `shared` — registered as a `@Modulith` shared module (`@Modulith(sharedModules = {"shared"})`), declared with `allowedDependencies = "user"`. Public API: `shared.security.SecurityConfig`. Implementation in `.internal` (`KeycloakJwtRoleConverter`, `UserSyncFilter`).

Dependency direction: `user  <--  shared` (shared depends on user; user depends on nothing).

Convention: public API lives at the module package root; implementation details live in `.internal` subpackages and are invisible to other modules.

`ArchitectureTests` guards this with five tests: the full Modulith `verify()`, exactly two modules present, `shared` registered as shared, `user` has no dependencies, and `shared` depends only on `user`.

## Rationale

- **Pro:** Boundaries are explicit, machine-checked, and fail the build (`./mvnw test`) on violation.
- **Pro:** Clear public API surfaces make modules navigable and testable.
- **Pro:** `shared` being a shared module keeps cross-cutting concerns (security) pluggable without a web of transitive dependencies.
- **Con:** Added Modulith dependency and test infrastructure; each new module needs a `package-info.java` with `@ApplicationModule`.
- **Con:** Over-decomposition risk — modules must be added deliberately, not per feature.

## Alternatives Considered

- **Single package / package-by-layer (controller, service, repository):** Rejected — no enforceable boundary; layers can drift.
- **OSGi / Java modules (JPMS):** Rejected — heavyweight, poor Spring ecosystem fit for this project size.
- **Hexagonal architecture per module from day one:** Rejected — YAGNI; Modulith gives enforcement with less ceremony.

## Consequences

1. New modules require a `package-info.java` with `@ApplicationModule`; shared modules additionally need registration in `TradironiApplication` `@Modulith(sharedModules = ...)`.
2. A cross-module import breaks the build (`ArchitectureTests.moduleStructureIsValid`).
3. All public API must be exposed at the module package root; everything else goes under `.internal`.
4. The `shared` module is the only place granted `allowedDependencies = "user"`; other modules must not depend on it.