# Chronos Product Requirements Document

## Namespaces

- `shop.domain`
- `shop.journeys`

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

| Step | Action | Expectation | Outcome | Telemetry | Risk |
|------|--------|-------------|---------|-----------|------|
| viewListing | Customer views product listing | Product grid is displayed | — | — | — |

**Outcomes**

- ✅ Success: Products displayed to customer


---

## Data Model

### Entities

#### shop.domain.Product

> Core product available in the catalog.

| Field | Type | Required |
|-------|------|----------|
| id | String |  |
| name | String |  |
| price | Float |  |

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
