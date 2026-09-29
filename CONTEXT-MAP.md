# Tradironi Context Map

Tradironi is an investing and trading application. It gathers evidence from external sources, turns that evidence into assessments about tradable instruments, and rolls those assessments up into durable user-facing views. This map records the bounded contexts, the language each one owns, and how they relate.

**Agents are not contexts.** An *agent* is a runtime role — a scheduled worker that runs an analyzer. A *bounded context* is a body of domain knowledge with its own vocabulary and invariants. The two often line up one-to-one today, but when they diverge the context wins and the agent becomes an implementation detail. See [Agents and contexts](#agents-and-contexts).

## Contexts

| Context | Status | Owns |
| --- | --- | --- |
| Identity & Access | Built (`user`, `shared`) | User, Role, keycloak link |
| [Instrument Catalog](./backend/src/main/java/com/example/tradironi/instrument/CONTEXT.md) | Modeled, not built | Instrument, Listing, Market, AssetClass, Issuer |
| [Research](./backend/src/main/java/com/example/tradironi/research/CONTEXT.md) | Modeled, not built | Insight, Evidence, Citation, AnalysisKind, AnalysisRun |
| Assessment | Not built | Thesis, Stance, Conviction, TimeHorizon, Catalyst |
| Portfolio | Not built | Investor, Watchlist, Position, Holding, Allocation |
| Execution | Out of scope | Order, Trade, Fill, Broker |
| Risk | Out of scope | Exposure, Limit, Concentration, KillSwitch |
| Agent Runtime | Not a domain | orchestration, scheduling, budgets, run bookkeeping |

A context marked *out of scope* is a real context that the language already needs, but which Tradironi does not build while it is advisory-only. Recording them prevents the analysis language from quietly growing a vocabulary for placing trades.

## Relationships

- **Research → Instrument Catalog:** *Conformist.* Research states its subject in the catalog's terms, using `InstrumentId`. The catalog knows nothing about research.
- **Assessment → Research:** *Conformist.* A Thesis is expressed in terms of Insights the Research context produced. Assessment adds no evidence of its own; it only combines.
- **Portfolio → Instrument Catalog:** *Conformist.* Holdings are held in Listings, identified by the catalog's `InstrumentId`.
- **Portfolio → Identity & Access:** *Conformist on user id.* An identity outlives all financial state, so the two do not merge. A User is an identity; an Investor is what that user does with a portfolio.
- **Assessment ⇹ Execution:** *Separate ways, deliberately.* Assessment must never depend on Execution. This is the structural form of the rule that analysis is advisory and never a trading signal.
- **Execution → Instrument Catalog:** *Conformist* on `Listing`, plus an *Anti-Corruption Layer* over each broker's API, since brokers disagree about order and fill vocabulary.
- **Research → external sources:** *Anti-Corruption Layer* over RSS, news APIs, and filings. The `NewsSource` seam in ADR 0009 is this ACL; it just was not named as one.
- **Agent Runtime → every context:** *Customer/Supplier.* It runs analyzers and enforces budgets, but owns no domain language of its own.

## Advisory boundary

Tradironi is advisory-only. The only context that moves money is Execution, and it is not built. Everything else produces views: an Insight assesses an Instrument, a Thesis combines Insights, a Portfolio records what the user already holds. No output of any built context may be presented as, or become, an order.

## Agents and contexts

ADR 0008 names one agent per analysis task. This table is the reconciliation, and it is the authority when a new agent is proposed.

| Agent | Bounded context | Analysis kind |
| --- | --- | --- |
| news | Research | `news` |
| fundamental | Research | `fundamental` |
| technical | Research | `technical` |
| sentiment | Research | `sentiment` |

Sentiment is the case that breaks the pattern, and is recorded here so it does not have to be re-litigated. Article tone is a `Tone` value on Research's output, and the directional view of an instrument is a `Stance` on an Insight. Neither is a context. A future sentiment agent is a new `AnalysisKind` inside Research, not a new module — it becomes a context only if it needs vocabulary the others do not have.

Adding an agent therefore does not require a new context. The question to ask first is: *does the new analysis need language the existing contexts do not already own?*
