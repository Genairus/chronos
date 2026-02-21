# Chronos Examples

This directory is organized into four sections. If you're new, start with **Getting Started**.

---

## 1. Getting Started (`getting-started/`)

A minimal two-file project that shows the core language features in one pass:

| File | What it covers |
|------|----------------|
| `domain.chronos` | `namespace`, `shape`, `enum`, `entity` with entity-scoped `invariant`, `relationship`, field traits (`@required`) |
| `journeys.chronos` | Cross-file `use` imports, `actor`, `policy`, global `invariant`, `journey` with steps, preconditions, outcomes, and traits |

**Try it:**

```sh
# Generate a Markdown PRD from the two-file model
chronos prd examples/getting-started/ --out /tmp/getting-started-prd/

# Validate only (print diagnostics, no output files)
chronos validate examples/getting-started/domain.chronos
chronos validate examples/getting-started/journeys.chronos
```

Output lands in `/tmp/getting-started-prd/catalog.journeys-prd.md` (filename derived from namespace).

---

## 2. E-Commerce (`ecommerce/`)

A production-quality, multi-namespace e-commerce checkout model. This is the largest example
and shows how a real bounded-context design maps onto Chronos — three namespaces, cross-file
`use` imports, actor inheritance, error types, `deny` blocks, a state machine, and two journeys
with error-path variants.

```
ecommerce/
├── common/
│   ├── types.chronos                    # shared shapes (Money, Address), entities (Customer, Product),
│   │                                    # list, map, and a global invariant
│   └── common-actors-and-policies.chronos  # actors with inheritance, GDPR/PCI policies
├── checkout/
│   ├── checkout-domain.chronos          # enum, entities (Cart, Order) with invariants, relationships
│   ├── checkout-journeys.chronos        # two journeys (GuestCheckout, AuthenticatedCheckout)
│   │                                    # with variants, SLO/KPI/compliance traits
│   └── checkout-statemachine.chronos    # statemachine governing Order.status transitions
└── payments/
    └── payments.chronos                 # PaymentMethod enum, PaymentInstrument entity,
                                         # three error types, one deny block
```

Three namespaces (`ecommerce.common`, `ecommerce.checkout`, `ecommerce.payments`) linked by `use` imports.

**Try it:**

```sh
# Compile all files together and produce a combined PRD
chronos prd examples/ecommerce/ --name ecommerce-prd --out /tmp/ecommerce-prd/
```

---

## 3. Single-Feature Showcases (top-level files)

Each file demonstrates one language construct in isolation.

| File | Demonstrates |
|------|--------------|
| `deny-example.chronos` | `deny` blocks with `@compliance` traits and `scope` |
| `error-example.chronos` | `error` types with `code`, `severity`, `recoverable`, and `payload` |
| `invariants-example.chronos` | Entity-scoped and global `invariant` with `expression`, `severity`, `message` |
| `my-journeys.chronos` | Minimal `journey` with actor and three steps |
| `statemachine-example.chronos` | `statemachine` with `states`, `initial`, `terminal`, transitions with `guard` and `action` |

**Try any of them:**

```sh
chronos validate examples/deny-example.chronos
chronos prd examples/statemachine-example.chronos
```

---

## 4. Integration Fixtures (`integration/`)

These files are used as inputs to the compiler's integration tests. They are kept here because
tests reference them by path and because they serve as concise, correct examples of specific
grammar features.

| File | Purpose |
|------|---------|
| `minimal-entity.chronos` | Two entities with `@required` — smallest valid model |
| `actor-and-journey.chronos` | `actor` + `entity` + `journey` with `@kpi` and `@description` |
| `checkout.chronos` | Comprehensive single-file example: all major shape types in one namespace |
| `relationship-basic.chronos` | Three entities with two `relationship` declarations |
| `multi/` | Two-file model spanning two namespaces (`shop.domain`, `shop.journeys`) |
| `*.golden.md` | Expected PRD output files — used as snapshot test baselines, not documentation |

You can run any fixture through the CLI, but expect validation warnings on some of them
(they are intentionally minimal to keep tests fast).

---

## Language Reference

See [../docs/quick-reference.md](../docs/quick-reference.md) for a full syntax cheatsheet covering all 13 shape types.
