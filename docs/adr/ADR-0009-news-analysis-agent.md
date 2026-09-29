# ADR 0009: News Analysis Agent

- **Author:** Mateo Plavcic
- **Date:** 2026-09-18
- **Status:** Pending
- **Tags:** backend, architecture

## Status

Pending

## Context

News analysis is the first agent built on the multi-agent architecture (ADR 0008). It must collect financial news relevant to a requested Instrument and return a qualitative view of it: a synthesis, a direction, and how much weight that direction deserves. It has no consumer yet and the Instrument Catalog does not exist to key its input off — it is, per ADR 0008, a **stateless, on-demand, non-persisting** agent: fetch and analyze on the spot, return, persist and schedule nothing.

This ADR is written against the domain model in [`CONTEXT-MAP.md`](../../CONTEXT-MAP.md) and the Research glossary ([`research/CONTEXT.md`](../../backend/src/main/java/com/example/tradironi/research/CONTEXT.md)). Two consequences of that model shape the contract below, and both are corrections rather than additions:

- **A symbol is not an Instrument.** A symbol belongs to a Listing; it can be reused after delisting and is not identity. v1 therefore resolves a requested string to an Instrument at a single named seam, and every output is keyed on the Instrument, never on the string.
- **Tone is not Stance.** The previous contract used one `SentimentLabel` type for two different judgments — how an Article reads, and which way the Instrument points. Those are separate concepts with separate types, and conflating them is why "sentiment" was ambiguous across contexts.

## Decision

### Module

New **`research`** Modulith leaf module, per ADR 0008 (as revised). `news` is not a module — it is the **`news` AnalysisKind**, one package inside it. Layout:

- **Module root (public API):** the vocabulary every AnalysisKind shares and answers in — `Insight`, `Stance`, `Conviction`, `Citation`, `ArticleReading`, `AnalysisKind`, `Staleness` — plus this ADR's response types (`NewsAnalysisResult`, `InstrumentNews`) and the analyzer seam. These are public because a future `fundamental` AnalysisKind must speak in them without reaching into an implementation.
- **`.internal.news`:** the news AnalysisKind — relevance selection, per-Article reading, Insight synthesis.
- **`.internal.feeds`:** the feed ACL (`NewsSource` implementations).
- **`.internal.analyzer`:** LLM provider clients behind the `NewsAnalyzer` seam.
- **`package-info.java`** with `@ApplicationModule`, per ADR 0003.

A future `fundamental` or `technical` agent is a **sibling package under `research`**, not a new module and not a new context — it costs no `package-info.java` and no architecture-test ceremony. See the agent-to-context table in [`CONTEXT-MAP.md`](../../CONTEXT-MAP.md).

### Disclaimer: advisory only

News analysis is **Advisory, never a trading signal** — the standing rule defined in the Research glossary. Every response carries a label so consumers cannot mistake it for direction (`# news analysis is advisory — not a trading signal`). This is a first-class part of the contract, not a comment. Execution is a separate, currently unbuilt context that this module must never depend on, so the rule is structural and not merely stated.

## Contract

**`GET /news/analysis`** returns a **`NewsAnalysisResult`**:

```java
public record NewsAnalysisResult(
    AnalysisKind kind,                    // `news` in v1; makes the response self-describing
    Instant asOf,                         // As Of: the instant these Insights were formed
    Staleness staleness,                  // how long they stay believable before a re-run
    List<InstrumentNews> instruments      // one Insight per processed Instrument; may be empty
)

// The Insight: an assessment of one Instrument by the news AnalysisKind,
// resting on the Citations it names.
public record InstrumentNews(
    String instrument,                     // the Instrument, not the raw request string
    String summary,                       // 2–4 sentence synthesis across readings
    Stance stance,                        // bullish | bearish | neutral
    BigDecimal conviction,                // 0..1, how much weight the Stance deserves
    List<ArticleReading> readings         // the Citations it rests on; may be empty
)

// A Citation with an interpretation. Deliberately not an Insight: it carries
// no Stance and no Conviction, because it does not judge the Instrument.
public record ArticleReading(
    Citation citation,
    String summary,                       // what this Article says
    Tone tone,                            // how it reads — not a direction on the Instrument
    Relevance relevance                   // why it bears on the Instrument
)

public record Citation(
    String headline,
    String publisher,
    String url,
    Instant publishedAt                   // from feed item, may be null
)
```

Value types, all from the Research glossary:

```java
enum     Stance         { BULLISH, BEARISH, NEUTRAL }
enum     Tone           { POSITIVE, NEUTRAL, NEGATIVE }
enum     AnalysisKind   { NEWS, FUNDAMENTAL, TECHNICAL }
record   Staleness(Duration since)
record   Relevance(String bearing, String note)   // v1: note may be a single sentence
```

Note what is **not** in the contract: there is no `SentimentLabel`, because the single overloaded type it represented has been split into `Tone` (per Article) and `Stance` (per Instrument). `conviction` is new, and is what makes `stance = NEUTRAL` with high weight distinguishable from indifference.

Behaviour:

