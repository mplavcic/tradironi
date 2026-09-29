# Research

Turns external sources into Insights about Instruments. Research observes and interprets; it does not recommend holdings, value instruments, or propose trades. Every Insight it produces is advisory.

## The assessment

**Insight**:
A single assessment of one Instrument on one aspect, formed at a known instant, carrying a Stance, a Conviction, and the Evidence it rests on. Immutable, and true only as of the instant it was formed.
_Avoid_: Analysis (also names the run that produced it), Signal, Opinion, Result

**As Of**:
The instant an Insight was formed, and therefore the instant after which it may no longer be believed. Every Insight is read together with its As Of; an Insight presented without it is misleading however current it once was.
_Avoid_: Timestamp, Created at

**Stance**:
The direction an Insight takes on an Instrument — bullish, bearish, or neutral. A Stance is a judgment about direction only; it says nothing about how strongly to believe it.
_Avoid_: Sentiment, Polarity, Direction, Score

**Conviction**:
How much weight an Insight's Stance deserves, on a fixed scale. Conviction is the analyst's own estimate of its reliability, and a neutral Stance with high Conviction is a meaningful result.
_Avoid_: Confidence, Score, Strength

**Advisory**:
The standing rule that an Insight informs a decision and never constitutes one. Research is Advisory by definition, and no product of it may be presented as a trading instruction.
_Avoid_: Recommendation, Signal

## The basis

**Evidence**:
Something observed that an Insight rests on, traceable to where it came from. An Insight without Evidence is an assertion, not a finding.
_Avoid_: Data, Input, Source

**Citation**:
An Evidence item that is dated and attributable — a named publisher, a retrievable location, and a moment of publication. A Citation is what makes an Insight auditable and its age knowable without asking the analyst.
_Avoid_: Reference, Link, Source

**Relevance**:
A Citation's bearing on the Instrument under analysis, as judged in the act of analysis. Relevance is a judgment made per analysis, never a property the Citation carries on its own.
_Avoid_: Match, Score, Tag

## The production

**AnalysisKind**:
The kind of question being asked of an Instrument — news, fundamental, or technical. An AnalysisKind is what decides which sources are worth reading and how long an Insight of that kind stays believable.
_Avoid_: Agent, Module, Analyzer

**Staleness Window**:
How long an Insight formed by a given AnalysisKind remains believable. It follows from the pace of the world being observed, and is a fact about the analysis rather than a setting: news goes stale in hours, fundamentals in quarters.
_Avoid_: TTL, Expiry, Cache lifetime

**AnalysisRun**:
One attempt at forming Insights, which either produces a batch of them or produces none. A run is the unit of repetition and of failure; it is not itself evidence and is never cited.
_Avoid_: Analysis (ambiguous with Insight), Job, Task, Request

## News

**Article**:
A dated piece of published writing that may become a Citation. An Article is the raw material; the Insight drawn from it is a separate thing with its own Stance and Conviction.
_Avoid_: Story, Post, Item, Entry, News

**Publisher**:
The organization responsible for an Article, and the origin that distinguishes one publisher's account of an event from another's.
_Avoid_: Source, Outlet, Site, Feed

**Feed**:
A repeating source of Articles from one or more Publishers, with no claim to completeness and no guarantee of timeliness. Feeds overlap, and the same Article may arrive through several.
_Avoid_: Channel, Source, Stream, Subscription

**Tone**:
The emotional coloring of an Article's own wording — positive, neutral, or negative. Tone describes how an Article reads; it is not a Stance on the Instrument, and the two routinely disagree.
_Avoid_: Sentiment, Mood, Polarity
