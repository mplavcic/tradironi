# ADR 0009: News Analysis Agent

- **Author:** Mateo Plavcic
- **Date:** 2026-09-18
- **Status:** Pending
- **Tags:** backend, architecture

## Status

Pending

## Context

News analysis is the first agent built on the multi-agent architecture (ADR 0008). It must collect financial news relevant to a requested symbol and return a qualitative analysis: a summary and a directional news sentiment label (positive / neutral / negative). It has no consumer yet and no instrument/symbol entity exists to key its input off — it is, per ADR 0008, a **stateless, on-demand, non-persisting** agent: fetch and analyze on the spot, return, persist and schedule nothing.

## Decision

### Module

New **`news`** Modulith leaf module, per ADR 0008. Public API at `com.example.tradironi.news` root; implementation under `.internal` (`.internal.feeds`, `.internal.analyzer`, `.internal.domain`); `package-info.java` with `@ApplicationModule`.

### Disclaimer: advisory only

News analysis is **advisory, never a trading signal**. Every response carries a label so consumers cannot mistake it for direction (`# news analysis is advisory — not a trading signal`). This is a first-class part of the contract, not a comment.

## Contract

**`GET /news/analysis`** returns a **`NewsAnalysisList`**:

```java
public record NewsAnalysisList(
    Instant analyzedAt,                  // server-side time of this run
    List<NewsAnalysis> analyses          // one NewsAnalysis per processed (symbol) run
)

public record NewsAnalysis(
    String symbol,                       // as requested, uppercased
    List<AnalyzedArticle> analyses,      // articles relevant to symbol
    String overallSummary,               // 2–4 sentence synthesis across analyses
    SentimentLabel overallSentiment      // positive | neutral | negative
)

public record AnalyzedArticle(
    String headline,
    String source,
    String url,
    Instant publishedAt,                 // from feed item, may be null
    String summary,                      // LLM-generated
    SentimentLabel label,                // per-article
    String rationale                     // short LLM justification
)
```

Behaviour:

- **`?symbol=` is optional.** Present → `NewsAnalysisList` with at most one `NewsAnalysis` for that symbol. Absent → the run processes per the configured default symbol set (allowlist, see `news.symbols.defaults`).
- **Symbol input** validated by regex `^[A-Z]{1,5}$` against the allowlist configured in `application.yaml` (`news.symbols.allowlist`). **No database lookup — there is no instrument entity.** Invalid symbol → `400 BadRequest`. Input is uppercased before matching; allowlist matches case-insensitively.
- **Empty result** (nothing in the fetched window is relevant to `symbol`): **HTTP 200**, `overallSentiment = neutral`, `overallSummary = "No relevant news for {SYMBOL} in the fetched window."`, `analyses = []`. Absence of news is not an error.

### Feed Sources

- **Curated general finance RSS/Atom feeds** (`news.sources.feeds` in `application.yaml`), seeded with general market/finance feeds — **curated at the feed-set level, not per symbol**.
- A **`NewsSource` seam** (module `NewsSource` returning normalized `NewsArticle` items) so paid/further feeds can be added later without touching the pipeline.

### Analysis Pipeline (bounded, two LLM calls per run per article)

Per request:

1. **Fetch:** poll configured feeds; normalize to `NewsArticle` items (headline, source, url, publishedAt, body excerpt); **dedup by (source, headline-hash)** with bounded overall timeout (default 5s per fetch).
2. **Relevance filter (LLM call 1):** one call asks the LLM to select, from the fetched window, which articles are relevant to `symbol`. **Relevance is decided by the LLM, not by static symbol-keyword mapping.**
3. **Per relevant article (LLM call 2):** one call each to produce `summary` + `label` + `rationale`.

**Strict per-run budget (counters, config, fail-fast):**

| Config | Default | Meaning |
|---|---|---|
| `news.analysis.llm.maxArticlesPerRun` | `5` | Max articles passed over the relevance threshold and analyzed |
| `news.analysis.llm.maxCallsPerRun` | `10` | Max **total LLM calls** per run (relevance call + per-article calls) |

The pipeline truncates the fetched set and caps per-article calls against these counters; exceeding either is enforced by the pipeline (fail-fast), not by hope.

### LLM seam

- **Analyzer seam:** `NewsAnalyzer` interface with relevance filter method (one call) and `summarizeAndLabel(...)` (one call) — backend-agnostic.
- **Provider/model choice is deferred** (see `Deferred`). Local dev can run a mock analyzer under the `dev`/`test` profile.

### Fail-fast & error contract

- **Distinct exceptions**, mapped to HTTP:
  - `FeedFetchException` (fetch/parse/timeout) → `502 Bad Gateway` (`FeedFetchFailed`).
  - `AnalysisFailureException` (LLM call failure/timeout) → `502 Bad Gateway` (`AnalysisFailed`).
- **Bounded timeouts** (config `news.analysis.timeout.fetch`, `.llm`, default ~5s each).
- **No global per-user quota in v1** — deferred to research.

## Rationale

- **Pro:** Stateless on-demand requires no DB, no schedule, no Flyway migrations, no storage — kills the biggest v1 risks identified in ADR 0008.
- **Pro:** General curated feeds + LLM relevance filter proves the agent with zero per-symbol curation. Coverage is proven with ~2–3 feeds; no symbol → feed map to maintain.
- **Pro:** The two-call pipeline (cheap relevance pass, bounded per-article analysis) concentrates cost on relevant articles; per-run counters bound total spend regardless of feed size.
- **Pro:** Returns a list (`NewsAnalysisList`), so the same contract serves future searches; `?symbol=` is a narrowing convenience, not a separate design.
- **Con:** Every request pays LLM calls; relevance is probabilistic (advisory, never a trading signal).
- **Con:** Curation lives at feed-set level — some instruments get thin coverage; per-market feed curation and instruments summary is deferred.
- **Con:** `symbol` is not validated against a real instruments/allowlist-domain — no instrument entity exists; callers can request symbols Tradironi doesn't track.

## Alternatives Considered

- **Paid news APIs (NewsAPI, Marketaux, ...):** Deferred — subscribe + key-management out of the box later, behind the same `NewsSource` seam.
- **Per-symbol static RSS maps (symbol → [feeds] in config):** Rejected — curation cost scales with symbol count; general feed + LLM relevance filter needs far less maintenance.
- **Rule/keyword-based analysis (no LLM):** Rejected — poor language nuance at similar implementation effort.
- **Persistence / scheduling from day one:** Rejected — no consumer runs off it; deferred to research (ADR 0008).
- **Returning a single `NewsAnalysis` (not a list):** Rejected — a list-shaped contract costs the same to build and serves future search/composite consumers; single-response shape would force an API change when the first consumer asks for more than one symbol.

## Consequences

1. New `news` Modulith leaf module with `package-info.java`, public API at root, implementation under `.internal`.
2. Contract locked in the ADR (above) — downstream callers can rely on it without reading code.
3. Budget counters are config (singleton-bounded), fail-fast; no global quota in v1.
4. **Deferred (pending research):** LLM provider/model, per-user quota & spend management, scheduling/cadence, output persistence & storage (Flyway migrations, tables, instrument/symbol entity).
5. This ADR intentionally leaves **storage, scheduling, and persistence unpriced** — they stay deferred until a consumer and an instrument entity exist.