- **`?instrument=` is optional.** Present → `NewsAnalysisResult` with at most one `InstrumentNews` for that Instrument. Absent → the run processes the configured default set (`news.instruments.defaults`). The parameter takes a symbol-shaped string because there is no catalog to address yet; the response is always keyed on the resolved Instrument.
- **Instrument resolution is a single named seam**, `InstrumentResolver`. In v1 it resolves the uppercased input against the configured allowlist (`news.instruments.allowlist`); regex `^[A-Z]{1,5}$`, case-insensitive match, no database lookup. **This is a temporary stand-in for the Instrument Catalog, not a domain concept** — when the catalog lands, `InstrumentResolver` becomes a conformist adapter and nothing else in this ADR changes. Invalid input → `400 BadRequest`.
- **Empty result** (nothing in the staleness window bears on the Instrument): **HTTP 200**, `instruments = []`, and no Insight at all. An Insight with no Evidence is not an Insight, so the response does not manufacture a neutral one; `summary` describing the empty window moves to the run-level log, not the payload. Absence of news is not an error.

### Feed Sources

- **Curated general finance RSS/Atom feeds** (`news.sources.feeds` in `application.yaml`), seeded with general market/finance feeds — **curated at the feed-set level, not per Instrument**.
- A **`NewsSource` seam** (module `NewsSource` returning normalized `Article` items) so paid/further feeds can be added later without touching the pipeline. Per the context map this is the **Anti-Corruption Layer** over external publishers: feeds disagree about what an Article is, and nothing upstream of it leaks into Research's vocabulary.

### Analysis Pipeline (bounded: one relevance call, one call per Article, one synthesis)

Per request:

1. **Fetch:** poll configured feeds; normalize to `Article` items (headline, publisher, url, publishedAt, body excerpt) within the news **Staleness Window**; **dedup by (publisher, headline-hash)** with bounded overall timeout (default 5s per fetch). Feeds overlap and the same Article arrives through several, so dedup is load-bearing, not tidy-up.
2. **Relevance filter (LLM call 1):** one call asks the LLM to select, from the fetched window, which Articles bear on the Instrument. **Relevance is judged in the act of analysis, never by static keyword mapping** — it is a property of this run, not of the Article.
3. **Per relevant Article (LLM call 2):** one call each to produce `summary` + `tone` + `relevance`.
4. **Form the Insight:** synthesize the readings into one `InstrumentNews` — `summary` + `stance` + `conviction` — in a final LLM call, covered by the call budget below.

**Strict per-run budget (counters, config, fail-fast):**

| Config | Default | Meaning |
|---|---|---|
| `news.analysis.llm.maxArticlesPerRun` | `5` | Max Articles passed over the relevance threshold and read |
| `news.analysis.llm.maxCallsPerRun` | `12` | Max **total LLM calls** per run (relevance + per-Article + synthesis) |

The budget belongs to the **AnalysisKind**, not to this module: it is a fact about how much a `news` analysis may cost, and it is the same numbers a `fundamental` AnalysisKind will need in its own terms. The pipeline truncates the fetched set and caps calls against these counters; exceeding either is enforced by the pipeline (fail-fast), not by hope.

### LLM seam

- **Analyzer seam:** `NewsAnalyzer` interface with `selectRelevant(...)` (one call) and `read(Citation)` (one call) — backend-agnostic. An analyzer is one AnalysisKind's implementation; it is not a bounded context and owns no vocabulary of its own.
- **Provider/model choice is deferred** — see the Research agenda in ADR 0008 (planned as ADR 0011). Local dev can run a mock analyzer under the `dev`/`test` profile.

### Fail-fast & error contract

- **Distinct exceptions**, mapped to HTTP:
  - `FeedFetchException` (fetch/parse/timeout) → `502 Bad Gateway` (`FeedFetchFailed`).
  - `AnalysisFailureException` (LLM call failure/timeout) → `502 Bad Gateway` (`AnalysisFailed`).
- **Bounded timeouts** (config `news.analysis.timeout.fetch`, `.llm`, default ~5s each).
- **No global per-user quota in v1** — deferred to research.

## Rationale

- **Pro:** Stateless on-demand requires no DB, no schedule, no Flyway migrations, no storage — kills the biggest v1 risks identified in ADR 0008.
- **Pro:** General curated feeds + LLM relevance filter proves the agent with zero per-Instrument curation. Coverage is proven with ~2–3 feeds; no Instrument → feed map to maintain.
- **Pro:** The pipeline (cheap relevance pass, bounded per-Article reads, one synthesis) concentrates cost on relevant Articles; per-run counters bound total spend regardless of feed size.
- **Pro:** Returns a list (`NewsAnalysisResult.instruments`), so the same contract serves future searches; `?instrument=` is a narrowing convenience, not a separate design.
- **Pro:** Keying on the resolved Instrument, not the request string, means the stringly-typed identity of the previous draft is quarantined behind `InstrumentResolver` and cannot leak into other contexts.
- **Con:** Every request pays LLM calls; relevance is probabilistic (Advisory, never a trading signal).
- **Con:** Curation lives at feed-set level — some Instruments get thin coverage; per-market feed curation is deferred.
- **Con:** `InstrumentResolver` is a stand-in, not a catalog. Until the Instrument Catalog exists, callers can request Instruments Tradironi does not track, and a symbol-shaped string is still what the wire accepts.
- **Con:** Splitting `SentimentLabel` into `Tone` and `Stance`, and adding `conviction`, is a larger LLM output surface than a single label — one more dimension to prompt for, bound to the same budget.
- **Con:** The empty result now returns no Insight at all rather than a neutral one. A consumer that used the old fabricated neutral as a signal will see `instruments = []` instead; this is intended, and is the honest answer.

