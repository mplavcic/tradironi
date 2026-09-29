# Instrument Catalog

The registry of tradable things and the ways they are quoted. It is the shared kernel: every other context states its subject in this context's terms, and this context states nothing about why anyone would want to know an Instrument.

## Identity

**Instrument**:
The thing that is being analyzed, held, or traded, independent of any place it is listed. Identity is permanent and does not depend on how the Instrument is currently spelled on a screen.
_Avoid_: Symbol, Ticker, Stock, Security, Asset

**InstrumentId**:
The stable identity of an Instrument. It outlives listing changes, renames, and delistings, so every reference to an Instrument outside this context is expressed as an InstrumentId.
_Avoid_: Symbol, Ticker

**Listing**:
A single quotation of an Instrument on one Market — the pairing of an Instrument with a symbol, currency, and venue. The same Instrument may have several, and they are not interchangeable.
_Avoid_: Symbol, Ticker, Instrument

**Delisted**:
The state of a Listing that no longer trades. A Delisted Listing keeps its history and is never silently reused, because its symbol may later refer to something else entirely.
_Avoid_: Inactive, Closed

## Composition

**Issuer**:
The legal entity that a share of common or preferred stock represents. An Issuer may have many Instruments, and one Instrument may be issued by more than one party for different classes.
_Avoid_: Company, Organization

**AssetClass**:
The broad category an Instrument belongs to — equity, fixed income, fund, derivative, currency, commodity. It governs which analyses are meaningful for the Instrument and which are nonsense.
_Avoid_: Type, Kind, Category

## Places

**Market**:
A venue on which Listings trade, carrying its own currency, calendar, and open hours. The same Instrument trading on two Markets is exposed to two calendars at once.
_Avoid_: Exchange, Venue

**Currency**:
The unit a Listing is quoted and settled in. A Listing's Currency is a property of the Listing, never of the Instrument.
_Avoid_: Quote currency, Trading currency
