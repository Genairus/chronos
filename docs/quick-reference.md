# Chronos Language Quick Reference

A single-page cheatsheet for writing `.chronos` files. For full grammar details see the ANTLR source at
`chronos-parser/src/main/antlr/com/genairus/chronos/parser/Chronos.g4`.

---

## File Structure

Every `.chronos` file follows this layout:

```chronos
namespace com.example.checkout        // required, exactly one per file

use com.example.shared#Money          // optional imports (zero or more)
use com.example.shared#Currency

// shape definitions follow (any order, any count)
entity Order { ... }
actor Customer
journey CheckoutJourney { ... }
```

---

## Comments

```chronos
// Regular comment — discarded entirely.

/// Doc comment — attached to the declaration that immediately follows.
/// Multiple consecutive /// lines are concatenated.
entity Product {
    id: String
}
```

---

## Primitive Types

| Keyword     | Meaning                          |
|-------------|----------------------------------|
| `String`    | UTF-8 text                       |
| `Integer`   | 32-bit integer                   |
| `Long`      | 64-bit integer                   |
| `Float`     | 64-bit floating-point            |
| `Boolean`   | true / false                     |
| `Timestamp` | point in time (ISO-8601 implied) |
| `Blob`      | binary data                      |
| `Document`  | schemaless JSON-like object      |

Generic collection types: `List<T>` and `Map<K, V>` (can appear inline in field positions).

---

## Traits

Traits annotate any shape or field. Three forms:

```chronos
@required                                        // bare — no arguments
@description("A registered shopper")             // positional string argument
@kpi(metric: "completion_rate", target: "95%")   // named arguments
@compliance("PCI-DSS")                           // compliance tag
```

Trait argument values: `"string"`, `123`, `3.14`, `true`/`false`, or a qualified name reference.

Traits placed **before a field** apply to that field; traits placed **before a shape keyword** apply to the shape.

---

## The 13 Shape Types

### 1. `entity` — a domain object with identity

```chronos
/// A customer who places orders.
entity Customer {
    @required
    id: String
    name: String
    email: String
}

// Inheritance (single parent)
actor SupportAgent extends CustomerActor
entity PremiumCustomer extends Customer {
    loyaltyPoints: Integer
}
```

### 2. `shape` — a value object (no identity)

```chronos
shape Money {
    amount: Float
    currency: String
}
```

### 3. `list` — a typed collection

```chronos
list CartItemList {
    member: CartItem
}
```

### 4. `map` — a typed key-value collection

```chronos
map TagMap {
    key: String
    value: List<String>
}
```

### 5. `enum` — a set of named constants

Members may optionally carry integer values. Mixing valued and unvalued members in the same enum is allowed.

```chronos
enum OrderStatus {
    PENDING
    CONFIRMED
    SHIPPED
    CANCELLED
}

enum PaymentStatus {
    PENDING    = 1
    PROCESSING = 2
    PAID       = 3
    DECLINED   = 4
}
```

### 6. `actor` — a participant in a journey

```chronos
actor Customer

@description("A shopper using the storefront UI")
actor CustomerActor

actor SupportAgent extends CustomerActor
```

### 7. `policy` — a governance or compliance rule

```chronos
@compliance("PCI-DSS")
policy PaymentSecurity {
    description: "Payment card data must never be stored in plaintext"
}
```

### 8. `journey` — an end-to-end user flow

```chronos
/// Guides a customer through checkout.
@kpi(metric: "checkout_completion_rate", target: "95%")
journey CheckoutJourney {
    actor: Customer
    preconditions: [
        "Cart contains at least one item",
        "Customer is authenticated"
    ]
    steps: [
        step ReviewCart {
            action: "Customer reviews cart contents"
            expectation: "Cart summary is displayed with correct totals"
            telemetry: [CartReviewed, PageViewed]
            risk: "Stale price data may cause discrepancy"
        },
        step ConfirmOrder {
            action: "Customer submits the order"
            expectation: "Order record is persisted with status PENDING"
            outcome: TransitionTo(OrderConfirmed)
            telemetry: [OrderSubmitted]
        }
    ]
    variants: {
        PaymentDeclined: {
            trigger: PaymentDeclinedError
            steps: [
                step NotifyDeclined {
                    action: "System notifies customer of the decline"
                    expectation: "Error message is displayed with retry option"
                    outcome: ReturnToStep(EnterPaymentDetails)
                }
            ]
            outcome: ReturnToStep(EnterPaymentDetails)
        }
    }
    outcomes: {
        success: "Order exists with status PAID and confirmation email sent",
        failure: "Cart remains intact and no charge is made"
    }
}
```

**Step fields** (all optional except `action` and `expectation`):

| Field         | Value                                           |
|---------------|-------------------------------------------------|
| `action`      | string — what the actor does                    |
| `expectation` | string — what the system must do in response    |
| `outcome`     | `TransitionTo(State)` or `ReturnToStep(StepId)` |
| `telemetry`   | `[Event1, Event2]` — events emitted             |
| `risk`        | string — risk note                              |

### 9. `relationship` — a named association between entities

```chronos
relationship CustomerOrders {
    from: Customer
    to: Order
    cardinality: one_to_many
}

relationship OrderContents {
    from: Order
    to: OrderItem
    cardinality: one_to_many
    semantics: composition
}
```

### 10. `invariant` — a business rule

**Entity-scoped** (inside an entity body):

```chronos
entity Order {
    totalAmount: Float

    invariant PositiveTotal {
        expression: "totalAmount > 0"
        severity: error
        message: "Order total must be positive"
    }
}
```

**Global** (top-level, references multiple entities):

```chronos
invariant ActiveOrderLimit {
    scope: [Customer, Order]
    expression: "count(Customer.orders, o => o.status == PENDING) <= 10"
    severity: warning
    message: "Customer should not exceed 10 pending orders"
}
```