## Alternatives Considered

- **Paid news APIs (NewsAPI, Marketaux, ...):** Deferred — subscribe + key-management out of the box later, behind the same `NewsSource` ACL.
- **Per-Instrument static RSS maps (Instrument → [feeds] in config):** Rejected — curation cost scales with Instrument count; general feed + LLM relevance filter needs far less maintenance.
- **Rule/keyword-based analysis (no LLM):** Rejected — poor language nuance at similar implementation effort.
- **Persistence / scheduling from day one:** Rejected — no consumer runs off it; deferred to research (ADR 0008).
- **Returning a single `InstrumentNews` (not a list):** Rejected — a list-shaped contract costs the same to build and serves future search/composite consumers; single-response shape would force an API change when the first consumer asks for more than one Instrument.
- **Keep one `SentimentLabel` for both Article tone and Instrument direction:** Rejected — this is what the previous draft did. A per-Article positive/negative reading and an Instrument's bullish/bearish direction are different judgments that frequently disagree; one type for both makes "sentiment" mean two things at once, and is why the word collided with a planned sentiment agent in ADR 0008.
- **Make per-Article readings full Insights, each with its own Stance and Conviction:** Rejected — an Article rarely settles direction on its own, and asking the LLM to judge it per Article produces several weak Stances that the synthesis then has to un-do. Tone plus relevance, with one Stance at the Instrument, is both cheaper and more honest.
- **Wait for the Instrument Catalog before starting the news agent:** Rejected — the Catalog is a larger piece of work and would block the first proof of value behind infrastructure. `InstrumentResolver` keeps the coupling to one seam instead of spreading a string identity through the model.

## Consequences

1. New **`research`** Modulith leaf module with `package-info.java`, whose public API is the shared AnalysisKind vocabulary and this agent's response types, with the news AnalysisKind, feed ACL, and LLM clients under `.internal`. The next agent in the same domain adds a package to this module and nothing else.
2. Contract locked in the ADR (above) — downstream callers can rely on it without reading code. The wire contract **changed from the previous draft**: `?symbol=` → `?instrument=`, `SentimentLabel` → `Tone` + `Stance`, `AnalyzedArticle` → `Citation` + `ArticleReading`, and `overallSentiment` → `stance` + `conviction`. Nothing has been implemented against the old shape, so no migration is owed.
3. Budget counters are config (singleton-bounded), fail-fast; no global quota in v1.
4. All responses carry `asOf` and `staleness`, so a consumer can tell when an Insight was formed and when it stops being believable. This is what lets the same payload serve a cached read without silently serving a stale judgment.
5. **Deferred (pending research):** LLM provider/model, per-user quota & spend management, scheduling/cadence, output persistence & storage (Flyway migrations, tables, the Instrument Catalog).
6. This ADR intentionally leaves **storage, scheduling, and persistence unpriced** — they stay deferred until a consumer and the Instrument Catalog exist.
7. When the Instrument Catalog lands, `InstrumentResolver` becomes a conformist adapter and `Instrument` becomes an `InstrumentId` throughout. The Instrument Catalog migration is the follow-up ADR this one is waiting on.
8. `ArchitectureTests.expectedModulesArePresent` currently asserts exactly `["shared", "user"]` and **will fail** when `research` is added. That test needs updating as part of implementing this ADR, and its assertion becomes the check that no second agent module crept in.

## Domain Model Alignment

Terms in this ADR resolve to the glossaries below; anything not listed here is an implementation detail and carries no domain meaning.

| This ADR | Glossary |
|---|---|
| `AnalysisKind` (`news`) | Research |
| `Instrument`, `InstrumentResolver` | Instrument Catalog |
| `InstrumentNews` | Research — `Insight` |
| `Stance`, `Tone`, `Citation`, `Relevance`, `Advisory`, `Staleness` | Research |
| `Article`, `Publisher` | Research |
| `ArticleReading` | no domain term — a Citation with an interpretation, deliberately not an Insight |
| `InstrumentResolver` | Instrument Catalog — a temporary stand-in for `InstrumentId` resolution |
| `NewsSource` | Anti-Corruption Layer over external publishers (see `CONTEXT-MAP.md`) |

Terms deliberately **not** used: `SentimentLabel`, `analyzedAt`, `rationale`, `symbol`. `Analysis` is avoided for outputs because the Research glossary reserves the ambiguity between the output (`Insight`) and the act (`AnalysisRun`).

