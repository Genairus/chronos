# Chronos Product Requirements Document

## Namespaces

- `shop.domain`
- `shop.journeys`

## Executive Summary

This PRD covers 1 journey, 1 entity, 0 value objects, 2 enumerations, 1 actor, 0 policies, 0 error types, and 0 state machines across 2 namespaces.

**Journeys:**

- **shop.journeys.BrowseCatalog**


## Table of Contents

- [Journeys](#journeys)
  - [shop.journeys.BrowseCatalog](#shopjourneysbrowsecatalog)
- [Data Model](#data-model)
  - [Entities](#entities)
  - [Enumerations](#enumerations)
- [Actors](#actors)

---

## Journeys

### shop.journeys.BrowseCatalog

> Browse the product catalog and view details.
>
> **Actor:** Customer

**Happy Path**

| Step | Action | Expectation | Outcome | SLO | Telemetry | Risk | Input | Output |
|------|--------|-------------|---------|-----|-----------|------|-------|--------|
| <a id="shopjourneysbrowsecatalog-viewlisting"></a>viewListing | Customer views product listing | Product grid is displayed | — | — | — | — | — | — |

**Outcomes**

- ✅ Success: Products displayed to customer


---

## Data Model

### Entities

#### shop.domain.Product

> Core product available in the catalog.

| Field | Type |
|-------|------|
| id | String |
| name | String |
| price | Float |

### Enumerations

#### shop.domain.Status

> Product availability status.

| Member | Ordinal |
|--------|----------|
| ACTIVE | 1 |
| INACTIVE | 2 |

#### shop.journeys.Status

> Customer session state.

| Member | Ordinal |
|--------|----------|
| ONLINE | 1 |
| OFFLINE | 2 |

---

## Actors

#### shop.journeys.Customer

> A customer browsing the catalog.

**Description:** —
