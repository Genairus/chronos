# Write Requirements with Your AI Coding Bot

**Chronos turns a feature brief into validated, machine-readable requirements — in minutes, with your AI assistant doing most of the work.**

Write one `.chronos` file. Get back a Markdown PRD, a Jira-ready backlog, TypeScript type definitions, test scaffolding, and more — all generated from a single source of truth your whole team can version-control and validate.

---

## Install Chronos

Chronos is a self-contained native binary — no JRE or runtime required.

**macOS / Linux**

```sh
brew install Genairus/tap/chronos
```

**Windows**

```powershell
scoop bucket add chronos https://github.com/Genairus/scoop-chronos
scoop install chronos
```

Verify it works:

```sh
chronos --version
# chronos 0.1.0
```

For direct downloads, ARM64, and other options see the [full install guide](install.md).

---

## What You'll Have in 5 Minutes

1. A validated `.chronos` requirements model your AI assistant generated from your feature brief.
2. A shareable Markdown PRD (`chronos prd`).
3. A Jira-ready backlog CSV (`chronos generate --target jira`).
4. Confidence — the Chronos compiler catches ambiguity and missing fields before your engineers do.

---

## How This Guide Works

Most AI coding assistants know nothing about Chronos. This guide gives you everything to change that:

1. **Part 1** — A complete Chronos language spec you can paste into any LLM (ChatGPT, Copilot, Cursor, etc.) as a system prompt or project context.
2. **Part 2** — A `CLAUDE.md` template for Claude Code users: loaded automatically into every session, it makes Claude an instant Chronos expert.
3. **Part 3** — Workflow patterns: compile-fix loops, prompt templates, and team conventions.

You'll need:

- The `chronos` CLI installed (above).
- A short feature brief — goals, users, constraints, known systems.
- Familiarity with the basics is helpful but not required; Part 1 covers everything from scratch.

Useful references:

- [Quick Reference](quick-reference.md) — every shape type and trait on one page
- [Examples](../examples/README.md) — runnable example models

---

# Part 1: Teaching Any LLM the Chronos Language

Copy the text block below into your LLM's system prompt, project instructions, or conversation context. It is self-contained.

````text
You are writing Chronos requirements files (.chronos). Chronos is a domain-specific
language for specifying software requirements as structured, compilable models.
The Chronos compiler validates these files and generates artifacts (PRDs, backlogs,
type definitions, test scaffolds).

Follow this specification exactly. Do not invent syntax.

═══════════════════════════════════════════════════════════════════════════════════
CHRONOS LANGUAGE SPECIFICATION
═══════════════════════════════════════════════════════════════════════════════════

── FILE STRUCTURE ─────────────────────────────────────────────────────────────

Every .chronos file has exactly this layout:

    namespace <qualified.name>       // required, exactly one per file
    use <namespace>#<ShapeName>      // zero or more imports
    // ... shape definitions (any order, any count)

── COMMENTS ───────────────────────────────────────────────────────────────────

    // Regular comment — discarded by the compiler.
    /// Doc comment — attached to the next declaration. Multiple consecutive
    /// lines are concatenated.

── PRIMITIVE TYPES ────────────────────────────────────────────────────────────

    String      UTF-8 text
    Integer     32-bit integer
    Long        64-bit integer
    Float       64-bit floating-point
    Boolean     true / false
    Timestamp   ISO-8601 point in time
    Blob        binary data
    Document    schemaless JSON-like object

Generic collection types can appear inline in field positions:
    List<T>          e.g. List<OrderItem>
    Map<K, V>        e.g. Map<String, String>

── TRAITS ─────────────────────────────────────────────────────────────────────

Traits annotate shapes or fields. Three forms:

    @required                                        // bare
    @description("A registered shopper")             // positional string arg
    @kpi(metric: "completion_rate", target: "95%")   // named args

Trait argument values: "string", 123, 3.14, true/false, duration literal (5m, 30s, 2h),
or a qualified name reference.

Placement rules:
- Trait BEFORE a field → applies to that field.
- Trait BEFORE a shape keyword (entity, journey, etc.) → applies to the shape.

Common traits: @required, @description("..."), @kpi(...), @compliance("..."),
@pii, @owner("..."), @slo(ms: 500), @epic("..."), @priority(high),
@story("..."), @estimate(3), @timeout(5m, onExpiry: VariantName),
@ttl(30d, action: archive), @schedule(cron: "0 0 * * 1").

── THE 15 SHAPE TYPES ────────────────────────────────────────────────────────

1. ENTITY — a domain object with identity

    entity Customer {
        @required
        id: String
        name: String
        email: String
    }

    // Inheritance (single parent):
    entity PremiumCustomer extends Customer {
        loyaltyPoints: Integer
    }

    // Entity-scoped invariants go inside the body:
    entity Order {
        totalAmount: Float
        invariant PositiveTotal {
            expression: "totalAmount > 0"
            severity: error
            message: "Order total must be positive"
        }
    }