`severity` values: `error`, `warning`, `info`, `critical`.

### 11. `deny` — a prohibition or compliance constraint

```chronos
@compliance("PCI-DSS")
deny StorePlaintextPasswords {
    description: "The system must never store passwords in plaintext"
    scope: [UserCredential]
    severity: critical
}
```

### 12. `error` — a named failure case with structured payload

```chronos
error PaymentDeclinedError {
    code: "PAYMENT_DECLINED"
    severity: high
    recoverable: true
    message: "Payment gateway returned a declined response"
    payload: {
        declineReason: String
        retryAllowed: Boolean
    }
}
```

### 13. `statemachine` — a lifecycle definition for an entity field

```chronos
@description("Manages the lifecycle of an order from creation to completion")
statemachine OrderLifecycle {
    entity: Order
    field: status
    states: [PENDING, PAID, SHIPPED, DELIVERED, CANCELLED]
    initial: PENDING
    terminal: [DELIVERED, CANCELLED]
    transitions: [
        PENDING -> PAID {
            guard: "payment.status == APPROVED"
            action: "Emit OrderPaidEvent"
        },
        PAID -> SHIPPED {
            guard: "fulfillment.status == DISPATCHED"
            action: "Emit OrderShippedEvent"
        },
        SHIPPED -> DELIVERED {
            guard: "delivery confirmation received"
        },
        PENDING -> CANCELLED          // bare transition — no guard or action required
    ]
}
```

---

## Use / Import

```chronos
namespace com.example.checkout

use com.example.shared#Money
use com.example.shared#Currency
```

- **Left of `#`** — the namespace that defines the symbol.
- **Right of `#`** — the exact shape name to import.
- After importing, refer to the shape by its simple name (`Money`, not `com.example.shared.Money`).
- You can also reference any shape in the **same namespace** by simple name without a `use` declaration, even across multiple files.

---

## Multi-File Compilation

```
chronos prd src/models/           # compiles all .chronos files under the directory
chronos build                     # compiles according to chronos.build config
```

- Each file must declare exactly one `namespace`.
- Multiple files may share the same namespace (their symbols are merged automatically).
- Cross-namespace references require an explicit `use` import.
- Same-namespace references work without `use`, regardless of which file they are in.

Minimal two-file example:

```chronos
// common/types.chronos
namespace shop.domain

entity Product {
    id: String
    name: String
}
```

```chronos
// journeys/catalog.chronos
namespace shop.journeys

use shop.domain#Product

actor Customer

journey BrowseCatalog {
    actor: Customer
    steps: [
        step ViewListing {
            action: "Customer views product listing"
            expectation: "Products are displayed"
        }
    ]
    outcomes: {
        success: "Products displayed to customer"
    }
}
```

---

## Generator Targets

Run `chronos generate --target <name> <input>` (or use `chronos prd` as a shortcut) to produce
output from a compiled model.

| Target | Output | Description |
|--------|--------|-------------|
| `prd` / `markdown` | `<namespace>-prd.md` | Markdown PRD — journeys, data model, actors, policies |
| `jira` | `<namespace>-backlog.csv` | Jira-importable CSV — journeys → Epics, steps → Stories, variants → Stories (High priority), policies/denies → compliance Stories |
| `typescript` | `<namespace>.d.ts` | TypeScript type definitions for all shapes |
| `mermaid-state` | `<namespace>-state.md` | Mermaid state diagrams for all statemachines |
| `test-scaffold` | `<namespace>-tests.java` | JUnit test scaffolding for invariants |
| `statemachine-tests` | `<namespace>-sm-tests.java` | JUnit test scaffolding for state machine transitions |

**Jira CSV column mapping:**

| CSV Column | Jira Field | Value |
|------------|-----------|-------|
| Summary | Summary | Journey name (Epic) or `name: action` (Story) |
| Issue Type | Issue Type | `Epic` for journeys, `Story` for everything else |
| Description | Description | Actor, preconditions, KPI, outcomes (Epics); expectation, outcome, risk, telemetry (Stories) |
| Priority | Priority | `High` for variants/compliance; `Highest` for `deny` with `severity: critical` |
| Labels | Labels | `<namespace>` or `<namespace> variant` or `<namespace> compliance [framework]` |
| Epic Name | Epic Name | Journey name (Epics only) |
| Epic Link | Epic Link | Parent journey name (Stories only) |
| Story Points | Story Points | Step count (Epics/variants) or `1` (step Stories) |

```sh
chronos generate --target jira examples/ecommerce/checkout/
# → ecommerce.checkout-backlog.csv
```

---

## Common Gotchas

| Symptom | Cause | Fix |
|---------|-------|-----|
| `CHR-001` — journey has no actor | `actor:` field missing from journey body | Add `actor: ActorName` |
| `CHR-008` — unresolved type reference | A field type refers to a name not defined or imported | Define the shape or add a `use` import |
| `CHR-012` — unresolved symbol reference at finalization | Cross-file ref not resolvable | Check spelling and that the target file is included in compilation |
| `CHR-014` — duplicate namespace definition | Two files in the same compilation set declare conflicting top-level symbols | Rename one shape |
| Enum member with `=` value but wrong type | Only integer literals are valid enum values | Use `MEMBER = 1`, not `MEMBER = "one"` |
| `@required` applied to wrong target | `@required` is a field trait, not a shape trait | Move `@required` immediately before the field, not before `entity` |
| Bare transition silently succeeds | `PENDING -> PAID` with no `{ guard: ... }` block is valid — it fires unconditionally | Add a guard if the transition must be conditional |
| Doc comment not attached | A blank line between `///` and the declaration breaks association | Remove the blank line |
