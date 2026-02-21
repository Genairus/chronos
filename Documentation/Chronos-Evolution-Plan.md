  
**Chronos Language Evolution Plan**

Requirements and Implementation Roadmap

Prepared for Coding Agent  |  Pre-Release (No Backward Compat)  |  February 2026

# **Executive Summary**

This document specifies the requirements and phased implementation plan for the Chronos requirements language. Chronos is pre-release and has not shipped to users, so there are no backward compatibility constraints. The grammar, AST, validation rules, and internal representations can be freely restructured at any point. This is a greenfield evolution: every construct described here should be implemented as the canonical form from day one, with no legacy fallbacks or deprecation paths.

The plan is organized into six phases, ordered so that each phase builds on the foundations laid by the previous one. Foundational structural changes (entity relationships, inheritance) come first because nearly everything else depends on a richer type graph. Constraints and rules come second. Behavioral enrichment (state machines, data flow, authorization) comes third. Temporal and reactive constructs fourth. Concurrency and composition fifth. Interface-layer constructs last, since they are the most domain-specific. Since there are no existing users, phases can be parallelized or reordered based on implementation convenience.

Each feature specification includes: the requirement statement, proposed syntax examples, new validation rules, implementation tasks, and acceptance criteria. All proposed syntax is illustrative and may be adjusted during implementation, but the semantics described are normative. All validation rules are hard errors unless explicitly marked otherwise — there is no need for soft warnings to ease migration.

# **Phased Roadmap Overview**

The table below summarizes all six phases and their constituent features. Each row is a discrete unit of work that can be implemented, tested, and released independently within its phase.

| Phase | Feature | What It Adds |
| :---- | :---- | :---- |
| **Phase 1: Structural Foundations** |  |  |
| 1.1 | Entity Relationships | Associations, aggregations, compositions with cardinality |
| 1.2 | Inheritance | extends keyword for entities and actors |
| **Phase 2: Constraint and Rule System** |  |  |
| 2.1 | Cross-Entity Invariants | Named boolean constraints spanning fields and entities |
| 2.2 | Negative Requirements | deny construct for shall-not prohibitions |
| 2.3 | Error Taxonomy | Typed error definitions with codes and payloads |
| **Phase 3: Behavioral Enrichment** |  |  |
| 3.1 | Formal State Machines | Declared states, transitions, guards, actions |
| 3.2 | Step Data Flow | Typed inputs/outputs on journey steps |
| 3.3 | Authorization Model | Roles, permissions, @authorize trait |
| **Phase 4: Temporal and Reactive Constructs** |  |  |
| 4.1 | Temporal Requirements | Timeouts, TTLs, scheduling, duration literals |
| 4.2 | Events and Reactions | Typed events, reactive flows, system-initiated triggers |
| **Phase 5: Concurrency and Composition** |  |  |
| 5.1 | Parallel Flows | parallel blocks with fork/join semantics |
| 5.2 | Journey Composition | @requires dependencies, invoke sub-journeys |
| 5.3 | Non-Functional Requirements | Availability, throughput, capacity, recoverability |
| **Phase 6: Interface and Presentation Layer** |  |  |
| 6.1 | API Contracts | Request/response declarations, OpenAPI generation |
| 6.2 | UI/UX Requirements | Views, accessibility, responsive constraints |
| **Cross-Cutting: Documentation Generation System** |  |  |
| D.1 | Grammar Doc Extractor | Auto-generate syntax reference from ANTLR .g4 files |
| D.2 | Semantic Doc Extractor | Auto-generate trait/rule/type references from compiler internals |
| D.3 | Railroad Diagram Generator | Visual syntax diagrams from parser rules |
| D.4 | Unified Doc Pipeline | Gradle task integrating all extractors into chronos build |

## **Phase 1: Structural Foundations**

These changes extend the shape system with relationship semantics and type hierarchies. They are foundational because every subsequent phase depends on a richer entity graph. Since this is pre-release, the AST and internal type representations can be designed from scratch to accommodate relationships and inheritance natively rather than bolting them on.

### **1.1  Entity Relationships and Cardinality**

**Requirement:** Introduce first-class relationship declarations between entities, supporting association, aggregation, and composition semantics with explicit cardinality constraints (one-to-one, one-to-many, many-to-many).

Proposed syntax for relationship declarations within entity bodies:

entity Customer {  
    @required  
    id: String  
    name: String

    // Association with cardinality  
    @relationship(type: "has\_many")  
    orders: List\<Order\>

    // Composition (lifecycle-dependent)  
    @relationship(type: "composes")  
    profile: CustomerProfile  
}

// Alternatively, standalone relationship block:  
relationship CustomerOrders {  
    from: Customer  
    to: Order  
    cardinality: one\_to\_many  
    semantics: association  // association | aggregation | composition  
    inverse: "customer"  
}

**Validation Rules:**

* CHR-011: Relationship targets must reference defined or imported entities  
* CHR-012: Composition targets cannot be referenced by more than one composing entity  
* CHR-013: Cardinality values must be one of: one\_to\_one, one\_to\_many, many\_to\_many  
* CHR-014: Inverse field name (if specified) must exist on the target entity

**Implementation Tasks:**

1. Add relationship keyword and @relationship trait to the lexer/parser  
2. Extend the type-checker to validate cardinality and target resolution  
3. Add composition lifecycle validation (single-owner constraint)  
4. Update artifact generators to emit relationship metadata in diagrams and docs  
5. Add inverse-field cross-validation

**Acceptance Criteria:**

* A .chronos file declaring entity relationships parses and validates without error  
* Circular compositions are rejected with a clear error message  
* Generated class diagrams render associations with correct cardinality notation  
* Referencing an undefined entity in a relationship produces CHR-008

### **1.2  Inheritance and Generalization**

**Requirement:** Allow entities and actors to extend a parent type using an extends keyword. Child types inherit all fields, traits, and relationships of the parent and may add or override fields.

Proposed syntax:

entity User {  
    @required  
    id: String  
    email: String  
}

entity PremiumUser extends User {  
    tier: PremiumTier  
    rewardsBalance: Integer  
}

actor AuthenticatedUser  
actor AdminUser extends AuthenticatedUser

**Validation Rules:**

* CHR-015: Circular inheritance chains are a validation error
* CHR-016: A child entity may not redefine a parent field with an incompatible type
* CHR-018: Multiple inheritance is not supported; use shape composition instead

**Implementation Tasks:**

1. Add extends keyword to the parser for entity and actor declarations  
2. Implement field-merging logic in the type-checker (parent fields \+ child fields)  
3. Add trait propagation with override semantics  
4. Validate no circular inheritance and no multiple inheritance  
5. Update all generators to resolve the full field set through the hierarchy

**Acceptance Criteria:**

* A child entity inherits all parent fields and traits  
* Overriding a parent field with a compatible subtype succeeds  
* Overriding with an incompatible type produces CHR-016  
* Circular extends produces CHR-015  
* Generated artifacts reflect the full merged field set

## **Phase 2: Constraint and Rule System**

With a richer entity graph from Phase 1, the language now needs the ability to express business rules that span fields and entities. This phase introduces invariants, negative requirements, and typed errors, giving Chronos the ability to validate domain logic, not just domain structure. Variant triggers can require typed error references outright since there is no existing usage to migrate.

### **2.1  Cross-Entity Invariants**

**Requirement:** Introduce an invariant block that expresses boolean constraints across fields and entities. Invariants are named, attached to an entity or declared globally, and are always-true propositions that the system must enforce.

Proposed syntax:

