# ADR 0008: Multi-Agent System Architecture

- **Author:** Mateo Plavcic
- **Date:** 2026-09-18
- **Status:** Pending
- **Tags:** backend, architecture

## Status

Pending

## Context

Tradironi will be extended with multiple dedicated analyst agents, each responsible for a single task: news analysis, fundamental analysis, sentiment analysis, and so on. Without an explicit structure, each agent risks being ad-hoc: bespoke package layouts, hand-rolled integration points, and duplicated cross-cutting concerns (retry, rate limiting, persistence). This decision defines, once, how an agent is shaped and how agents relate to each other, so that adding the next agent is a mechanical exercise rather than a new design.

Two constraints shape the v1 form: there is **no consumer** of any agent's output yet, and the only domain entity today is the `user` (the Instrument Catalog does not exist to key agent output off). Scheduling, persistence, and storage all assume — and so build in advance of — a consuming workflow that does not exist. The v1 decision therefore optimizes for **fastest proof of value and lowest blast radius**.

This ADR is written against the domain model in [`CONTEXT-MAP.md`](../../CONTEXT-MAP.md). Its decision was **revised** after that model was written: an earlier draft of this ADR made a Modulith module per agent. The shared vocabulary an agent needs — what an `Insight` is, what a `Stance` means, when one goes stale — is common to every analyzer, and that single fact is what the revised decision turns on. The revision is recorded in [Alternatives Considered](#alternatives-considered) rather than quietly overwritten.

## Decision

**A Modulith module maps to a bounded context, not to an agent.** An agent is an `AnalysisKind`: one way of asking an Instrument a question, implemented as a package inside the module that owns the vocabulary its answer is written in.

- **One module per bounded context.** The first is **`research`** — a leaf module holding every AnalysisKind that produces Insights, with the shared vocabulary (`Insight`, `Stance`, `Conviction`, `Citation`, `AnalysisKind`, `Staleness`) exposed as its public API at the module root. Agents do not get their own modules; they get their own packages under it, behind the module boundary.
- **When an agent earns a context.** An AnalysisKind stays inside its context until it needs language the other AnalysisKinds do not own — the test is a vocabulary test, not a size or effort test. Only then does it become a bounded context and get its own module. `sentiment` is the live case: article tone and an Instrument's direction are values inside Research, not a new context, and the rule is recorded so it does not have to be re-argued when sentiment work starts.
- **v1: stateless and on-demand.** An agent receives a request, produces its analysis on the spot, and returns it to the caller. **No schedule, no writes, no persistence, no Flyway migrations, no storage** — until a real consumer exists.
- **Communication:** agents are independent; there is **no direct inter-agent invocation** in v1. If cross-agent chaining is ever needed, it will be introduced via an event-driven mechanism, never direct calls.
- **Shared agent infrastructure** (external-client error/retry handling, rate limiting, LLM call accounting, common persistence types) goes into a new **shared Modulith module (e.g. `agent`)**, registered in `TradironiApplication` `@Modulith(sharedModules = ...)`, **created only when at least two agents exist**.
- **The shared module carries middleware only, never domain vocabulary.** A Modulith shared module is exempt from dependency rules by construction, so any domain type placed there becomes reachable by everything — which is precisely the enforcement ADR 0003 exists to provide. `Insight` and its siblings live in `research` where the compiler can see who uses them.

## Rationale

- **Pro (decisive):** Every agent answers in the same vocabulary. An `Insight` of a `fundamental` and an `Insight` of `news` are the same type, or they are not the same type — and if they are separate types, nothing downstream can consume them uniformly. Shared vocabulary is the condition for the domain to be coherent, and shared vocabulary inside one module is the only way to get it without either exempting it from the build's dependency rules or copying it.
- **Pro:** Reuses the enforced Modulith model — a cross-module import already breaks the build (`ArchitectureTests`), so context boundaries are machine-checked, not just documented. This now protects fewer boundaries, but the ones it protects are the ones that carry meaning.
- **Pro:** A shared `agent` module concentrates cross-agent middleware and is only created when there is a second agent to share it (YAGNI).
- **Con:** AnalysisKinds inside a context are separated by package convention, not by the compiler. A bad import within `research` will not break the build the way a cross-module import would.
- **Con:** That isolation is weaker than the earlier draft claimed. The previous rationale asserted each agent is "independently evolvable, testable, and deployable" and that a bad agent cannot corrupt another; **none of that held.** Nothing is independently deployable in a single-process Modulith application, and v1 agents are stateless functions with no shared mutable state, so there was no corruption pathway to prevent. The claim was decoration, and the vocabulary argument is not.
- **Con:** Ceremony is paid per context rather than per agent, which is less often — but the cheap way to extract an AnalysisKind into its own module later, should it earn one, is now unavailable by default.
- **Con:** No cross-agent orchestration in v1 — composite signals (e.g. a combined score) are deferred until an individual agent proves useful.

## Alternatives Considered

- **A module per agent, with the shared Insight vocabulary in a shared `agent` module:** Rejected — **this is what the earlier draft of this ADR decided.** A Modulith shared module is exempt from dependency rules, so `Insight`, `Stance`, and the rest would have been importable from any module by anyone, and the boundary ADR 0003 enforces would have quietly stopped existing for exactly the types that matter most. The shared-vocabulary requirement is what ruled it out.
- **A module per agent, with the vocabulary duplicated in each module:** Rejected — guaranteed divergence between copies, invisible until two agents disagreed in production on what "positive" meant.
- **Single monolith package with one `AnalyzerService` per agent:** Rejected — no enforcement, and violates the existing Modulith convention (ADR 0003).
- **Agents as separate Spring processes (services):** Rejected — premature distribution; deployment and ops cost outweigh benefits at this scale.
- **Agent event bus from day one (Spring Modulith events, Kafka):** Deferred — no current consumer of agent events exists; re-visited when agents need to chain.
- **Agents as children of the `shared` module:** Rejected — `shared` is constrained to depend only on `user`; giving it external-analysis concerns bloats the shared module and muddies its intent.
- **Stateless vs. stateful v1:** see Rationale — stateless chosen because nothing consumes agent output and the Instrument Catalog does not exist to key storage off.

## Deferred

These items are explicitly **not decided here** — they are the research agenda for the agent track. Each is resolved by further research (see the `Research` section), a new consumer, or the Instrument Catalog, and will land as its own ADR.

- **Scheduling/cadence:** no v1 schedule; re-visited once any consumer exists or agents chain.
- **Output persistence & storage (Flyway migrations, tables):** blocked on the Instrument Catalog and a consumer.
- **LLM provider, model, and global per-user LLM quota/quota enforcement:** deferred to research; planned as short follow-up ADRs.
- **Cross-agent orchestration / combined signals:** deferred until an agent proves useful. Note this becomes materially easier once AnalysisKinds share a module and therefore a vocabulary.

## Consequences

1. The first agent module is **`research`**, not `news`. `news` is an AnalysisKind inside it. A second agent in the same domain adds a package, not a module, and does not pay `package-info.java` or architecture-test ceremony.
2. The shared `agent` module, once it exists, holds middleware only. Domain vocabulary never goes in it.
3. **Tradironi singletons (`tradironi_schema`, `tradironi_user`, `TradironiApplication`) are NOT touched by this decision** — a new agent module neither adds Flyway migrations nor registers schema.
4. Value proposition: an agent proves or discards itself with zero schema/infrastructure debt. Preserved — an AnalysisKind is still a self-contained package with its own seam.
5. The module's public API is the architecture seam: `ArchitectureTests` verifies the boundary. Within `research`, the per-kind seams (the feed ACL, the analyzer) are the unit-test seams for behavior.
6. This ADR **reverses** the earlier "one module per agent" decision. Nothing has been implemented against the old shape, so no migration is owed.

## Research

Open questions to resolve before any agent is implemented (results land in the ADRs below or new ADRs):

- **LLM provider & model** (context window, cost, rate limits, latency) — **pending research → ADR 0011**.
- **Per-user LLM quota & spend balance** (global per-user daily/weekly budget enforcement) — **pending research → ADR 0012**.
- **Instrument Catalog** (Instrument, Listing, schema, Flyway migration, propagation) — **pending research → ADR 0013**. Also unblocks the `InstrumentResolver` stand-in in ADR 0009.
- **Per-market feed curation & more feed types** (paid feeds, ticker-to-feed maps) — **pending research → ADR 0014**.

## Domain Model Alignment

| This ADR | Glossary |
|---|---|
| bounded context, module | [Context map](../../CONTEXT-MAP.md) |
| `AnalysisKind` | [Research](../../backend/src/main/java/com/example/tradironi/research/CONTEXT.md) |
| `Insight`, `Stance`, `Conviction`, `Citation`, `Staleness` | [Research](../../backend/src/main/java/com/example/tradironi/research/CONTEXT.md) |
| `Instrument` | [Instrument Catalog](../../backend/src/main/java/com/example/tradironi/instrument/CONTEXT.md) |
| `agent` (shared module) | Agent Runtime — explicitly **not** a bounded context |

## Proof of Edition

Clean, final form. No ceremony.
