# ADR 0008: Multi-Agent System Architecture

- **Author:** Mateo Plavcic
- **Date:** 2026-09-18
- **Status:** Pending
- **Tags:** backend, architecture

## Status

Pending

## Context

Tradironi will be extended with multiple dedicated analyst agents, each responsible for a single task: news analysis, fundamental analysis, sentiment analysis, and so on. Without an explicit structure, each agent risks being ad-hoc: bespoke package layouts, hand-rolled integration points, and duplicated cross-cutting concerns (retry, rate limiting, persistence). This decision defines, once, how an agent is shaped as a Modulith module and how agents relate to each other, so that adding the next agent is a mechanical exercise rather than a new design.

Two constraints shape the v1 form: there is **no consumer** of any agent's output yet, and the only domain entity today is the `user` (no instrument/symbol entity exists to key agent output off). Scheduling, persistence, and storage all assume — and so build in advance of — a consuming workflow that does not exist. The v1 decision therefore optimizes for **fastest proof of value and lowest blast radius**.

## Decision

Model each agent as its own **Modulith leaf module**, named after its domain (`news`, `fundamental`, `sentiment`, ...):

- **Per-agent module layout** follows the existing convention: public API at the module package root, implementation in `.internal`. Each new agent module gets a `package-info.java` with `@ApplicationModule`.
- **v1: stateless and on-demand.** An agent receives a request, produces its analysis on the spot, and returns it to the caller. **No schedule, no writes, no persistence, no Flyway migrations, no storage** — until a real consumer exists.
- **Communication:** agents are independent; there is **no direct inter-agent invocation** in v1. If cross-agent chaining is ever needed, it will be introduced via an event-driven mechanism, never direct calls.
- **Shared agent infrastructure** (external-client error/retry handling, rate limiting, common persistence types) goes into a new **shared Modulith module (e.g. `agent`)**, registered in `TradironiApplication` `@Modulith(sharedModules = ...)`, **created only when at least two agents exist**.

## Rationale

- **Pro:** Reuses the enforced Modulith model — a cross-module import already breaks the build (`ArchitectureTests`), so agent boundaries are machine-checked, not just documented.
- **Pro:** Each agent is independently evolvable, testable, and deployable; a bad agent cannot corrupt another.
- **Pro:** A shared `agent` module concentrates cross-agent middleware and is only created when there is a second agent to share it (YAGNI).
- **Con:** Adds module ceremony per agent (package-info, tests, possible shared-module registration).
- **Con:** No cross-agent orchestration in v1 — composite signals (e.g. a combined score) are deferred until an individual agent proves useful.

## Alternatives Considered

- **Single monolith package with one `AnalyzerService` per agent:** Rejected — no enforcement, no isolation, and violates the existing Modulith convention (ADR 0003).
- **Agents as separate Spring processes (services):** Rejected — premature distribution; deployment and ops cost outweigh benefits at this scale.
- **Agent event bus from day one (Spring Modulith events, Kafka):** Deferred — no current consumer of agent events exists; re-visited when agents need to chain.
- **Agents as children of the `shared` module:** Rejected — `shared` is constrained to depend only on `user`; giving it external-analysis concerns bloats the shared module and muddies its intent.
- **Stateless vs. stateful v1:** see Rationale — stateless chosen because nothing consumes agent output and no instrument entity exists to key storage off.

## Deferred

These items are explicitly **not decided here** — they are the research agenda for the agent track. Each is resolved by further research (see the `Research` section), new consumer, or instrument entity, and will land as its own ADR.

- **Scheduling/cadence:** no v1 schedule; re-visited once any consumer exists or agents chain (ADR 0008).
- **Output persistence & storage (Flyway migrations, tables):** blocked on an instrument/symbol entity and a consumer (ADR 0008).
- **LLM provider, model, and global per-user LLM quota/quota enforcement:** deferred to research; planned as short follow-up ADRs.
- **Cross-agent orchestration / combined signals:** deferred until an agent proves useful.

## Consequences

1. Each new agent = a new leaf Modulith module with `package-info.java`, public API at root, implementation under `.internal`.
2. Shared cross-agent concerns accumulate in a new shared `agent` module — registered via `@Modulith(sharedModules = ...)` — once at least two agents exist.
3. **Tradironi singletons (`tradironi_schema`, `tradironi_user`, `TradironiApplication`) are NOT touched by this decision** — a new agent module neither adds Flyway migrations nor registers schema.
4. Value proposition: an agent proves or discards itself with zero schema/infrastructure debt.
5. The `Agent` seam (`NewsSource`) is the unit-test seam for architecture enforcement — verifies module boundary, not behavior.

## Research

Open questions to resolve before any agent is implemented (results land in the ADRs below or new ADRs):

- **LLM provider & model** (context window, cost, rate limits, latency) — **pending research → ADR 0011**.
- **Per-user LLM quota & spend balance** (global per-user daily/weekly budget enforcement) — **pending research → ADR 0012**.
- **Instrument/symbol entity** (schema, Flyway migration, propagation) — **pending research → ADR 0013**.
- **Per-market feed curation & more feed types** (paid feeds, ticker-to-feed maps) — **pending research → ADR 0014**.

## Proof of Edition

Clean, final form. No ceremony.