entity Order {  
    id: String  
    items: List\<OrderItem\>  
    total: Money  
    shipDate: Timestamp  
    orderDate: Timestamp

    invariant TotalMatchesItems {  
        expression: "total.amount \== sum(items, i \=\> i.unitPrice.amount \* i.quantity)"  
        severity: error  
    }

    invariant ShipAfterOrder {  
        expression: "shipDate \> orderDate"  
        severity: error  
        message: "Ship date must be after order date"  
    }  
}

// Global invariant spanning entities  
invariant ActiveOrderLimit {  
    scope: \[Customer, Order\]  
    expression: "count(customer.orders, o \=\> o.status \== PENDING) \<= 10"  
    severity: warning  
    message: "Customer should not exceed 10 pending orders"  
}

**Validation Rules:**

* CHR-019: Invariant expressions must reference only fields visible in scope  
* CHR-020: Severity must be one of: error, warning, info  
* CHR-021: Global invariants must declare a scope listing all referenced entities  
* CHR-022: Invariant names must be unique within their enclosing scope

**Implementation Tasks:**

1. Design and document the expression micro-language (comparison operators, arithmetic, aggregations: sum, count, min, max, exists, forAll)  
2. Add invariant keyword to the parser (entity-scoped and global)  
3. Implement expression parsing and type-checking against resolved entity fields  
4. Generate invariant documentation in PRDs and test scaffolding as assertions  
5. Emit validation warnings for invariants referencing optional fields without null guards

**Acceptance Criteria:**

* Entity-scoped invariants parse and validate against local fields  
* Global invariants validate scope declarations against defined entities  
* Referencing an undefined field in an expression produces CHR-019  
* Generated test scaffolding includes assertion stubs for each invariant

### **2.2  Negative Requirements (shall\_not)**

**Requirement:** Introduce a deny construct for expressing prohibitions: things the system must never do. Denials are first-class, named, validatable, and generate negative test cases.

Proposed syntax:

deny StorePlaintextPasswords {  
    description: "The system must never store passwords in plaintext"  
    scope: \[UserCredential\]  
    severity: critical  
}

deny ExposePIIInLogs {  
    @compliance("GDPR")  
    description: "PII-annotated fields must never appear in application logs"  
    scope: \[CustomerProfile, PaymentInfo\]  
    severity: critical  
}

**Validation Rules:**

* CHR-023: Every deny must include a description  
* CHR-024: Scope entities must be defined or imported  
* CHR-025: Severity must be one of: critical, high, medium, low

**Implementation Tasks:**

1. Add deny keyword to the parser  
2. Implement scope resolution and validation  
3. Generate negative test case stubs (e.g., assert that the prohibited condition never holds)  
4. Include deny items in compliance traceability reports  
5. Add deny blocks to generated PRD sections under a Prohibitions heading

**Acceptance Criteria:**

* deny blocks parse and validate correctly  
* Generated test scaffolding includes negative test stubs for each deny  
* Compliance-tagged denials appear in compliance traceability output  
* Referencing undefined entities in scope produces CHR-008

### **2.3  Error and Exception Taxonomy**

**Requirement:** Introduce a typed error construct so that error conditions in variants and outcomes are structured, not ad-hoc strings. Errors have codes, severity, recoverability, and optional payloads.

Proposed syntax:

error PaymentDeclinedError {  
    code: "PAY-001"  
    severity: high  
    recoverable: true  
    message: "Payment was declined by the gateway"  
    payload: {  
        gatewayCode: String  
        retryable: Boolean  
    }  
}

// Usage in variants:  
variants: {  
    PaymentDeclined: {  
        trigger: PaymentDeclinedError  
        steps: \[ ... \]  
    }  
}

**Validation Rules:**

* CHR-026: Error codes must be unique across the namespace  
* CHR-027: Variant triggers must reference a defined error type (string triggers are not supported)  
* CHR-028: Error severity must be one of: critical, high, medium, low

**Implementation Tasks:**

1. Add error keyword to the parser with code, severity, recoverable, message, and payload fields  
2. Require variant triggers to reference named error types (no string-based triggers)  
3. Generate error catalog documentation from all defined errors  
4. Include error payload shapes in API stub generation  
5. Validate error code uniqueness within namespace

**Acceptance Criteria:**

* Named error types parse and validate  
* Variants with string-based triggers produce CHR-027  
* Variants referencing defined error types resolve correctly  
* Duplicate error codes within a namespace produce CHR-026  
* Generated docs include an error catalog with codes, descriptions, and payloads

## **Phase 3: Behavioral Enrichment**

With structure and constraints in place, this phase enriches the behavioral layer. Formal state machines replace implicit TransitionTo states, steps gain typed data flow, and authorization rules make actor permissions explicit. Since no journeys exist yet in production, the step grammar can be restructured to include input/output blocks as first-class fields rather than optional add-ons.

### **3.1  Formal State Machines**

**Requirement:** Introduce a statemachine construct that explicitly declares states, transitions, guard conditions, and entry/exit actions. State machines are linked to entities (typically via a status enum) and can be referenced from journey steps.

Proposed syntax:

statemachine OrderLifecycle {  
    entity: Order  
    field: status

    states: \[PENDING, PAID, SHIPPED, DELIVERED, CANCELLED\]  
    initial: PENDING  
    terminal: \[DELIVERED, CANCELLED\]

    transitions: \[  
        PENDING \-\> PAID {  
            guard: "payment.status \== APPROVED"  
            action: "Emit OrderPaidEvent"  
        },  
        PENDING \-\> CANCELLED {  
            guard: "cancellation requested by actor OR payment timeout exceeded"  
        },  
        PAID \-\> SHIPPED {  
            guard: "fulfillment.status \== DISPATCHED"  
            action: "Emit OrderShippedEvent"  
        },  
        SHIPPED \-\> DELIVERED {  
            guard: "delivery confirmation received"  
        }  
    \]  
}

**Validation Rules:**

* CHR-029: All states referenced in transitions must be declared in the states list  
* CHR-030: Every non-terminal state must have at least one outbound transition  
* CHR-031: The initial state must be in the states list  
* CHR-032: Terminal states must not have outbound transitions  
* CHR-033: The referenced entity and field must be defined; field type should be an enum

**Implementation Tasks:**

1. Add statemachine keyword and transition syntax to the parser  
2. Validate state declarations, initial/terminal markers, and transition completeness  
3. Cross-reference entity field type against declared states (should match enum members)  
4. Generate state diagrams (Mermaid or PlantUML) from statemachine declarations  
5. Generate transition-coverage test stubs  
6. Allow journey steps to reference statemachine transitions for consistency checking

**Acceptance Criteria:**

* A valid statemachine parses and generates a correct state diagram  
* Referencing an undeclared state in a transition produces CHR-029  
* A non-terminal state with no outbound transition produces CHR-030  
* TransitionTo() in journey steps can be validated against declared statemachine transitions

### **3.2  Step Data Flow**

**Requirement:** Allow journey steps to declare typed inputs and outputs, enabling validation that data produced by one step is available to subsequent steps.

Proposed syntax:

step ProvideShipping {  
    action: "Submits shipping address form"  
    input: {  
        rawAddress: Address  
    }  
    expectation: "System validates address and calculates shipping methods"  
    output: {  
        validatedAddress: Address  
        shippingOptions: List\<ShippingMethod\>  
    }  
    outcome: TransitionTo(ShippingMethodsDisplayed)  
},

