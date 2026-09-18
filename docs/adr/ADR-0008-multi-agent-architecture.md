# ADR 0008: Multi-Agent System Architecture

- **Author:** Mateo Plavcic
- **Date:** 2026-09-18
- **Status:** Proposed
- **Tags:** backend, architecture

## Status

Proposed

## Context

Tradironi will be extended with multiple dedicated analyst agents, each responsible for a single task: news analysis, fundamental analysis, sentiment analysis, and so on. Without an explicit structure, each agent risks becoming ad-hoc and inconsistent with the codebase. This decision defines, once, how an agent is shaped as a Modulith module and how agents relate to each other — so that adding the next agent follows an established pattern.

## Decision

Model each agent as its own **Modulith leaf module**, named after its domain (`news`, `fundamental`, `sentiment`, ...):

- **Per-agent module layout** follows the existing convention: public API at the module package root, implementation in `.internal` (`*.internal.client`, `*.internal.analyzer`, ...). Each new agent module gets a `package-info.java` with `@ApplicationModule`.
- **Communication:** agents are independent of each other; there is **no direct inter-agent invocation** in v1. If cross-agent chaining is needed later (e.g. sentiment informed by news), it will be introduced via an event-driven mechanism, never direct calls.
- **Shared agent infrastructure** that spans agents (e.g. external-client error/retry handling) goes into a new shared Modulith module (e.g. `agent`), registered in `TradironiApplication` `@Modulith(sharedModules = ...)`, mirroring how `shared` is registered today.

## Rationale

- **Pro:** Reuses the enforced Modulith model — a cross-module import already breaks the build (`ArchitectureTests`), so agent boundaries are machine-checked, not just documented.
- **Pro:** Each agent is independently evolvable, testable, and deployable; a bad agent cannot corrupt another.
- **Pro:** A shared `agent` module concentrates cross-agent middleware instead of duplicating it in every agent.
- **Con:** Adds module ceremony per agent (package-info, tests, possible shared-module registration).
- **Con:** No cross-agent orchestration in v1 — composite signals (e.g. a combined score) are deferred until an individual agent proves useful.

## Alternatives Considered

- **Single monolith package with one `AnalyzerService` per agent:** Rejected — no enforcement, no isolation, and violates the existing Modulith convention (ADR 0003).
- **Agents as separate Spring processes/services:** Rejected — premature distribution, deployment and ops cost outweigh benefits at this scale.
- **Agent event bus from day one (e.g. Spring Modulith events, Kafka):** Deferred — no current consumer of agent events exists; re-visited when agents need to chain.
- **Agents as children of the `shared` module:** Rejected — `shared` is constrained to depend only on `user`; giving it external-analysis concerns bloats the shared module and muddies its intent.

## Consequences

1. Each new agent = a new leaf Modulith module with `package-info.java`, public API at root, implementation under `.internal`.
2. Shared cross-agent concerns accumulate in a new shared `agent` module registered via `@Modulith(sharedModules = ...)`.
3. `ArchitectureTests` must keep passing — the module graph is the enforcement mechanism.
4. **Not decided here (pending research):** agent triggering/scheduling, output persistence and storage shape (incl. Flyway migrations). These will be added to this ADR once researched.
5. Follow-up decisions (LLM provider, event bus for cross-agent chaining) will be captured in separate ADRs.