2. SHAPE — a value object (no identity)

    shape Money {
        amount: Float
        currency: String
    }

3. LIST — a named typed collection

    list CartItemList {
        member: CartItem
    }

4. MAP — a named typed key-value collection

    map TagMap {
        key: String
        value: List<String>
    }

5. ENUM — a set of named constants (optional integer values)

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

6. ACTOR — a participant in a journey

    actor Customer
    actor SupportAgent extends CustomerActor
    @description("Warehouse operative") actor FulfillmentActor

7. POLICY — a governance or compliance rule

    @compliance("PCI-DSS")
    policy PaymentSecurity {
        description: "Payment card data must never be stored in plaintext"
    }

    NOTE: The body contains ONLY `description: "..."`. The word `description`
    is a contextual keyword here — it CANNOT be used as a field name in
    entity or shape bodies. Use `summary` or another name instead.

8. JOURNEY — an end-to-end user flow (the core of Chronos)

    journey CheckoutJourney {
        actor: Customer                             // REQUIRED
        preconditions: [                            // optional
            "Cart contains at least one item",
            "Customer is authenticated"
        ]
        steps: [                                    // at least one step
            step ReviewCart {
                action: "Customer reviews cart"      // REQUIRED
                expectation: "Totals displayed"      // REQUIRED
                telemetry: [CartReviewed]            // optional, refs event types
                risk: "Stale price data"             // optional
            },
            step SubmitOrder {
                action: "Customer submits order"
                expectation: "Order persisted as PENDING"
                outcome: TransitionTo(PAID)          // optional
            }
        ]
        variants: {                                 // optional
            PaymentDeclined: {
                trigger: PaymentDeclinedError        // must ref a defined error type
                steps: [
                    step NotifyDeclined {
                        action: "Show decline message"
                        expectation: "Retry option displayed"
                        outcome: ReturnToStep(ReviewCart)
                    }
                ]
                outcome: ReturnToStep(ReviewCart)     // variant-level outcome
            }
        }
        outcomes: {                                 // REQUIRED
            success: "Order exists with status PAID",
            failure: "Cart remains intact, no charge"
        }
    }

    Step fields (action and expectation are required, all others optional):
        action:      string — what the actor does
        expectation: string — what the system must do in response
        outcome:     TransitionTo(State) or ReturnToStep(StepId)
        telemetry:   [Event1, Event2] — events emitted (must be declared)
        risk:        string — risk note
        input:       [fieldName: Type, ...] — data consumed by this step
        output:      [fieldName: Type, ...] — data produced by this step

9. RELATIONSHIP — a named association between entities

    relationship CustomerOrders {
        from: Customer
        to: Order
        cardinality: one_to_many       // one_to_one | one_to_many | many_to_many
        semantics: aggregation         // optional: association | aggregation | composition
        inverse: customerId            // optional: field name on target entity
    }