step ChooseShipping {  
    action: "Selects a shipping method"  
    input: {  
        selected: ShippingMethod  // must come from prior output  
    }  
    expectation: "System applies shipping cost to order total"  
    output: {  
        updatedTotal: Money  
    }  
}

**Validation Rules:**

* CHR-034: Input types must reference defined shapes, entities, or primitives  
* CHR-035: A step input type must appear in a preceding step output or precondition  
* CHR-036: Output field names must be unique within the journey scope

**Implementation Tasks:**

1. Add optional input and output blocks to the step grammar  
2. Implement type resolution for input/output fields against defined shapes  
3. Add data-flow validation: reject if an input type has no matching upstream output  
4. Include input/output signatures in generated work item descriptions  
5. Generate data-flow diagrams showing how data moves through a journey

**Acceptance Criteria:**

* Steps with input/output blocks parse and validate  
* A step referencing an undefined type in input produces CHR-034  
* A step whose input type has no upstream source produces error CHR-035  
* Generated artifacts include data-flow documentation

### **3.3  Authorization and Permissions**

**Requirement:** Introduce a permission construct for declaring what actors are allowed and denied to do, and a role construct for grouping permissions. Permissions can be attached to journeys, steps, or entities.

Proposed syntax:

role Admin {  
    permissions: \[  
        allow ManageUsers,  
        allow IssueRefunds,  
        allow ViewReports  
    \]  
}

role Customer {  
    permissions: \[  
        allow ViewOwnOrders,  
        allow CancelOwnOrder,  
        deny ViewOtherCustomerData  
    \]  
}

// Usage on journeys and steps:  
@authorize(role: "Admin")  
journey IssueRefund { ... }

step DeleteAccount {  
    @authorize(role: "Admin", permission: "ManageUsers")  
    action: "Clicks delete on a user account"  
    ...  
}

**Validation Rules:**

* CHR-037: Roles referenced in @authorize must be defined or imported  
* CHR-038: Permissions referenced in @authorize must be declared in the role  
* CHR-039: A journey actor must hold a role compatible with all @authorize annotations in that journey  
* CHR-040: deny permissions take precedence over allow at the same specificity level

**Implementation Tasks:**

1. Add role and permission keywords to the parser  
2. Add @authorize trait with role and permission parameters  
3. Implement cross-validation: journey actor vs. required roles  
4. Generate authorization matrix documentation (actor x permission grid)  
5. Include authorization checks in generated test scaffolding

**Acceptance Criteria:**

* role and permission declarations parse and validate  
* @authorize on a journey with an incompatible actor produces CHR-039  
* Generated docs include an actor-permission authorization matrix  
* deny permissions override allow in the resolved permission set

## **Phase 4: Temporal and Reactive Constructs**

Many real-world requirements are time-bound or event-driven. This phase introduces the constructs needed to express timeouts, deadlines, scheduled triggers, event subscriptions, and system-initiated flows. It builds on the state machines and entity graph from earlier phases. Telemetry fields can require typed event references from the start — no need to support bare name strings alongside typed references.

### **4.1  Temporal and Time-Based Requirements**

**Requirement:** Introduce temporal constraint annotations for steps, states, and entities. Support timeouts, deadlines, scheduling windows, and time-bounded state constraints.

Proposed syntax:

step AwaitPaymentConfirmation {  
    action: "System waits for payment gateway callback"  
    expectation: "Payment confirmation is received"  
    @timeout(duration: "15m", onExpiry: TransitionTo(PaymentTimedOut))  
}

// Time-bounded state in a statemachine:  
PENDING \-\> AUTO\_CANCELLED {  
    guard: "stateAge(PENDING) \> 30d"  
    action: "Emit OrderAutoCancelledEvent"  
}

// Entity-level temporal constraint:  
entity UnverifiedAccount {  
    @ttl(duration: "30d", action: "delete")  
    createdAt: Timestamp  
}

// Scheduling window:  
@schedule(cron: "0 0 \* \* \*", timezone: "UTC")  
trigger PurgeExpiredSessions {  
    description: "Delete sessions older than 24 hours"  
    action: "Delete all Session where lastActivity \< now() \- 24h"  
}

**Validation Rules:**

* CHR-041: @timeout duration must be a valid duration literal (e.g., 15m, 2h, 30d)  
* CHR-042: @timeout onExpiry must reference a valid state or variant  
* CHR-043: @ttl action must be one of: delete, archive, notify  
* CHR-044: @schedule cron expressions must be valid 5-field cron syntax

**Implementation Tasks:**

1. Define duration literal syntax and add to the lexer (ms, s, m, h, d)  
2. Add @timeout trait with duration and onExpiry parameters  
3. Add @ttl trait for entity-level time-to-live  
4. Add trigger keyword with @schedule trait and cron support  
5. Validate temporal references against state machines and variants  
6. Generate SLA documentation from temporal constraints

**Acceptance Criteria:**

* Duration literals parse correctly across all units  
* @timeout on a step validates onExpiry against defined states/variants  
* Invalid cron expressions produce CHR-044  
* Generated docs include an SLA/timeout summary table

### **4.2  Events, Reactions, and System-Initiated Flows**

**Requirement:** Promote events from simple telemetry annotations to first-class typed constructs with payloads. Introduce a reaction construct for event-driven flows and a trigger construct for system-initiated processes that have no human actor.

Proposed syntax:

event OrderPlacedEvent {  
    payload: {  
        orderId: String  
        customerId: String  
        total: Money  
        timestamp: Timestamp  
    }  
}

// Reactive flow: triggered by an event, no human actor  
reaction OnOrderPlaced {  
    trigger: OrderPlacedEvent  
    actor: FulfillmentService  // system actor

    steps: \[  
        step ReserveInventory {  
            action: "Reserves inventory for all order items"  
            expectation: "Inventory decremented or backorder created"  
            output: { reservation: InventoryReservation }  
        },  
        step NotifyWarehouse {  
            action: "Sends pick-pack request to warehouse system"  
            expectation: "Warehouse acknowledges receipt"  
            telemetry: \[WarehouseNotifiedEvent\]  
        }  
    \]

    outcomes: {  
        success: "Inventory reserved and warehouse notified",  
        failure: "Backorder created and customer notified of delay"  
    }  
}

**Validation Rules:**

* CHR-045: Telemetry fields must reference defined event types (bare name strings are not supported)  
* CHR-046: Reaction triggers must reference a defined event type  
* CHR-047: Reactions must declare an actor (system or human)  
* CHR-048: Event payload fields follow the same type rules as entity fields

**Implementation Tasks:**

1. Add event keyword with payload block to the parser  
2. Require telemetry fields to reference typed event definitions (no bare name strings)  
3. Add reaction keyword (structurally similar to journey but triggered by event)  
4. Validate event references across journeys, reactions, and state machine actions  
5. Generate event catalog documentation with payload schemas  
6. Generate event-flow diagrams showing publish/subscribe chains

**Acceptance Criteria:**

* Typed event definitions parse with payload validation  
* Telemetry references to undefined events produce error CHR-045  
* Reactions parse and validate like journeys but require a trigger instead of preconditions  
* Generated docs include a full event catalog and event-flow diagram

## **Phase 5: Concurrency and Composition**

With rich behavioral constructs in place, this phase addresses the remaining structural gaps: parallel execution within journeys, dependencies between journeys, and full non-functional requirement coverage. These integrate naturally into the journey grammar established in earlier phases.

### **5.1  Concurrency and Parallel Flows**

**Requirement:** Introduce a parallel block within journey steps that declares concurrent execution, fork/join semantics, and optional race conditions.

Proposed syntax:

