# ADR 0009: News Analysis Agent

- **Author:** Mateo Plavcic
- **Date:** 2026-09-18
- **Status:** Proposed
- **Tags:** backend, architecture

## Status

Proposed

## Context

News analysis is the first agent built on the multi-agent architecture (ADR 0008). It must collect financial news for the instruments Tradironi tracks and produce an analysis (a summary and/or a qualitative news sentiment). Choices involved — data source, analysis pipeline — have material tradeoffs and are founding precedents for the agents that follow.

## Decision

Build a **`news` Modulith leaf module** with the following shape:

- **Ingestion — seed with curated RSS/Atom feeds** over individual sources aggregated per market/instrument. The drafted approach is an HTTP polling client that fetches the feeds and feeds each item into the analysis pipeline.
- **Analysis pipeline — LLM-based enrichment:** each fetched article is passed to an LLM for (a) a headline/body summary and (b) a directional news sentiment label (`positive` / `neutral` / `negative`) with a short rationale.
- **Module boundary:** public API at the `news` package root (e.g. `NewsAnalysisService`), implementation under `.internal`, per ADR 0008.

## Rationale

- **Pro:** RSS/Atom is free, well-standardized, and source-controlled by the team — no vendor API keys or rate-limit quotas to start.
- **Pro:** LLM enrichment gives human-readable signal cheaply and can be swapped (model/provider) behind the analyzer seam.
- **Con:** RSS coverage is uneven per-instrument and needs curation; some headlines are paywalled/short.
- **Con:** LLM cost and latency per article — needs a mitigation strategy (to be covered by a future decision on budget/throttling).
- **Con:** Sentiment labels are probabilistic; the analysis is advisory, not a trading signal.

## Alternatives Considered

- **Paid news APIs (NewsAPI, Marketaux, etc.):** Deferred — reliable and aggregate well, but require a subscription and API-key management out of the box; can slot in later behind the same `NewsSource` seam.
- **Rule/keyword-based analysis without an LLM:** Rejected for the headline use case — poor sentiment nuance at similar implementation effort.
- **Streaming/high-frequency ingestion (WebSocket feeds):** Rejected for v1 — intraday streaming is a later refinement, not a starting requirement.

## Consequences

1. New `news` Modulith module with `package-info.java`, root-level public API, `.internal` implementation.
2. A provider seam (`NewsSource`) exists from day one so paid/further sources can be added without touching the pipeline.
3. Analysis output is advisory and must be labeled as such to downstream consumers.
4. **Not decided here (pending research):** scheduling/cadence of fetching and analysis, output persistence and storage shape (incl. Flyway migrations), and LLM provider/model choice. These will be added to this ADR once researched.
5. The LLM provider/model choice remains open — expected to be its own short ADR once selected.