10. INVARIANT (global) — a business rule spanning multiple entities

    invariant ActiveOrderLimit {
        scope: [Customer, Order]
        expression: "count(Customer.orders, o => o.status == PENDING) <= 10"
        severity: warning              // error | warning | info
        message: "Customer should not exceed 10 pending orders"
    }

    (Entity-scoped invariants go inside the entity body — see #1 above.)

    SEVERITY for invariants: error, warning, or info.

11. DENY — a prohibition or compliance constraint

    @compliance("PCI-DSS")
    deny StorePlaintextPasswords {
        description: "Never store passwords in plaintext"
        scope: [UserCredential]
        severity: critical             // critical | high | medium | low
    }

    SEVERITY for deny: critical, high, medium, or low (NOT error/warning/info).

12. ERROR — a named failure case with structured payload

    error PaymentDeclinedError {
        code: "PAY-001"
        severity: high                 // critical | high | medium | low
        recoverable: true              // true | false
        message: "Payment declined by gateway"
        payload: {                     // optional block of typed fields
            declineReason: String
            retryAllowed: Boolean
        }
    }

    SEVERITY for error: critical, high, medium, or low (NOT error/warning/info).

13. STATEMACHINE — a lifecycle definition for an entity field

    statemachine OrderLifecycle {
        entity: Order
        field: status                  // must be an enum-typed field on the entity
        states: [PENDING, PAID, SHIPPED, DELIVERED, CANCELLED]
        initial: PENDING               // must be in states list
        terminal: [DELIVERED, CANCELLED]
        transitions: [
            PENDING -> PAID {
                guard: "payment approved"   // optional
                action: "Emit OrderPaid"    // optional
            },
            PAID -> SHIPPED,               // bare transition (no guard/action)
            SHIPPED -> DELIVERED,
            PENDING -> CANCELLED
        ]
    }

    State machine rules:
    - initial must be in states list.
    - All transition from/to states must be in states list.
    - Every non-terminal state must have at least one outbound transition.
    - Terminal states must NOT have any outbound transitions.
    - TransitionTo(X) in journey steps must reference a state from a declared statemachine.

14. ROLE — authorization role with allow/deny permissions

    role AdminRole {
        allow: [createUser, deleteUser, viewReports]
        deny: [purgeAuditLog]
    }

15. EVENT — a telemetry/domain event type

    event CartReviewed {}              // signal event (no fields)
    event OrderSubmitted {             // event with payload fields
        orderId: String
        totalAmount: Float
    }

    Events referenced in step telemetry arrays must be declared or imported.

── NAMESPACE & IMPORTS ────────────────────────────────────────────────────────

    namespace com.example.checkout
    use com.example.shared#Money
    use com.example.shared#Currency

- Left of # = source namespace. Right of # = shape name to import.
- After importing, use the simple name (Money, not com.example.shared.Money).
- Same-namespace shapes resolve automatically across files — no `use` needed.
- Multiple files can share the same namespace; their symbols merge.
- Cross-namespace references REQUIRE a `use` import.

── DURATION LITERALS ──────────────────────────────────────────────────────────

Unquoted durations used in trait arguments: 500ms, 5s, 2m, 1h, 7d, 1w.
Example: @timeout(30s, onExpiry: PaymentTimeout), @slo(ms: 500), @ttl(90d, action: archive).

── KEY VALIDATION RULES (the compiler enforces these) ─────────────────────────

- Every journey MUST have `actor:` and `outcomes:` with at least `success`.
- Every step MUST have `action:` and `expectation:`.
- Variant `trigger:` must reference a defined or imported `error` type.
- Invariant severity: error | warning | info.
- Deny and error severity: critical | high | medium | low.
- State machine: initial in states, transitions use declared states only,
  non-terminal states need outbound transitions, terminal states must not have them.
- TransitionTo(X) must reference a declared statemachine state.
- Step telemetry events must be declared or imported event types.
- Entity-scoped invariant expressions can only reference direct fields of that entity.

── AUTHORING CONTRACT ─────────────────────────────────────────────────────────

When generating Chronos files:
1. Follow the grammar strictly — do not invent syntax.
2. Use one namespace per file. Use explicit `use ns#Shape` for cross-namespace refs.
3. Prefer multi-file output organized by concern:
   - domain.chronos — entities, shapes, enums, relationships
   - actors-policies.chronos — actors, policies
   - journeys.chronos — journeys with variants
   - errors.chronos — error types
   - statemachines.chronos — statemachine definitions
   - events.chronos — event type declarations
4. Use doc comments (///) above major declarations.
5. Keep names unique within a namespace.
6. When unsure about requirements, ask clarifying questions first.
7. Always include both success and failure outcomes on journeys.
8. Always declare error types for variant triggers.
9. Always declare event types for step telemetry references.

Output format:
- List files you will create first.
- Provide each file in a separate fenced code block with the file path.
- After writing, do a self-check against the validation rules above.

── COMPLETE WORKED EXAMPLE ────────────────────────────────────────────────────

File: subscription/domain.chronos

    namespace com.acme.subscriptions

    shape Money {
        amount: Float
        currency: String
    }

    enum SubscriptionStatus {
        ACTIVE
        PAUSED
        CANCELLED
    }

    /// A recurring subscription plan held by a customer.
    entity Subscription {
        @required
        id: String
        customerId: String
        planName: String
        monthlyFee: Money
        status: SubscriptionStatus
        pausedAt: Timestamp
        resumeDate: Timestamp

        invariant FeePositive {
            expression: "monthlyFee != null"
            severity: error
            message: "Monthly fee must be set"
        }
    }

File: subscription/actors-policies.chronos

    namespace com.acme.subscriptions

    @description("End user managing their own subscription")
    actor Subscriber

    @description("Internal support team member")
    actor SupportAgent extends Subscriber

    @compliance("billing-policy")
    policy NoPauseAbuse {
        description: "Subscribers may not pause more than 3 times per year to prevent billing cycle gaming"
    }

File: subscription/errors.chronos

    namespace com.acme.subscriptions

    error PauseNotEligibleError {
        code: "SUB-001"
        severity: medium
        recoverable: false
        message: "Subscription is not eligible for pause"
        payload: {
            reason: String
            pauseCount: Integer
        }
    }

    error ResumeFailedError {
        code: "SUB-002"
        severity: high
        recoverable: true
        message: "Failed to resume subscription"
        payload: {
            billingError: String
        }
    }

File: subscription/events.chronos

    namespace com.acme.subscriptions

    event PauseRequested {}
    event PauseConfirmed {}
    event ResumeRequested {}
    event ResumeConfirmed {}

File: subscription/statemachine.chronos

    namespace com.acme.subscriptions

    use com.acme.subscriptions#Subscription
    use com.acme.subscriptions#SubscriptionStatus

    statemachine SubscriptionLifecycle {
        entity: Subscription
        field: status
        states: [ACTIVE, PAUSED, CANCELLED]
        initial: ACTIVE
        terminal: [CANCELLED]
        transitions: [
            ACTIVE -> PAUSED {
                guard: "pause eligibility confirmed"
                action: "Record pause timestamp; suspend billing"
            },
            PAUSED -> ACTIVE {
                guard: "resume date reached or manual resume"
                action: "Reactivate billing; clear pause timestamp"
            },
            ACTIVE -> CANCELLED,
            PAUSED -> CANCELLED
        ]
    }

File: subscription/journeys.chronos

    namespace com.acme.subscriptions

    use com.acme.subscriptions#Subscriber
    use com.acme.subscriptions#Subscription
    use com.acme.subscriptions#PauseNotEligibleError
    use com.acme.subscriptions#ResumeFailedError
    use com.acme.subscriptions#PauseRequested
    use com.acme.subscriptions#PauseConfirmed
    use com.acme.subscriptions#ResumeRequested
    use com.acme.subscriptions#ResumeConfirmed

    @kpi(metric: "pause_success_rate", target: ">90%")
    journey PauseSubscription {
        actor: Subscriber
        preconditions: [
            "Subscription is in ACTIVE status",
            "Subscriber has not exceeded pause limit"
        ]
        steps: [
            step RequestPause {
                action: "Subscriber requests to pause their subscription"
                expectation: "System checks eligibility and displays pause options with resume date"
                telemetry: [PauseRequested]
            },
            step ConfirmPause {
                action: "Subscriber confirms the pause with selected resume date"
                expectation: "Subscription transitions to PAUSED; billing is suspended"
                outcome: TransitionTo(PAUSED)
                telemetry: [PauseConfirmed]
            }
        ]
        variants: {
            NotEligible: {
                trigger: PauseNotEligibleError
                steps: [
                    step ShowIneligible {
                        action: "System shows why pause is not available"
                        expectation: "Clear message with reason and next eligible date"
                    }
                ]
            }
        }
        outcomes: {
            success: "Subscription is paused; billing suspended; resume date set",
            failure: "Subscription remains active; subscriber sees eligibility error"
        }
    }

    @kpi(metric: "resume_success_rate", target: ">95%")
    journey ResumeSubscription {
        actor: Subscriber
        preconditions: ["Subscription is in PAUSED status"]
        steps: [
            step RequestResume {
                action: "Subscriber requests to resume their subscription"
                expectation: "System validates billing method and shows reactivation summary"
                telemetry: [ResumeRequested]
            },
            step ConfirmResume {
                action: "Subscriber confirms reactivation"
                expectation: "Subscription transitions to ACTIVE; billing resumes"
                outcome: TransitionTo(ACTIVE)
                telemetry: [ResumeConfirmed]
            }
        ]
        variants: {
            BillingFailed: {
                trigger: ResumeFailedError
                steps: [
                    step UpdatePayment {
                        action: "Subscriber updates payment method"
                        expectation: "New payment method is validated and stored"
                    }
                ]
                outcome: ReturnToStep(ConfirmResume)
            }
        }
        outcomes: {
            success: "Subscription is active; billing resumed; confirmation sent",
            failure: "Subscription remains paused; subscriber prompted to update payment"
        }
    }

    deny FraudulentPauseAbuse {
        description: "Accounts flagged for fraud must not be allowed to pause subscriptions"
        scope: [Subscription]
        severity: high
    }

════════════════════════════════════════════════════════════════════════════════
END OF CHRONOS LANGUAGE SPECIFICATION
════════════════════════════════════════════════════════════════════════════════
````

---

# Part 2: Deep Dive — Teaching Claude Code About Chronos

Claude Code supports `CLAUDE.md` files that are loaded automatically into every conversation. This section provides a complete `CLAUDE.md` template that makes Claude Code an expert on Chronos.

## How to Use

1. Copy the template below into a file called `CLAUDE.md` at the root of your project.
2. Every Claude Code session in that project will now have full Chronos language knowledge.
3. Customize the "Project-Specific" section at the bottom for your team's namespaces, libraries, and conventions.

## The CLAUDE.md Template

````markdown
# Chronos Requirements Project

This project uses the Chronos language (.chronos files) for structured software requirements.
Claude must follow this specification when writing or editing .chronos files.

## Chronos Language Specification

### File Structure
```
namespace <qualified.name>       // required, exactly one per file
use <namespace>#<ShapeName>      // zero or more imports
// shape definitions in any order
```

### Primitive Types
String, Integer, Long, Float, Boolean, Timestamp, Blob, Document.
Generic collections: `List<T>`, `Map<K, V>` (inline in field positions).

### Traits
Three forms: `@bare`, `@positional("arg")`, `@named(key: value, key2: value2)`.
Before a field → field trait. Before a shape keyword → shape trait.
Values: "string", 123, 3.14, true/false, duration literal (5m, 30s, 2h, 1d, 1w, 500ms), or qualified name.

### The 15 Shape Types

**entity** — domain object with identity. Supports `extends` (single parent). Can contain fields and entity-scoped invariants.
```chronos
entity Order {
    @required id: String
    total: Float
    invariant PositiveTotal {
        expression: "total > 0"
        severity: error
        message: "Total must be positive"
    }
}
entity PremiumOrder extends Order { loyaltyDiscount: Float }
```

**shape** — value object (no identity): `shape Money { amount: Float  currency: String }`

**list** — named collection: `list Items { member: CartItem }`

**map** — named key-value: `map Tags { key: String  value: String }`

**enum** — named constants (optional integer values):
```chronos
enum Status { PENDING  ACTIVE  CLOSED }
enum Priority { LOW = 1  MEDIUM = 2  HIGH = 3 }
```

**actor** — journey participant: `actor Customer` or `actor Agent extends Customer`

**policy** — governance rule (body is ONLY `description: "..."`):
```chronos
@compliance("GDPR") policy DataRetention { description: "Purge PII on request" }
```

**journey** — end-to-end user flow. MUST have `actor:` and `outcomes:` with `success`.
```chronos
journey Checkout {
    actor: Customer
    preconditions: ["Cart not empty"]
    steps: [
        step Review { action: "Review cart" expectation: "Totals shown" },
        step Pay { action: "Submit payment" expectation: "Order created" outcome: TransitionTo(PAID) }
    ]
    variants: {
        Declined: {
            trigger: PaymentDeclinedError
            steps: [ step Retry { action: "Try again" expectation: "Form shown" } ]
            outcome: ReturnToStep(Review)
        }
    }
    outcomes: { success: "Order paid", failure: "Cart preserved" }
}
```

**relationship** — association between entities:
```chronos
relationship CustomerOrders {
    from: Customer  to: Order  cardinality: one_to_many
    semantics: aggregation   // association | aggregation | composition
    inverse: customerId      // optional
}
```

**invariant** (global) — cross-entity business rule:
```chronos
invariant OrderLimit {
    scope: [Customer, Order]
    expression: "count(Customer.orders, o => o.status == PENDING) <= 10"
    severity: warning    // error | warning | info
    message: "Max 10 pending orders"
}
```

**deny** — prohibition:
```chronos
deny NoPIIInLogs { description: "Never log PII" scope: [Customer] severity: critical }
```
Severity: critical | high | medium | low.

**error** — typed failure case:
```chronos
error PaymentDeclinedError {
    code: "PAY-001"  severity: high  recoverable: true
    message: "Payment declined"
    payload: { reason: String  retryable: Boolean }
}
```
Severity: critical | high | medium | low.

**statemachine** — entity field lifecycle:
```chronos
statemachine OrderLifecycle {
    entity: Order  field: status
    states: [PENDING, PAID, SHIPPED, DELIVERED, CANCELLED]
    initial: PENDING  terminal: [DELIVERED, CANCELLED]
    transitions: [
        PENDING -> PAID { guard: "payment approved" action: "Emit event" },
        PAID -> SHIPPED,
        SHIPPED -> DELIVERED,
        PENDING -> CANCELLED
    ]
}
```

**role** — authorization role: `role Admin { allow: [create, delete] deny: [purge] }`

**event** — telemetry/domain event: `event CartViewed {}` or `event OrderPlaced { orderId: String }`

### Namespace & Imports
- One namespace per file. `use ns#Shape` for cross-namespace refs.
- Same-namespace shapes resolve across files without `use`.
- Multiple files can share a namespace.

### Doc Comments
`///` lines attach to the next declaration. No blank line between `///` and declaration.

### Duration Literals
Unquoted in trait args: 500ms, 5s, 2m, 1h, 7d, 1w.

## Compiler Validation Rules

### Severity Value Rules
- `invariant` severity: `error`, `warning`, or `info`
- `deny` and `error` severity: `critical`, `high`, `medium`, or `low`
- NEVER mix these up — the compiler rejects wrong values.

### Journey Rules
- `actor:` is required (CHR-001)
- `outcomes:` with `success` is required (CHR-002)
- Every step must have `action:` and `expectation:` (CHR-003)
- Variant `trigger:` must reference a declared/imported error type (CHR-027)

### State Machine Rules
- `initial` must be in `states` (CHR-031)
- All transition from/to states must be in `states` (CHR-029)
- Every non-terminal state needs at least one outbound transition (CHR-030)
- Terminal states must NOT have outbound transitions (CHR-032)
- `entity` and `field` must reference a defined entity and field (CHR-033)
- TransitionTo(X) must reference a declared statemachine state (CHR-034)

### Type Resolution
- All field types must be primitives, declared shapes, or imported shapes (CHR-008, CHR-013)
- Cross-namespace types need explicit `use` import (CHR-012, CHR-016)

### Invariant Expression Language
Entity-scoped invariant expressions can reference ONLY direct fields of that entity.
Dot-path navigation (`order.total`) works only in global invariants with scope entities.

The expression parser supports:
- Field references: `fieldName`, `Entity.fieldName`
- Literals: `42`, `3.14`, `"text"`, `true`, `false`, `null`
- Binary operators: `==`, `!=`, `>`, `<`, `>=`, `<=`, `&&`, `||`, `+`, `-`, `*`, `/`
- Unary operators: `!`, `-`
- Aggregate calls with lambdas: `count(target, p => body)`, `sum(...)`, `exists(...)`, `forAll(...)`
- Enum references: `PascalCaseName` (no dot) → treated as enum reference

### Telemetry Events
- Step `telemetry:` array entries must reference declared or imported `event` types (CHR-041)
- Always declare event types before referencing them in steps.

### Step Data Flow
- Output field names must be unique across all steps in a journey (CHR-035)
- Input fields must be produced by a preceding step's output (CHR-036)

### Relationships
- Composition target can only be referenced by ONE composing entity (CHR-048)
- `from:` and `to:` must reference defined entities (CHR-011)

### Inheritance
- Single inheritance only (CHR-018)
- Child cannot redefine parent field with incompatible type (CHR-049)

## Complete Diagnostic Code Reference

Use this table to fix compiler errors. When the compiler emits a CHR-xxx code,
find it here for the cause and fix.

### Errors (must fix)
| Code | Trigger | Fix |
|------|---------|-----|
| CHR-001 | Journey has no `actor:` | Add `actor: ActorName` to journey body |
| CHR-002 | Journey has no `outcomes:` with `success` | Add `outcomes: { success: "..." }` |
| CHR-003 | Step missing `action:` or `expectation:` | Add both fields to every step |
| CHR-005 | Duplicate shape name in namespace | Rename one of the duplicates |
| CHR-008 | Field type references unknown shape | Define the shape or add a `use` import |
| CHR-011 | Relationship from/to references undefined entity | Define the entity or import it |
| CHR-012 | Unresolved symbol after all resolution phases | Check spelling; ensure the target file is compiled |
| CHR-013 | Type name not resolvable during type resolution | Define or import the referenced type |
| CHR-014 | Relationship inverse field not found on target entity | Add the field to the target entity or fix the name |
| CHR-015 | Circular inheritance chain | Break the cycle (A extends B extends A) |
| CHR-016 | Unknown import target | The `use` import points to a shape that doesn't exist |
| CHR-017 | Ambiguous import — same name from different namespaces | Use fully qualified names or rename |
| CHR-018 | Multiple inheritance not supported | Use single `extends` only |
| CHR-019 | Invariant expression references undeclared field | Only reference fields defined on the entity |
| CHR-020 | Invalid invariant severity | Use `error`, `warning`, or `info` |
| CHR-021 | Global invariant has empty scope | Add at least one entity to `scope: [...]` |
| CHR-022 | Duplicate invariant name within scope | Rename the invariant |
| CHR-023 | Deny block missing description | Add `description: "..."` |
| CHR-024 | Deny scope references undefined entity | Define or import the entity |
| CHR-025 | Invalid deny severity | Use `critical`, `high`, `medium`, or `low` |
| CHR-026 | Duplicate error code in namespace | Make error codes unique |
| CHR-027 | Variant trigger references undefined error type | Define or import the error type |
| CHR-028 | Invalid error severity | Use `critical`, `high`, `medium`, or `low` |
| CHR-029 | Transition references state not in `states` list | Add the state to `states: [...]` |
| CHR-030 | Non-terminal state has no outbound transition | Add a transition from that state or make it terminal |
| CHR-031 | Initial state not in states list | Add initial state to `states: [...]` |
| CHR-032 | Terminal state has outbound transitions | Remove transitions from terminal states or un-terminal it |
| CHR-033 | Statemachine entity/field not defined | Define the entity and ensure it has the named field |
| CHR-034 | TransitionTo() references unknown state | Use a state declared in a statemachine |
| CHR-035 | Duplicate output field name across steps | Rename the duplicate output field |
| CHR-036 | Step input not produced by preceding output | Add the field as output in an earlier step |
| CHR-037 | @authorize role not declared | Define a `role` with that name |
| CHR-038 | @authorize permission not in role's allow list | Add the permission to the role's `allow` |
| CHR-039 | Journey actor missing required @authorize | Add `@authorize(role: X)` to the actor |
| CHR-040 | @authorize permission is in role's deny list | Remove from deny or use a different permission |
| CHR-041 | Step telemetry event not declared/imported | Declare the event or add a `use` import |
| CHR-042 | Invariant expression parse failure | Fix the expression syntax |
| CHR-044 | Statemachine state not in bound enum | Add the state to the enum or remove from statemachine |
| CHR-046 | TransitionTo() target ambiguous (multiple machines) | Ensure the state name is unique across statemachines |
| CHR-048 | Composition target has multiple owners | Only one entity can compose a given target |
| CHR-049 | Child redefines parent field incompatibly | Match the parent's field type or remove the override |
| CHR-050 | Invalid duration in @timeout/@ttl | Use format: 5m, 30s, 2h, 1d, etc. |
| CHR-051 | @timeout onExpiry references unknown variant | Define the variant in the journey's variants block |
| CHR-052 | @ttl action invalid | Use: delete, archive, or notify |
| CHR-053 | @schedule cron invalid | Use valid 5-field cron: "0 0 * * 1" |

### Warnings (should fix)
| Code | Trigger | Fix |
|------|---------|-----|
| CHR-004 | Journey has zero happy-path steps | Add at least one step |
| CHR-006 | Entity/shape has no fields | Add fields or remove if unused |
| CHR-007 | Actor missing @description | Add `@description("...")` |
| CHR-009 | Journey missing @kpi | Add `@kpi(metric: "...", target: "...")` |
| CHR-010 | Journey missing @compliance | Add `@compliance("...")` if applicable |
| CHR-043 | Type mismatch in invariant expression | Fix the expression types |
| CHR-045 | Enum member not covered by statemachine | Add a state for the enum member or accept the gap |
| CHR-047 | TransitionTo() target has no incoming transitions | Add a transition to that state in the statemachine |
| CHR-W001 | Optional field used without null guard | Add null check: `field != null && ...` |

## Language Gotchas

1. **`description` is a keyword** — cannot be used as a field name in entity/shape bodies. Use `summary` instead.
2. **Entity invariant scope** — expressions can only reference DIRECT fields of the entity. `price.amount > 0` won't work; use a top-level Float field or a global invariant.
3. **Global invariant scope** — must reference entities in the same namespace/compilation unit.
4. **`outcomes` is required** on journeys — omitting it triggers CHR-002.
5. **Same-namespace imports** — within the same namespace you don't need `use`, but across namespaces you always do.
6. **Severity split** — invariants use error/warning/info; deny and error blocks use critical/high/medium/low. This is the most common mistake.

## CLI Commands

```bash
# Compile and generate PRD from a directory of .chronos files
chronos prd <file-or-directory> [--output <outDir>] [--name <docName>]

# Generate to a specific target
chronos generate --target <target> <file-or-directory>
# Targets: prd, markdown, jira, typescript, mermaid-state, test-scaffold, statemachine-tests

# Validate only (no generation)
chronos validate <file-or-directory>

# Build from chronos.build config
chronos build [--config <path>]
```

## Authoring Conventions

- One namespace per file.
- Multi-file layout: domain, actors-policies, journeys, errors, statemachines, events.
- Doc comments (`///`) above every major declaration.
- Error names end with `Error`.
- Journey names use verb phrases (e.g., `CheckoutJourney`, `PauseSubscription`).
- Invariant names describe the rule (e.g., `PositiveTotal`, `ActiveOrderLimit`).
- Namespace convention: `com.org.domain.context`.
- After writing files, self-check against validation rules before presenting.

## Project-Specific Configuration
<!-- Customize this section for your team -->

Namespace root: <!-- e.g., com.acme.subscriptions -->
Shared libraries to import:
<!-- e.g.,
  - com.acme.common#Money
  - com.acme.common#Address
  - com.acme.security#PiiAccessPolicy
-->
Required compliance tags: <!-- e.g., GDPR, PCI-DSS, SOX -->
Model directory: <!-- e.g., src/requirements/ -->
````

---

# Part 3: Workflow

## Fast Start

1. Start with a bounded context and target namespace.
2. Ask the bot to generate 4-6 files:
   - `domain.chronos` — entities, shapes, enums, relationships
   - `actors-policies.chronos` — actors, policies
   - `journeys.chronos` — journeys with variants
   - `errors.chronos` — error types
   - `statemachines.chronos` — lifecycle definitions
   - `events.chronos` — telemetry event types
3. Compile and validate:

```bash
chronos prd /path/to/model-dir --name draft --output /tmp/chronos-draft
```

4. If diagnostics appear, paste them back to the bot and ask for targeted fixes only.
5. Repeat until compilation succeeds.
6. Generate final artifacts.

## Prompt Templates

### Bootstrap a New Feature

```text
Create Chronos requirements for this feature.

Feature brief:
- Problem:
- Primary user/actor:
- Business outcome and KPI:
- In-scope behaviors:
- Out-of-scope:
- Compliance/security requirements:
- Existing systems/APIs involved:
- Known failure scenarios:

Authoring requirements:
- Generate files under: <path>
- Namespace root: <namespace>
- Reuse/import existing shapes from: <library namespaces>
- Include at minimum:
  - 2+ entities or shapes
  - 1+ actor (with @description)
  - 1+ policy (if applicable)
  - 1+ journey with variants
  - typed errors for variant triggers
  - deny rules for prohibited behavior
  - event types for step telemetry
  - statemachine if entity has a lifecycle
- Make invariants realistic and testable.
- Keep expressions simple and unambiguous.

Quality bar:
- Must compile with Chronos without errors.
- Warnings are acceptable but should be minimized.
- If assumptions are needed, state them as `/// Assumption: ...`.
```

### Fix Compiler Diagnostics

```text
Apply only the minimum edits needed to fix these Chronos diagnostics.
Do not redesign names or structure unless required.
Refer to the diagnostic code table for the cause and fix.

<paste diagnostic output here>
```

### Add Failure Modeling

```text
Improve failure completeness:
- Add explicit `error` types for each journey variant trigger.
- Ensure each variant has trigger, recovery path, and concrete expectations.
- Add at least one `deny` rule preventing data or authorization misuse.
- Add `event` types for any step telemetry references.
Return only diffs by file.
```

### Add a State Machine

```text
Add a `statemachine` for entity `<EntityName>` on field `<fieldName>`.
States should include: <list states>.
Ensure the enum for the field includes all states.
Ensure transitions satisfy: non-terminal states have outbound transitions,
terminal states do not. Update any journey TransitionTo() references.
```

### Optimize for Code Generation

```text
Optimize these Chronos requirements for downstream code generation:
- remove vague wording from action/expectation strings
- ensure every step has deterministic expected outcomes
- make invariants explicit and implementation-testable
- ensure error payloads include fields needed for retries and observability
- add input/output data flow to journey steps where applicable
Return updated .chronos files only.
```

## Diagnostic-Driven Correction Loop

Use this loop every time:

1. Generate or update Chronos files with the bot.
2. Compile:

```bash
chronos prd /path/to/model-dir --name draft --output /tmp/chronos-draft
```

3. If errors exist, paste the diagnostic output into a correction prompt.
4. Ask for minimum-change fixes (not a full rewrite).
5. Re-compile.
6. Stop only when errors are zero. Warnings are acceptable but should be reviewed.

**For Claude Code users**: Claude Code can run the compiler directly and read the output. Just say "compile the .chronos files in `<dir>` and fix any errors" and it will loop automatically.

## Common Mistakes

| Mistake | Why It Fails | Fix |
|---------|-------------|-----|
| Omitting `outcomes:` on a journey | CHR-002 | Always add `outcomes: { success: "...", failure: "..." }` |
| Using `error` severity on a `deny` block | CHR-025 | Deny uses `critical/high/medium/low` |
| Using `critical` severity on an `invariant` | CHR-020 | Invariant uses `error/warning/info` |
| Referencing an error in variant trigger without defining it | CHR-027 | Define the `error` type in the same namespace or import it |
| Using `description` as a field name | Parse error | `description` is a keyword; use `summary` instead |
| Dot-path in entity invariant expression | CHR-019 | Entity invariants can only reference direct fields |
| TransitionTo() with state not in any statemachine | CHR-034 | Declare the state in a statemachine's states list |
| Referencing events in telemetry without declaring them | CHR-041 | Add `event EventName {}` declarations |
| Forgetting `actor:` on a journey | CHR-001 | Add `actor: ActorName` |
| Terminal state with outbound transitions | CHR-032 | Remove the transition or un-terminal the state |
| Missing `use` import for cross-namespace type | CHR-012 | Add `use other.namespace#TypeName` |
| Enum member not matching statemachine states | CHR-044 | Keep enum members and statemachine states in sync |

## Team Customization Questions

1. Which AI bot(s) are you standardizing on? (Determines whether you need the CLAUDE.md template or the general system prompt.)
2. Do you want single-file or multi-file output by default?
3. Which governance libraries are mandatory (security/compliance/privacy)?
4. Should the bot ask clarifying questions first, or draft immediately with assumptions?
5. Should generated requirements target human PRD readability or LLM codegen precision?
6. What naming conventions does your team use for namespaces, entities, and journeys?

## Recommended Next Step

Pilot this workflow on one medium-complexity feature and track:

- Time to first valid compile
- Number of correction loops
- Requirement clarification requests from engineers
- Defects traced to requirement ambiguity

Use those metrics to tune your prompt templates and library strategy before scaling org-wide.

---

## Contributing to Chronos

Want to extend the language, add a generator, or fix a bug?

See the [Contributing Guide](contributing.md) for source build instructions, module layout, the ANTLR grammar location, and how to run the full test suite.