steps: \[  
    step CollectInfo { ... },

    parallel ValidateAll {  
        join: all  // all | any | n\_of(2)  
        branches: \[  
            step ValidatePayment {  
                action: "System validates payment with gateway"  
                expectation: "Payment authorized"  
            },  
            step CheckInventory {  
                action: "System checks inventory availability"  
                expectation: "All items in stock"  
            },  
            step ScreenFraud {  
                action: "System runs fraud detection"  
                expectation: "Transaction cleared"  
            }  
        \]  
    },

    step ConfirmOrder { ... }  
\]

**Validation Rules:**

* CHR-049: parallel blocks must contain at least two branches  
* CHR-050: Join strategy must be one of: all, any, n\_of(N) where N \< branch count  
* CHR-051: Branches within a parallel block must not declare data dependencies on each other  
* CHR-052: Each branch follows all existing step validation rules

**Implementation Tasks:**

1. Add parallel keyword with join and branches to the step grammar  
2. Validate branch independence (no cross-branch data-flow references)  
3. Update data-flow analysis to treat all branch outputs as available after the parallel block  
4. Generate parallel-aware state diagrams with fork/join notation  
5. Generate concurrent test scaffolding

**Acceptance Criteria:**

* parallel blocks parse and validate with all join strategies  
* A parallel block with one branch produces CHR-049  
* Cross-branch data dependencies produce CHR-051  
* Generated diagrams show fork/join correctly

### **5.2  Inter-Journey Dependencies and Composition**

**Requirement:** Allow journeys to declare dependencies on other journeys and to compose sub-journeys as reusable steps.

Proposed syntax:

journey PlaceOrder {  
    @requires(journey: UserRegistration)  
    @requires(journey: AddToCart)  
    actor: Customer  
    ...  
}

// Inline sub-journey reference:  
steps: \[  
    step VerifyIdentity {  
        invoke: IdentityVerificationJourney  
        onSuccess: TransitionTo(IdentityConfirmed)  
        onFailure: TransitionTo(VerificationFailed)  
    },  
    step ContinueCheckout { ... }  
\]

**Validation Rules:**

* CHR-053: @requires targets must reference defined journeys  
* CHR-054: Circular journey dependencies are a validation error  
* CHR-055: invoke must reference a defined journey; its outcomes map to the invoking step  
* CHR-056: An invoked journey actor must be compatible with the parent journey actor

**Implementation Tasks:**

1. Add @requires trait for journey-level dependency declaration  
2. Add invoke keyword to step grammar  
3. Build dependency graph and validate for cycles  
4. Validate actor compatibility between parent and invoked journeys  
5. Generate journey dependency diagrams  
6. Generate end-to-end test scaffolding that respects dependency ordering

**Acceptance Criteria:**

* @requires references validate against defined journeys  
* Circular dependencies produce CHR-054  
* invoke steps map sub-journey outcomes to parent step transitions  
* Generated dependency diagram is accurate and acyclic

### **5.3  Expanded Non-Functional Requirements**

**Requirement:** Extend the trait system with dedicated constructs for availability, throughput, scalability, capacity, and recoverability requirements. These attach at the journey, entity, or system level.

Proposed syntax:

@availability(target: "99.95%", measurement: "monthly")  
@throughput(target: "5000 req/s", sustained: true)  
@capacity(users: "1M concurrent", storage: "500TB")  
@recoverability(rpo: "1h", rto: "4h")  
journey PlaceOrder { ... }

// System-level NFR block:  
nfr PlatformReliability {  
    availability: "99.99%"  
    rpo: "15m"  
    rto: "1h"  
    throughput: "10000 req/s"  
    scalability: "horizontal, auto-scaling"  
    scope: \[PlaceOrder, GuestCheckout, UserRegistration\]  
}

**Validation Rules:**

* CHR-057: Availability targets must be valid percentages  
* CHR-058: RPO and RTO must be valid duration literals  
* CHR-059: NFR scope must reference defined journeys or entities  
* CHR-060: Throughput targets must include a rate unit

**Implementation Tasks:**

1. Add @availability, @throughput, @capacity, @recoverability traits  
2. Add nfr keyword for system-level non-functional requirement blocks  
3. Validate all parameter formats (percentages, durations, rates)  
4. Generate NFR summary tables in PRD output  
5. Include NFR targets in test scaffolding as performance test baselines

**Acceptance Criteria:**

* All new NFR traits parse and validate with correct parameter formats  
* nfr blocks scope-resolve against defined journeys  
* Invalid percentage or duration formats produce appropriate errors  
* Generated PRDs include an NFR summary section

## **Phase 6: Interface and Presentation Layer**

The final phase addresses the outermost layer: API contracts and UI/UX requirements. These are the most domain-specific and opinionated constructs, so they are introduced last to avoid over-constraining the language early. They build naturally on the typed entities, events, and authorization model from earlier phases.

### **6.1  Interface and API Contracts**

**Requirement:** Introduce an api construct for declaring request/response contracts, HTTP semantics, error responses, and versioning. This makes Chronos self-contained for API-driven products without depending on external IDLs like Smithy.

Proposed syntax:

api PlaceOrderEndpoint {  
    method: POST  
    path: "/v1/orders"  
    version: "1.0"

    request: {  
        body: CreateOrderRequest  
        headers: {  
            Authorization: String  
            Idempotency-Key: String  
        }  
    }

    response: {  
        success: {  
            status: 201  
            body: Order  
        }  
        errors: \[  
            { status: 400, error: InvalidOrderError },  
            { status: 402, error: PaymentDeclinedError },  
            { status: 409, error: DuplicateOrderError }  
        \]  
    }

    @authorize(role: "Customer")  
    @slo(latency: "500ms", p99: true)  
    @rateLimit(limit: 100, window: "1m")  
}

**Validation Rules:**

* CHR-061: HTTP method must be one of: GET, POST, PUT, PATCH, DELETE  
* CHR-062: Request/response body types must reference defined shapes or entities  
* CHR-063: Error references must point to defined error types  
* CHR-064: API paths must be unique within a namespace for the same method

**Implementation Tasks:**

1. Add api keyword with method, path, version, request, response blocks  
2. Add @rateLimit trait  
3. Validate body type references, error type references, and path uniqueness  
4. Generate OpenAPI 3.x specs from api declarations  
5. Generate API documentation with request/response examples  
6. Link api endpoints to journeys that use them (traceability)

**Acceptance Criteria:**

* api declarations parse and validate all sub-blocks  
* Generated OpenAPI spec is valid and includes all declared endpoints  
* Undefined body or error types produce appropriate validation errors  
* Duplicate method+path combinations produce CHR-064

### **6.2  UI/UX and Accessibility Requirements**

**Requirement:** Introduce traits and an optional view construct for declaring user interface constraints, accessibility standards, responsive behavior, and interaction design requirements.

Proposed syntax:

@accessibility(standard: "WCAG-2.1-AA")  
@responsive(breakpoints: \["mobile", "tablet", "desktop"\])  
journey UserRegistration { ... }

view RegistrationForm {  
    journey: UserRegistration  
    step: ProvideEmail

    components: \[  
        field EmailInput {  
            type: "email"  
            label: "Email Address"  
            placeholder: "you@example.com"  
            validation: inline  
            @accessibility(role: "textbox", ariaLabel: "Email address input")  
        },  
        action SubmitButton {  
            label: "Create Account"  
            style: primary  
        }  
    \]

    layout: {  
        maxWidth: "480px"  
        alignment: center  
    }  
}

**Validation Rules:**

