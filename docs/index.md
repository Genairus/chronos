# Chronos

**Chronos** is a requirements modeling language designed to describe the behavior of software systems in a structured, machine-readable form — so that AI coding assistants, document generators, and static analyzers can all work from the same source of truth.

Write once. Generate backlogs, PRDs, TypeScript types, test scaffolding, and more.

---

## In 30 seconds

```chronos
namespace shop.checkout

actor Customer

entity Order {
    @required
    id: String
    status: OrderStatus
}

enum OrderStatus {
    PENDING = 1
    CONFIRMED = 2
    PAID = 3
}

/// The end-to-end checkout flow.
@kpi(metric: "checkout_completion_rate", target: "95%")
journey Checkout {
    actor: Customer
    steps: [
        step ReviewCart {
            action: "Customer reviews cart and totals"
            expectation: "All items and prices are correct"
        },
        step Pay {
            action: "Customer submits payment"
            outcome: TransitionTo(PAID)
        }
    ]
    outcomes: {
        success: "Order confirmed and email sent",
        failure: "Cart intact, no charge made"
    }
}
```

Then run:

```sh
chronos prd checkout.chronos
# → shop.checkout-prd.md

chronos generate --target jira checkout.chronos
# → shop.checkout-backlog.csv

chronos generate --target typescript checkout.chronos
# → shop.checkout.d.ts
```

---

## Why Chronos?

| Without Chronos | With Chronos |
|-----------------|--------------|
| Requirements live in Confluence (stale) | Single `.chronos` file is the source of truth |
| Jira tickets created manually | `chronos generate --target jira` generates the backlog |
| Developers write their own TypeScript types | `--target typescript` emits `.d.ts` files |
| AI bots get inconsistent context | Feed the IR directly to your coding assistant |
| PRDs drift from the implementation | PRD is regenerated on every CI run |

---

## Key Concepts

- **13 shape types** — `entity`, `shape`, `enum`, `list`, `map`, `actor`, `policy`, `journey`, `relationship`, `invariant`, `deny`, `error`, `statemachine`
- **Multi-file namespaces** — split large models across files; Chronos resolves cross-file references automatically
- **Traits** — attach metadata (`@kpi`, `@compliance`, `@description`, `@required`) to any shape
- **Generators** — pluggable output targets: Markdown PRD, Jira CSV, TypeScript, Mermaid diagrams, JUnit scaffolding
- **Validators** — 20+ rules catch unresolved references, missing required fields, and constraint violations at compile time

---

## Next steps

- [**Install**](install.md) — get the `chronos` binary on your machine
- [**Quick Start**](examples/getting-started.md) — walk through the getting-started example
- [**Language Reference**](quick-reference.md) — every shape type and trait in one page
- [**Generators**](generators.md) — all output targets and what they produce
