# Quick Start: Getting Started Example

This walkthrough uses the two-file example in `examples/getting-started/`. It covers the most important Chronos concepts in one pass.

## What we're modeling

A simple **product catalog** system. Two namespaces:

- `catalog.domain` — the data model (shapes, entities, invariants, relationships)
- `catalog.journeys` — the behavior (actor, policy, journey)

## Step 1 — Write the domain model

**`examples/getting-started/domain.chronos`**

```chronos
namespace catalog.domain

/// A monetary value with an explicit currency code.
shape Price {
    amount: Float
    currency: String
}

enum Category {
    BOOKS       = 1
    ELECTRONICS = 2
    CLOTHING    = 3
}

/// A product available in the catalog.
entity Product {
    @required
    id: String
    name: String
    summary: String
    price: Price
    listPrice: Float
    category: Category
    inStock: Boolean

    invariant PositiveListPrice {
        expression: "listPrice > 0"
        severity: error
        message: "List price must be positive"
    }
}

/// A supplier who provides products to the catalog.
entity Supplier {
    @required
    id: String
    name: String
    contactEmail: String
}

invariant ProductHasKnownSupplier {
    scope: [Product, Supplier]
    expression: "true"
    severity: warning
    message: "Every product should reference a known supplier"
}

relationship SupplierProducts {
    from: Supplier
    to: Product
    cardinality: one_to_many
}
```

Key points:
- `shape` = value object (no identity), `entity` = domain object with identity
- Entity-scoped `invariant` lives inside the entity block; global `invariant` lives at the top level
- `@required` is a field trait — it marks mandatory fields for validators and generators

## Step 2 — Write the journeys

**`examples/getting-started/journeys.chronos`**

```chronos
namespace catalog.journeys

use catalog.domain#Product
use catalog.domain#Supplier

/// The catalog manager who adds, edits, and publishes products.
actor CatalogManager

@compliance("internal-data-quality-policy")
policy DataQuality {
    description: "Product descriptions, prices, and category assignments must be reviewed before publication."
}

event ProductFormOpened { }
event SupplierSelected { }
event ProductPublished { }

/// The complete flow for adding a new product to the catalog.
@kpi(metric: "catalog_onboarding_time", target: "<2 business days")
journey AddProduct {
    actor: CatalogManager
    preconditions: [
        "Supplier exists in the system",
        "Product details have been reviewed"
    ]
    steps: [
        step EnterProductDetails {
            action: "Manager enters name, description, price, and category"
            expectation: "Form validates all required fields"
            telemetry: [ProductFormOpened]
            risk: "Free-text fields may contain unvetted content"
        },
        step SelectSupplier {
            action: "Manager associates the product with an existing supplier"
            expectation: "Supplier lookup returns a matching result"
            telemetry: [SupplierSelected]
        },
        step PublishProduct {
            action: "Manager confirms and publishes the product"
            expectation: "Product appears in catalog with inStock status"
            outcome: TransitionTo(Published)
            telemetry: [ProductPublished]
        }
    ]
    outcomes: {
        success: "New product is visible in the catalog with correct details",
        failure: "Product draft is saved but not published; manager sees a validation error"
    }
}
```

Key points:
- `use catalog.domain#Product` imports a specific shape by qualified name
- `actor` is a named participant; `journey.actor:` references it
- `@kpi`, `@compliance` are standard built-in traits

## Step 3 — Generate output

```sh
# Markdown PRD
chronos prd examples/getting-started/ --output /tmp/getting-started/

# Jira backlog CSV
chronos generate --target jira examples/getting-started/ --output /tmp/getting-started/

# Validate only (print diagnostics)
chronos validate examples/getting-started/domain.chronos
chronos validate examples/getting-started/journeys.chronos
```

Output lands in `/tmp/getting-started/`:
- `catalog.journeys-prd.md` — combined PRD for both namespaces
- `catalog.journeys-backlog.csv` — Jira epics and stories

## Next steps

- See the [E-Commerce example](ecommerce.md) for a full production-quality multi-namespace model
- Read the [Language Reference](../quick-reference.md) for all 13 shape types
- See [Generators](../generators.md) for all available output targets