* CHR-065: @accessibility standard must reference a recognized standard (WCAG-2.0-A, WCAG-2.1-AA, WCAG-2.2-AAA, Section508)  
* CHR-066: view constructs must reference a defined journey and step  
* CHR-067: Component field types must be valid HTML input types  
* CHR-068: @responsive breakpoints must be unique within the view

**Implementation Tasks:**

1. Add @accessibility and @responsive traits  
2. Add view keyword with components and layout blocks  
3. Validate journey/step references in views  
4. Generate accessibility checklist documentation from @accessibility annotations  
5. Generate wireframe-level component specs from view declarations  
6. Include accessibility requirements in test scaffolding

**Acceptance Criteria:**

* view declarations parse and validate against referenced journeys/steps  
* Invalid accessibility standards produce CHR-065  
* Generated docs include an accessibility requirements checklist  
* Component specs are generated from view declarations

# **Documentation Generation System**

As Chronos evolves through the six phases above, the language reference documentation must stay perfectly synchronized with the implementation. A manually-maintained reference will inevitably drift. This section specifies a documentation generation system that treats the ANTLR grammar and Java compiler source as the single source of truth and produces the full language reference as a build artifact.

This system should be implemented before or alongside Phase 1 so that every new construct added to the grammar is automatically documented from the start. It is a cross-cutting concern, not a phase unto itself. Since the project is pre-release, the doc generation pipeline can be designed for the full target grammar without needing to handle legacy or partially-documented rules.

## **Architecture Overview**

The documentation pipeline has three layers, each reading from a different part of the compiler source. Together they produce the complete language reference.

| Layer | Source of Truth | Output |
| :---- | :---- | :---- |
| Grammar Extractor | ANTLR .g4 files | Syntax reference, railroad diagrams, keyword index |
| Semantic Extractor | Java compiler classes | Trait reference, validation rules, type catalog, expression functions |
| Spec-Level Generator | User .chronos files | Entity catalogs, journey docs, event catalogs, error catalogs (already exists via chronos build) |

Layers 1 and 2 run against the compiler source code and produce the language reference. Layer 3 already exists (the chronos build projection system) and runs against user-authored .chronos files. The key design decision is that layers 1 and 2 are Gradle tasks that execute during the compiler build, not during chronos build. This means the language reference is regenerated every time the compiler is built, guaranteeing synchronization.

### **D.1  Grammar Doc Extractor**

**Requirement:** Build a Java tool that parses the Chronos ANTLR .g4 grammar files using the ANTLRv4 meta-grammar, extracts parser rules with their doc comments, and generates structured Markdown reference pages for every language construct.

The ANTLR .g4 file is already a formal, complete description of every construct the parser accepts. The extractor reads this as data to produce documentation that is always in sync with what the parser actually accepts.

**Doc Comment Convention:**

Establish a Javadoc-style doc comment convention in the .g4 file. Comments above parser rules use /\*\* ... \*/ and include structured annotations:

/\*\*  
 \* Declares a named entity with identity, fields, and optional relationships.  
 \* Entities represent persistent business objects in the domain model.  
 \*  
 \* @since 1.0  
 \* @category Shape Definitions  
 \* @see shapeDeclaration  
 \* @example  
 \*   @pii  
 \*   entity Customer {  
 \*       @required  
 \*       id: String  
 \*       name: String  
 \*       orders: List\<Order\>  
 \*   }  
 \*/  
entityDeclaration  
    : trait\* ENTITY name=IDENTIFIER (EXTENDS parent=qualifiedName)?  
      LBRACE entityBody RBRACE  
    ;

/\*\*  
 \* A single field within an entity or shape body.  
 \*  
 \* @since 1.0  
 \* @category Shape Definitions  
 \*/  
fieldDeclaration  
    : trait\* fieldName=IDENTIFIER COLON fieldType  
    ;

**Supported Doc Annotations:**

| Annotation | Purpose | Example |
| :---- | :---- | :---- |
| @since | Version when this construct was introduced | @since 1.0 |
| @category | Groups the rule under a section heading in the generated docs | @category Journeys |
| @see | Cross-reference to another parser rule | @see journeyDeclaration |
| @example | Inline Chronos code example (continues until next @tag or \*/) | (multi-line block) |
| @deprecated | Marks rule as deprecated with reason | @deprecated Use entityDecl |
| @internal | Excludes the rule from public documentation | @internal |

**Extractor Implementation:**

The extractor is a standalone Java class that uses the ANTLR4 runtime to parse the .g4 file itself:

// GrammarDocExtractor.java  
package com.genairus.chronos.docgen;

import org.antlr.v4.tool.Grammar;  
import org.antlr.v4.tool.Rule;  
import org.antlr.v4.tool.ast.GrammarAST;

public class GrammarDocExtractor {

    /\*\*  
     \* Entry point. Reads the .g4 grammar, parses doc comments,  
     \* and emits structured Markdown.  
     \*/  
    public static void main(String\[\] args) {  
        String grammarPath \= args\[0\];  // path to ChronosParser.g4  
        String outputDir  \= args\[1\];   // path to docs/output/

        Grammar grammar \= Grammar.load(grammarPath);  
        GrammarDocModel model \= new GrammarDocModel();

        for (Rule rule : grammar.rules.values()) {  
            if (isInternal(rule)) continue;

            DocComment doc \= DocCommentParser.parse(rule);  
            RuleSignature sig \= RuleSignatureExtractor.extract(rule);

            model.addEntry(new GrammarDocEntry(  
                rule.name,  
                doc.description(),  
                doc.since(),  
                doc.category(),  
                sig.alternatives(),  
                sig.referencedRules(),  
                sig.referencedTokens(),  
                doc.examples(),  
                doc.seeAlso(),  
                doc.isDeprecated()  
            ));  
        }

        // Generate output  
        MarkdownRenderer.render(model, outputDir);  
        RailroadDiagramRenderer.render(model, outputDir);  
        KeywordIndexRenderer.render(model, outputDir);  
    }  
}

**Output Structure:**

The extractor generates one Markdown file per @category, following the same structure as the existing Chronos-Language.md:

docs/generated/  
  language-reference/  
    shape-definitions.md       \# Entities, Shapes, Enums, Collections  
    journeys.md                \# Journey structure, steps, outcomes, variants  
    actors-and-policies.md     \# Actor and policy declarations  
    trait-system.md            \# Built-in and custom traits  
    state-machines.md          \# Statemachine declarations (Phase 3+)  
    events-and-reactions.md    \# Event types, reactions (Phase 4+)  
    authorization.md           \# Roles, permissions (Phase 3+)  
    api-contracts.md           \# API declarations (Phase 6+)  
    views.md                   \# UI/UX views (Phase 6+)  
  diagrams/  
    railroad/                  \# One SVG per parser rule  
  keyword-index.md            \# Alphabetical keyword reference  
  validation-rules.md         \# All CHR-xxx rules (from semantic extractor)  
  changelog.md                \# Generated from @since annotations

**Generated Markdown Format:**

Each construct page follows a consistent template mirroring Chronos-Language.md:

\#\# Entity Declarations

Entities represent the data model of your product \- business objects with identity.

\`\`\`chronos  
@pii  
@sensitivity("high")  
entity Order {  
    @required  
    id: String  
    items: List\<OrderItem\>  
    total: Money  
    status: OrderStatus  
}  
\`\`\`

\*\*Syntax:\*\*

\!\[entityDeclaration railroad diagram\](../diagrams/railroad/entityDeclaration.svg)

\`\`\`  
entityDeclaration  
    : trait\* ENTITY IDENTIFIER (EXTENDS qualifiedName)? '{' entityBody '}'  
    ;  
\`\`\`

\*\*Rules:\*\*  
\- Entity names must be PascalCase  
\- Fields are \`name: Type\` pairs  
\- Fields without \`@required\` are optional by default  
\- Can reference other entities, lists, enums, and shapes

\*\*Since:\*\* 1.0  
\*\*See also:\*\* \[Shapes\](\#shapes-value-objects), \[Collections\](\#collections)

**Validation Rules:**

* CHR-069: Every non-internal parser rule must have a /\*\* doc comment  
* CHR-070: Every doc comment must include @since and @category annotations  
* CHR-071: @see references must point to existing parser rules  
* CHR-072: @example blocks must be valid Chronos syntax (validated at build time)

**Implementation Tasks:**

1. Define the doc comment grammar: write a lightweight parser (regex or ANTLR sub-grammar) for /\*\* ... \*/ blocks with @since, @category, @see, @example, @deprecated, @internal annotations  
2. Implement DocCommentParser.java to extract structured annotations from comment text above each rule  
3. Implement RuleSignatureExtractor.java to walk each ANTLR rule AST and extract alternatives, referenced rules, referenced tokens, and labeled elements  
4. Implement GrammarDocModel.java as the in-memory model grouping entries by @category with cross-reference resolution  
5. Implement MarkdownRenderer.java to emit one .md file per category using the template format above  
6. Implement KeywordIndexRenderer.java to emit an alphabetical keyword reference  
7. Add Gradle task :generateGrammarDocs that runs the extractor as part of the build  
8. Add CI check that fails the build if any non-internal parser rule lacks a doc comment (enforces CHR-069)

**Acceptance Criteria:**

* Running :generateGrammarDocs produces a complete language-reference/ directory  
* Adding a new parser rule without a doc comment fails the CI build  
* Generated Markdown matches the structure and tone of the existing Chronos-Language.md  
* @see cross-references resolve to valid anchors in the generated output  
* @example blocks are syntax-validated against the Chronos parser

### **D.2  Semantic Doc Extractor**

**Requirement:** Build a Java tool that introspects the Chronos compiler internals (trait registry, validation rule registry, type system, expression function catalog) and generates reference documentation for language semantics that live beyond the grammar.

The grammar tells you what the parser accepts. The semantic extractor tells you what the language means: what traits are available, what parameters they take, what validation rules exist, what expression functions are supported in invariants, and what primitive types are available.

**Source Convention:**

Each semantic construct in the compiler should be annotated with a @DocExport annotation that marks it for documentation extraction:

// In the Java compiler source:

@DocExport(  
    category \= "Step Traits",  
    description \= "Service level objective for step latency",  
    since \= "1.0"  
)  
public class SloTrait implements Trait {  
    @DocParam(description \= "Maximum acceptable latency", example \= "500ms")  
    private final Duration latency;

    @DocParam(description \= "Whether this is a p99 target", example \= "true")  
    private final boolean p99;  
}

@DocExport(  
    category \= "Validation Rules",  
    description \= "Every journey must declare an actor",  
    severity \= "Error",  
    since \= "1.0"  
)  
public static final ValidationRule CHR\_001 \= ...

@DocExport(  
    category \= "Expression Functions",  
    description \= "Returns the sum of a numeric expression across a collection",  
    since \= "2.0"  
)  
public class SumFunction implements ExpressionFunction {  
    @DocParam(description \= "The collection to iterate")  
    private final CollectionRef collection;

    @DocParam(description \= "Lambda extracting the numeric value")  
    private final LambdaExpr selector;  
}

**Extractor Implementation:**

The semantic extractor uses Java reflection or compile-time annotation processing to scan the compiler classpath for @DocExport-annotated classes:

// SemanticDocExtractor.java  
package com.genairus.chronos.docgen;

public class SemanticDocExtractor {

    public static void main(String\[\] args) {  
        String outputDir \= args\[0\];

        SemanticDocModel model \= new SemanticDocModel();

        // Scan trait registry  
        for (Class\<?\> traitClass : TraitRegistry.allTraits()) {  
            DocExport export \= traitClass.getAnnotation(DocExport.class);  
            if (export \== null) continue;

            List\<ParamDoc\> params \= new ArrayList\<\>();  
            for (Field f : traitClass.getDeclaredFields()) {  
                DocParam dp \= f.getAnnotation(DocParam.class);  
                if (dp \!= null) {  
                    params.add(new ParamDoc(  
                        f.getName(), f.getType().getSimpleName(),  
                        dp.description(), dp.example()));  
                }  
            }

            model.addTrait(new TraitDocEntry(  
                traitClass.getSimpleName(),  
                export.category(),  
                export.description(),  
                export.since(),  
                params  
            ));  
        }

        // Scan validation rules  
        for (ValidationRule rule : ValidationRegistry.allRules()) {  
            DocExport export \= rule.getAnnotation(DocExport.class);  
            if (export \== null) continue;  
            model.addRule(new RuleDocEntry(  
                rule.code(), export.severity(),  
                export.description(), export.since()));  
        }

        // Scan expression functions (Phase 2+)  
        for (ExpressionFunction fn : ExprFunctionRegistry.allFunctions()) {  
            // ... similar pattern  
        }

        // Render to Markdown  
        TraitReferenceRenderer.render(model, outputDir);  
        ValidationRuleRenderer.render(model, outputDir);  
        TypeCatalogRenderer.render(model, outputDir);  
        ExprFunctionRenderer.render(model, outputDir);  
    }  
}

**Output:**

The semantic extractor appends to the same docs/generated/ directory:

docs/generated/  
  language-reference/  
    trait-system.md             \# Extended with full parameter tables from @DocExport  
  trait-reference.md            \# Complete trait catalog (journey, entity, step, field)  
  validation-rules.md           \# All CHR-xxx rules with severity, since, description  
  primitive-types.md            \# Type catalog with descriptions and examples  
  expression-functions.md       \# sum, count, exists, forAll, etc. (Phase 2+)

**Implementation Tasks:**

1. Define @DocExport and @DocParam annotations in the compiler source  
2. Annotate all existing traits, validation rules, and primitive types with @DocExport  
3. Implement SemanticDocExtractor.java with reflection-based scanning  
4. Implement TraitReferenceRenderer.java to generate trait tables matching the format in Chronos-Language.md (trait, parameters, description, example columns)  
5. Implement ValidationRuleRenderer.java to generate the validation rules table  
6. Implement TypeCatalogRenderer.java for primitive and collection types  
7. Add Gradle task :generateSemanticDocs  
8. Add CI check: every Trait subclass and ValidationRule constant must have @DocExport

**Acceptance Criteria:**

* Running :generateSemanticDocs produces trait-reference.md and validation-rules.md  
* Adding a new trait without @DocExport fails the CI build  
* Trait tables include all parameters with types, descriptions, and examples  
* Validation rules table includes code, severity, description, and @since version

### **D.3  Railroad Diagram Generator**

**Requirement:** Generate visual railroad (syntax) diagrams from ANTLR parser rules and embed them in the language reference. Railroad diagrams show the structure of each construct at a glance and are the industry standard for language documentation.

**Approach:**

Convert ANTLR rules to W3C EBNF notation, then render as SVG using an established railroad diagram library. The recommended pipeline is:

ChronosParser.g4  
    |  
    v  
ANTLR rule AST  (parsed by GrammarDocExtractor)  
    |  
    v  
W3C EBNF representation  (EBNFConverter.java)  
    |  
    v  
Railroad diagram SVG  (via rr-diagram-java or GrammarKit)  
    |  
    v  
docs/generated/diagrams/railroad/entityDeclaration.svg

**Java Library Options:**

| Library | Approach | Notes |
| :---- | :---- | :---- |
| rr-diagram-java (Tabatkins) | Pure Java, direct EBNF to SVG rendering | Recommended. No external dependencies, generates clean SVG |
| GrammarKit / ANTLR4-to-BNF | ANTLR-native AST walk to BNF, then external renderer | More control over rule simplification |
| Custom SVG renderer | Walk ANTLR rule alternatives, emit SVG paths directly | Maximum control, highest implementation cost |

**Implementation Tasks:**

1. Add rr-diagram-java (or chosen library) as a build dependency  
2. Implement EBNFConverter.java to transform ANTLR rule ASTs into EBNF notation compatible with the diagram library  
3. Implement RailroadDiagramRenderer.java to generate one SVG per non-internal parser rule  
4. Add rule simplification logic: inline trivial helper rules, collapse token sequences into readable labels (e.g., show ENTITY as entity keyword, not the token name)  
5. Integrate SVG output into the MarkdownRenderer so each construct page embeds its railroad diagram  
6. Add Gradle task :generateRailroadDiagrams (or integrate into :generateGrammarDocs)

**Acceptance Criteria:**

* Every non-internal parser rule has a corresponding SVG railroad diagram  
* Diagrams render keywords as readable labels, not ANTLR token names  
* Generated Markdown pages embed the diagram for each construct  
* Diagrams are deterministic (same grammar produces identical SVGs, safe for diff review)

### **D.4  Unified Doc Pipeline and Gradle Integration**

**Requirement:** Wire the grammar extractor, semantic extractor, and railroad generator into a single Gradle task hierarchy that runs as part of the standard build. The generated documentation must be versioned, diffable, and publishable to the Chronos documentation site.

**Gradle Task Hierarchy:**

// build.gradle.kts

tasks {

    // Individual extraction tasks  
    val generateGrammarDocs by registering(JavaExec::class) {  
        group \= "documentation"  
        description \= "Generate syntax reference from ANTLR grammar"  
        mainClass.set("com.genairus.chronos.docgen.GrammarDocExtractor")  
        args \= listOf(  
            "$projectDir/src/main/antlr/ChronosParser.g4",  
            "$buildDir/docs/generated"  
        )  
        classpath \= sourceSets\["main"\].runtimeClasspath  
        dependsOn("generateGrammarSource")  // ANTLR plugin task  
    }

    val generateSemanticDocs by registering(JavaExec::class) {  
        group \= "documentation"  
        description \= "Generate trait/rule reference from compiler annotations"  
        mainClass.set("com.genairus.chronos.docgen.SemanticDocExtractor")  
        args \= listOf("$buildDir/docs/generated")  
        classpath \= sourceSets\["main"\].runtimeClasspath  
        dependsOn("classes")  
    }

    val generateRailroadDiagrams by registering(JavaExec::class) {  
        group \= "documentation"  
        description \= "Generate railroad syntax diagrams"  
        mainClass.set("com.genairus.chronos.docgen.RailroadDiagramRenderer")  
        args \= listOf(  
            "$projectDir/src/main/antlr/ChronosParser.g4",  
            "$buildDir/docs/generated/diagrams/railroad"  
        )  
        classpath \= sourceSets\["main"\].runtimeClasspath  
        dependsOn("generateGrammarSource")  
    }

    // Aggregate task  
    val generateDocs by registering {  
        group \= "documentation"  
        description \= "Generate all Chronos language documentation"  
        dependsOn(generateGrammarDocs, generateSemanticDocs, generateRailroadDiagrams)

        doLast {  
            // Assemble final output  
            copy {  
                from("$buildDir/docs/generated")  
                into("$projectDir/docs/language-reference")  
            }  
            println("Documentation generated at docs/language-reference/")  
        }  
    }

    // Wire into CI  
    named("check") {  
        dependsOn(generateDocs)  
    }  
}

**CI Enforcement:**

The following checks run as part of the CI pipeline and fail the build on violations:

| Check | Fails When |
| :---- | :---- |
| Doc comment coverage | A non-@internal parser rule has no /\*\* doc comment |
| @DocExport coverage | A Trait subclass or ValidationRule constant lacks @DocExport |
| Example validation | An @example block in a doc comment fails Chronos parsing |
| Cross-reference integrity | An @see reference points to a non-existent parser rule |
| Docs staleness | Generated docs differ from committed docs (ensures docs are regenerated before merge) |

**Version Changelog Generation:**

The @since annotations across both grammar doc comments and @DocExport annotations allow automatic generation of a changelog. The pipeline groups all constructs by their @since version and emits a What's New page for each version:

\# What's New in Chronos 2.0

\#\# New Constructs  
\- \*\*invariant\*\* \- Cross-entity boolean constraints (see \[Invariants\](...))  
\- \*\*deny\*\* \- Negative requirements / prohibitions (see \[Deny\](...))  
\- \*\*error\*\* \- Typed error definitions with codes and payloads (see \[Errors\](...))

\#\# New Traits  
\- \*\*@range\*\* \- Numeric range validation on fields  
\- \*\*@length\*\* \- String length constraints on fields

\#\# New Validation Rules  
\- CHR-019: Invariant expressions must reference only fields visible in scope  
\- CHR-020: Severity must be one of: error, warning, info  
\- ...

**Implementation Tasks:**

1. Create the Gradle task hierarchy as specified above  
2. Implement the docs-staleness CI check (generate docs in CI, diff against committed docs, fail if different)  
3. Implement ChangelogRenderer.java to group all @since-annotated items by version and emit changelog Markdown  
4. Implement DocCoverageValidator.java for the CI checks (doc comment coverage, @DocExport coverage, example validation, cross-reference integrity)  
5. Add a :publishDocs task that pushes generated docs to the documentation site (static site generator or hosted docs platform)  
6. Document the doc generation system itself in a CONTRIBUTING.md section so that future contributors know the convention

**Acceptance Criteria:**

* Running ./gradlew generateDocs produces the complete docs/language-reference/ directory  
* Running ./gradlew check includes doc generation and all CI enforcement checks  
* A PR that adds a new parser rule without a doc comment fails CI  
* A PR that adds a new trait without @DocExport fails CI  
* A PR that changes grammar without regenerating docs fails the staleness check  
* The generated language reference is structurally identical to the hand-written Chronos-Language.md (same sections, same table formats, same code block style)

# **Appendix A: New Validation Rules Summary**

All new validation rules introduced across the six phases, for quick reference:

| Code | Feature | Description |
| :---- | :---- | :---- |
| CHR-011 | Entity Relationships and Cardinality | Relationship targets must reference defined or imported entities |
| CHR-012 | Entity Relationships and Cardinality | Composition targets cannot be referenced by more than one composing entity |
| CHR-013 | Entity Relationships and Cardinality | Cardinality values must be one of: one\_to\_one, one\_to\_many, many\_to\_many |
| CHR-014 | Entity Relationships and Cardinality | Inverse field name (if specified) must exist on the target entity |
| CHR-015 | Inheritance and Generalization | Circular inheritance chains are a validation error |
| CHR-016 | Inheritance and Generalization | A child entity may not redefine a parent field with an incompatible type |
| CHR-017 | Import Resolution | Ambiguous import: same simple name bound to different targets (compiler / `ImportResolver`) |
| CHR-018 | Inheritance and Generalization | Multiple inheritance is not supported; use shape composition instead |
| CHR-019 | Cross-Entity Invariants | Invariant expressions must reference only fields visible in scope |
| CHR-020 | Cross-Entity Invariants | Severity must be one of: error, warning, info |
| CHR-021 | Cross-Entity Invariants | Global invariants must declare a scope listing all referenced entities |
| CHR-022 | Cross-Entity Invariants | Invariant names must be unique within their enclosing scope |
| CHR-023 | Negative Requirements (shall\_not) | Every deny must include a description |
| CHR-024 | Negative Requirements (shall\_not) | Scope entities must be defined or imported |
| CHR-025 | Negative Requirements (shall\_not) | Severity must be one of: critical, high, medium, low |
| CHR-026 | Error and Exception Taxonomy | Error codes must be unique across the namespace |
| CHR-027 | Error and Exception Taxonomy | Variant triggers must reference a defined error type (string triggers are not supported) |
| CHR-028 | Error and Exception Taxonomy | Error severity must be one of: critical, high, medium, low |
| CHR-029 | Formal State Machines | All states referenced in transitions must be declared in the states list |
| CHR-030 | Formal State Machines | Every non-terminal state must have at least one outbound transition |
| CHR-031 | Formal State Machines | The initial state must be in the states list |
| CHR-032 | Formal State Machines | Terminal states must not have outbound transitions |
| CHR-033 | Formal State Machines | The referenced entity and field must be defined; field type should be an enum |
| CHR-034 | Step Data Flow | Input types must reference defined shapes, entities, or primitives |
| CHR-035 | Step Data Flow | A step input type must appear in a preceding step output or precondition |
| CHR-036 | Step Data Flow | Output field names must be unique within the journey scope |
| CHR-037 | Authorization and Permissions | Roles referenced in @authorize must be defined or imported |
| CHR-038 | Authorization and Permissions | Permissions referenced in @authorize must be declared in the role |
| CHR-039 | Authorization and Permissions | A journey actor must hold a role compatible with all @authorize annotations in that journey |
| CHR-040 | Authorization and Permissions | deny permissions take precedence over allow at the same specificity level |
| CHR-041 | Temporal and Time-Based Requirements | @timeout duration must be a valid duration literal (e.g., 15m, 2h, 30d) |
| CHR-042 | Temporal and Time-Based Requirements | @timeout onExpiry must reference a valid state or variant |
| CHR-043 | Temporal and Time-Based Requirements | @ttl action must be one of: delete, archive, notify |
| CHR-044 | Temporal and Time-Based Requirements | @schedule cron expressions must be valid 5-field cron syntax |
| CHR-045 | Events, Reactions, and System-Initiated Flows | Telemetry fields must reference defined event types (bare name strings are not supported) |
| CHR-046 | Events, Reactions, and System-Initiated Flows | Reaction triggers must reference a defined event type |
| CHR-047 | Events, Reactions, and System-Initiated Flows | Reactions must declare an actor (system or human) |
| CHR-048 | Events, Reactions, and System-Initiated Flows | Event payload fields follow the same type rules as entity fields |
| CHR-049 | Concurrency and Parallel Flows | parallel blocks must contain at least two branches |
| CHR-050 | Concurrency and Parallel Flows | Join strategy must be one of: all, any, n\_of(N) where N \< branch count |
| CHR-051 | Concurrency and Parallel Flows | Branches within a parallel block must not declare data dependencies on each other |
| CHR-052 | Concurrency and Parallel Flows | Each branch follows all existing step validation rules |
| CHR-053 | Inter-Journey Dependencies and Composition | @requires targets must reference defined journeys |
| CHR-054 | Inter-Journey Dependencies and Composition | Circular journey dependencies are a validation error |
| CHR-055 | Inter-Journey Dependencies and Composition | invoke must reference a defined journey; its outcomes map to the invoking step |
| CHR-056 | Inter-Journey Dependencies and Composition | An invoked journey actor must be compatible with the parent journey actor |
| CHR-057 | Expanded Non-Functional Requirements | Availability targets must be valid percentages |
| CHR-058 | Expanded Non-Functional Requirements | RPO and RTO must be valid duration literals |
| CHR-059 | Expanded Non-Functional Requirements | NFR scope must reference defined journeys or entities |
| CHR-060 | Expanded Non-Functional Requirements | Throughput targets must include a rate unit |
| CHR-061 | Interface and API Contracts | HTTP method must be one of: GET, POST, PUT, PATCH, DELETE |
| CHR-062 | Interface and API Contracts | Request/response body types must reference defined shapes or entities |
| CHR-063 | Interface and API Contracts | Error references must point to defined error types |
| CHR-064 | Interface and API Contracts | API paths must be unique within a namespace for the same method |
| CHR-065 | UI/UX and Accessibility Requirements | @accessibility standard must reference a recognized standard (WCAG-2.0-A, WCAG-2.1-AA, WCAG-2.2-AAA, Section508) |
| CHR-066 | UI/UX and Accessibility Requirements | view constructs must reference a defined journey and step |
| CHR-067 | UI/UX and Accessibility Requirements | Component field types must be valid HTML input types |
| CHR-068 | UI/UX and Accessibility Requirements | @responsive breakpoints must be unique within the view |
| CHR-069 | Grammar Doc Extractor | Every non-internal parser rule must have a /\*\* doc comment |
| CHR-070 | Grammar Doc Extractor | Every doc comment must include @since and @category annotations |
| CHR-071 | Grammar Doc Extractor | @see references must point to existing parser rules |
| CHR-072 | Grammar Doc Extractor | @example blocks must be valid Chronos syntax (validated at build time) |

# **Appendix B: Implementation Principles**

These principles should guide every phase of implementation:

**Clean-Slate Design:** Chronos is pre-release with no existing users or .chronos files in production. There are no backward compatibility constraints. The grammar, AST, and internal representations should be designed for the final target state from the start. Do not introduce transitional syntax, dual-mode parsing, or deprecation paths. Every construct should be implemented in its canonical form on the first pass.

**Errors, Not Warnings:** All validation rules are hard errors unless there is a specific technical reason for a softer severity. Since there are no existing files to break, there is no need for warning-level rules to ease migration. If something is wrong, reject it.

**Fail Loudly on Ambiguity:** If a construct introduces potential ambiguity with existing syntax, it is a parser error, not a silent interpretation. Chronos’s core principle of strictness over flexibility applies to the evolution itself.

**Restructure Freely:** If implementing a later phase reveals that an earlier phase’s AST structure, type representation, or grammar rule should be reorganized, do it. There are no deployed parsers to break. The best internal design wins over any commitment to an earlier phase’s implementation.

**Single Canonical Form:** Every concept has exactly one way to express it. Do not support both string-based and typed references (e.g., variant triggers must be typed error references, telemetry must reference typed events). No syntactic sugar that creates ambiguity about which form is preferred.

**Test-Driven Validation Rules:** Every CHR-xxx rule must have a corresponding test case (both positive and negative) before the feature is considered complete.

**Documentation as Code:** The language reference documentation is generated from the ANTLR grammar and compiler source (see the Documentation Generation System section). Doc comments in the .g4 file and @DocExport annotations in Java source are mandatory and enforced by CI.

**Parallel-Safe Phases:** Since there are no deployed users, phases can be developed in parallel on feature branches when their dependencies are satisfied. Phases 1 and 2 can run concurrently if the entity type graph is stabilized early. Phases 4 and 5 can run concurrently since they extend different parts of the grammar.