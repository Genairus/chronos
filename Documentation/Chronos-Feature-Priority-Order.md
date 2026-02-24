# Chronos Complete Language Recommendations

Complete implementation recommendations for a fully capable Chronos language and platform  
Date: February 2026

## Purpose

This document consolidates:

1. Remaining items from `Create_Chronos_Evolution_Task_List_2026-02-21T20-02-01.md`.
2. Corrections for tasks marked complete but still needing hardening.
3. Additional missing capabilities needed for a complete production language.

## Current Baseline

Implemented strongly:

- Phase 1: relationships, inheritance
- Phase 2: invariants, deny, typed errors
- Phase 3.1: state machines

Still open from task list:

- Phase 3 remainder (`3.2`, `3.3`)
- Phase 4 (`4.1`, `4.2`)
- Phase 5 (`5.1`, `5.2`, `5.3`)
- Phase 6 (`6.1`, `6.2`)
- Cross-cutting docs (`D.1`, `D.2`, `D.3`, `D.4`)

## Release Recommendation

- **v1.0 GA blockers:** P0 + Reopen Items
- **v1.1:** P1
- **v1.2:** P2
- **v1.3+:** P3 + Additional Platform Completeness

## P0: Must-Have Before GA

### 1) Step Data Flow (Task Group 3.2)

What it is for:
- Makes step contracts explicit for AI/codegen and validation.
- Prevents hidden data assumptions between steps.

Task coverage:
- `3.2.1`, `3.2.2`, `3.2.3`, `3.2.4`

Mini spec:
- Add `input` and `output` blocks to `step`.
- Fields use existing `typeRef`.
- Enforce upstream availability and unique output names per journey.

Example:
```chronos
step ProvideShipping {
    action: "Enter shipping address"
    input: {
        rawAddress: Address
    }
    expectation: "Address is validated and shipping options computed"
    output: {
        validatedAddress: Address
        shippingOptions: List<ShippingMethod>
    }
}
```

### 2) Authorization and Permissions (Task Group 3.3)

What it is for:
- Encodes access control in requirements, not post-hoc in implementation.

Task coverage:
- `3.3.1`, `3.3.2`, `3.3.3`, `3.3.4`

Mini spec:
- Add `role` declaration and allow/deny permission lists.
- Add `@authorize(role, permission)` trait.
- Enforce role existence, permission membership, actor-role compatibility, deny precedence.

Example:
```chronos
role SupportAgent {
    permissions: [
        allow ViewOwnTickets,
        allow ResolveTickets,
        deny ViewPaymentCardData
    ]
}

@authorize(role: "SupportAgent", permission: "ResolveTickets")
journey ResolveTicket {
    actor: SupportActor
    steps: [ step CloseTicket { action: "Close issue", expectation: "Ticket is closed" } ]
    outcomes: { success: "Ticket resolved" }
}
```

### 3) Typed Events for Telemetry Baseline (Partial 4.2 Pull-In)

What it is for:
- Removes ambiguous telemetry IDs and establishes event schema contracts early.

Task coverage:
- Pull in `4.2.1` + `4.2.2` into GA scope.

Mini spec:
- Add top-level `event` with typed `payload`.
- Require `telemetry: [EventType]` references to declared/imported events.

Example:
```chronos
event CheckoutStartedEvent {
    payload: {
        cartId: String
        customerId: String
    }
}

step ReviewCart {
    action: "Review cart"
    expectation: "Cart totals shown"
    telemetry: [CheckoutStartedEvent]
}
```

## Reopen Items (Even If Marked Complete)

### R1) Replace lexical invariant validation with a real expression parser/type-checker

Why:
- Current token-split approach is fragile for complex expressions.

Change:
- Introduce expression AST + typed semantic validation.

Example target expression:
```chronos
invariant TotalMatchesItems {
    expression: "total.amount == sum(items, i => i.unitPrice.amount * i.quantity)"
    severity: error
}
```

### R2) Tighten statemachine field-state compatibility

Why:
- Require explicit equivalence/compatibility between enum members and declared states.

Change:
- Add check: `states` must be subset/equal to bound enum members (with explicit policy).

Example:
```chronos
statemachine OrderLifecycle {
    entity: Order
    field: status
    states: [PENDING, PAID, SHIPPED]
    initial: PENDING
    terminal: [SHIPPED]
}
```

### R3) Strengthen `TransitionTo` semantics

Why:
- Existence-only checking is weaker than machine-linked transition validation.

Change:
- Bind journey to target statemachine/context and validate allowed transitions/targets.

Example:
```chronos
step SubmitPayment {
    action: "Submit payment"
    expectation: "Payment authorized"
    outcome: TransitionTo(PAID)
}
```

### R4) Normalize diagnostic-code ownership and numbering

Why:
- Plan and implementation reuse/overlap some CHR codes with different meanings.

Change:
- Publish a canonical diagnostic registry and enforce uniqueness by subsystem.

### R5) Resolve spec-doc drift (severities/rules/examples)

Why:
- Quick reference and validator constraints currently diverge in places.

Change:
- Add CI check to validate docs examples and allowed enum values against compiler rules.

## P1: First Post-GA Wave

### 4) Temporal Requirements (Task Group 4.1)

What it is for:
- Expresses timeouts, TTL, schedules, and SLA semantics directly.

Task coverage:
- `4.1.1`..`4.1.6`

Mini spec:
- Duration literals (`ms`, `s`, `m`, `h`, `d`).
- `@timeout(duration, onExpiry)` for steps.
- `@ttl(duration, action)` for entities.
- Trigger scheduling with cron-like validation.

Example:
```chronos
@ttl(duration: "30d", action: "archive")
entity Session {
    id: String
    createdAt: Timestamp
}

step AwaitPayment {
    @timeout(duration: "15m", onExpiry: TransitionTo(PaymentTimedOut))
    action: "Wait for gateway callback"
    expectation: "Payment callback is received"
}
```

### 5) Events, Reactions, and System-Initiated Flows (Task Group 4.2 Full)

What it is for:
- Makes event-driven architecture first-class.

Task coverage:
- `4.2.1`..`4.2.5`

Mini spec:
- Add `reaction` triggered by typed events.
- Validate event references across journeys/reactions/actions.
- Generate event catalogs and flow diagrams.

Example:
```chronos
reaction OnOrderPaid {
    trigger: OrderPaidEvent
    actor: FulfillmentService
    steps: [
        step ReserveInventory {
            action: "Reserve stock"
            expectation: "Inventory reservation created"
        }
    ]
    outcomes: { success: "Reservation completed" }
}
```

### 6) Parallel Flows (Task Group 5.1)

What it is for:
- Models concurrent branches with clear join semantics.

Task coverage:
- `5.1.1`..`5.1.4`

Mini spec:
- Add `parallel` block with `join: all|any|n_of(N)`.
- Enforce branch independence and post-join output availability rules.

Example:
```chronos
parallel ValidateOrder {
    join: all
    branches: [
        step ValidatePayment { action: "Check payment", expectation: "Payment valid" },
        step CheckInventory { action: "Check stock", expectation: "Stock available" }
    ]
}
```

### 7) Journey Composition and Dependencies (Task Group 5.2)

What it is for:
- Enables reuse and orchestration of modular journeys.

Task coverage:
- `5.2.1`..`5.2.5`

Mini spec:
- Add `@requires` and `Invoke(...)`.
- Validate acyclic dependency graph and actor compatibility.

Example:
```chronos
@requires(["VerifyIdentity"])
journey OpenAccount {
    actor: CustomerActor
    steps: [
        step VerifyKyc {
            action: "Run KYC"
            expectation: "Identity verified"
            outcome: Invoke(VerifyIdentity)
        }
    ]
    outcomes: { success: "Account opened" }
}
```

### 8) Expanded NFRs (Task Group 5.3)

What it is for:
- Turns operational targets into first-class requirements.

Task coverage:
- `5.3.1`..`5.3.3`

Mini spec:
- Add NFR traits (`@availability`, `@throughput`, `@capacity`, `@recoverability`) and `nfr` blocks.
- Validate unit formats and generate NFR summary/test baselines.

Example:
```chronos
nfr CheckoutSLO {
    scope: [CheckoutJourney]
    availability: "99.95%"
    throughput: "1200rps"
    rto: "15m"
    rpo: "5m"
}
```

## P2: Interface and UX Layer

### 9) API Contracts (Task Group 6.1)

What it is for:
- Defines request/response and error contracts in the same source of truth.

Task coverage:
- `6.1.1`..`6.1.5`

Mini spec:
- Add `api` declarations + `@rateLimit`.
- Validate method/path uniqueness and type references.
- Generate OpenAPI 3.x + docs + journey traceability links.

Example:
```chronos
api CreateOrder {
    method: POST
    path: "/orders"
    version: "v1"
    request: CreateOrderRequest
    response: OrderCreatedResponse
    errors: [PaymentDeclinedError, InventoryUnavailableError]
}
```

### 10) UI/UX and Accessibility Requirements (Task Group 6.2)

What it is for:
- Captures UX/accessibility constraints as executable requirements.

Task coverage:
- `6.2.1`..`6.2.4`

Mini spec:
- Add `view` plus `@accessibility` / `@responsive` traits.
- Validate references to journeys/steps and conformance values.

Example:
```chronos
@accessibility(standard: "WCAG-2.1-AA")
@responsive(breakpoints: ["mobile", "tablet", "desktop"])
view CheckoutPage {
    journey: CheckoutJourney
    step: ReviewCart
}
```

## P3: Documentation Automation System

### 11) Grammar Doc Extractor (Task Group D.1)

What it is for:
- Keeps syntax docs synchronized with grammar.

Task coverage:
- `D.1.1`..`D.1.8`

Example:
```bash
./gradlew :generateGrammarDocs
# outputs docs/generated/grammar-reference.md
```

### 12) Semantic Doc Extractor (Task Group D.2)

What it is for:
- Auto-docs validator rules, traits, and type catalog from code.

Task coverage:
- `D.2.1`..`D.2.7`

Example:
```bash
./gradlew :generateSemanticDocs
# outputs docs/generated/validator-rules.md and validator-rules.json
```

### 13) Railroad Diagram Generator (Task Group D.3)

What it is for:
- Improves onboarding through visual syntax diagrams.

Task coverage:
- `D.3.1`..`D.3.4`

Example:
```bash
./gradlew :generateRailroadDiagrams
# outputs docs/generated/diagrams/*.svg
```

### 14) Unified Docs Pipeline (Task Group D.4)

What it is for:
- Makes docs generation reproducible and CI-enforced.

Task coverage:
- `D.4.1`..`D.4.3`

Example:
```bash
./gradlew :generateDocs
```

## Additional Recommendations Not Explicit in Current Task List

### A1) Language Version Declaration

Why:
- Enables explicit compatibility policy and migration strategy.

Example:
```chronos
language 1.0
namespace com.example.checkout
```

### A2) Deprecation and Migration Metadata

Why:
- Allows controlled evolution without breaking users silently.

Example:
```chronos
@deprecated("Use CustomerV2")
entity CustomerLegacy { ... }
```

### A3) Package/Dependency Manifest + Lockfile

Why:
- Reproducible library imports and deterministic builds.

Example:
```toml
[dependencies]
com.acme.security = "1.3.2"
com.acme.payments = "2.1.0"
```

### A4) Import Aliasing and Conflict Resolution

Why:
- Avoids name collisions across imported namespaces.

Example:
```chronos
use com.a#Order as AOrder
use com.b#Order as BOrder
```

### A5) Stable Requirement IDs

Why:
- Required for audit traceability across docs, code, tests, and runtime.

Example:
```chronos
@id("REQ-CHECKOUT-001")
journey CheckoutJourney { ... }
```

### A6) Canonical Formatter (`chronos fmt`)

Why:
- Reduces diff noise and improves review quality.

Example:
```bash
chronos fmt src/models/
```

### A7) Linter (`chronos lint`)

Why:
- Catches style/governance issues beyond parser and validator errors.

Example:
```bash
chronos lint src/models/ --profile enterprise
```

### A8) Language Server Protocol (LSP)

Why:
- Editor completions, go-to-definition, hover docs, and inline diagnostics.

Example:
```json
{
  "capabilities": ["completion", "hover", "definition", "diagnostics"]
}
```

### A9) Parser/Validator Fuzz and Property Testing

Why:
- Hardens language reliability against malformed or adversarial inputs.

Example:
```bash
./gradlew :chronos-parser:fuzzTest
```

### A10) Performance and Scalability Gates

Why:
- Ensures compile/runtime behavior remains acceptable at scale.

Example:
```bash
./gradlew :bench:compileLargeModels
```

### A11) IR/Schema Versioning Contract

Why:
- Keeps downstream tools stable across compiler updates.

Example:
```json
{ "irSchemaVersion": "1.0.0", "model": { ... } }
```

### A12) Library Provenance/Signing

Why:
- Protects supply chain and enforces trusted requirement sources.

Example:
```bash
chronos verify-library com.acme.security@1.3.2 --signature
```

## Suggested Sequencing

1. **v1.0:** P0 + Reopen Items
2. **v1.1:** P1
3. **v1.2:** P2
4. **v1.3+:** P3 + A1..A12

## Coding Agent Prompt Pack

Use the prompts below directly with your coding agent. Each prompt assumes work is done in this repository and includes implementation + tests + docs updates.

### P0 Prompts

1. **Step Data Flow (3.2)**
```text
Implement Task Group 3.2 (Step Data Flow) end-to-end.
Scope:
- Add `input` and `output` blocks to step grammar.
- Extend syntax/IR models and compiler phases to carry typed input/output fields.
- Add validator rules for upstream data availability and unique output names within journey scope.
- Add generator output updates (PRD section/signatures and any relevant diagrams/tests).
Tests:
- Parser tests for valid/invalid input/output syntax.
- Compiler/validator tests for dependency and uniqueness errors.
- Integration test with a multi-step journey showing data flow.
Deliver:
- Code changes, tests, and a short diagnostic code mapping note if new CHR codes are introduced.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

2. **Authorization and Permissions (3.3)**
```text
Implement Task Group 3.3 (Authorization and Permissions).
Scope:
- Add grammar support for `role` declarations and allow/deny permission lists.
- Add `@authorize(role, permission)` trait support on journeys and steps.
- Implement validator checks for role existence, permission existence, actor-role compatibility, and deny-over-allow precedence.
- Generate authorization matrix output in documentation/test scaffold artifacts.
Tests:
- Parser tests for roles and authorize trait syntax.
- Validator tests for CHR-037..CHR-040 scenarios.
- End-to-end test showing a valid and invalid authorization model.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

3. **Typed Event Telemetry Baseline (4.2 partial)**
```text
Implement typed telemetry baseline by completing 4.2.1 + 4.2.2.
Scope:
- Add top-level `event` declarations with typed payload fields.
- Require step telemetry references to declared/imported event types.
- Add validation for unknown telemetry event references.
Tests:
- Grammar/parser tests for event declarations.
- Validator tests for valid telemetry references and unknown-event failures.
- PRD/generator update test to ensure event references are rendered.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

### Reopen Item Prompts

4. **R1: Expression Parser Hardening**
```text
Replace lexical invariant validation with a real expression parser + typed checker.
Scope:
- Introduce an expression AST (operators, literals, field refs, aggregate funcs, lambda params).
- Parse invariant expressions into AST instead of token splitting.
- Type-check expressions against entity/global scope and function signatures.
Tests:
- Positive/negative expression coverage including nested expressions and lambda scopes.
- Backward compatibility tests for existing invariant examples.
- Error message quality tests for undefined fields and type mismatches.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

5. **R2: State/Enum Compatibility Tightening**
```text
Strengthen statemachine validation to enforce explicit compatibility between declared states and bound enum members.
Scope:
- Define and implement policy (equal set or allowed subset with explicit rule).
- Validate missing/extra states relative to enum.
- Improve diagnostics with actionable messages.
Tests:
- Matching enum/states passes.
- Unknown state and enum mismatch failures.
- Imported entity/enum cross-file case coverage.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

6. **R3: TransitionTo Semantic Strengthening**
```text
Upgrade TransitionTo validation from state existence to machine-aware semantics.
Scope:
- Associate journey/step TransitionTo with relevant statemachine context.
- Validate allowed target states and (if applicable) allowed transition edges.
- Keep multi-file behavior deterministic.
Tests:
- Valid TransitionTo target passes.
- Invalid target and invalid edge cases fail with clear diagnostics.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

7. **R4: Diagnostic Code Registry Normalization**
```text
Normalize diagnostic code ownership and eliminate code meaning overlap.
Scope:
- Create/update a canonical diagnostic registry table.
- Ensure one CHR code = one meaning across parser/compiler/validator/docs.
- Refactor conflicting codes and update tests/docs.
Tests:
- Registry integrity test (uniqueness + ownership).
- Existing diagnostic tests updated to canonical codes.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

8. **R5: Spec/Doc Drift Guardrails**
```text
Add guardrails to prevent docs/rules drift.
Scope:
- Validate docs quick-reference examples against parser in CI.
- Add checks for enum-like allowed values documented vs validator-enforced values where feasible.
- Update inconsistent docs to match canonical behavior.
Tests:
- Failing fixture for intentionally invalid doc snippet.
- Passing fixtures for valid docs examples.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

### P1 Prompts

9. **Temporal Requirements (4.1)**
```text
Implement full Task Group 4.1 (Temporal and Time-Based Requirements).
Scope:
- Duration literal syntax in lexer/parser.
- `@timeout(duration, onExpiry)` support and validation.
- `@ttl(duration, action)` support and validation.
- Scheduled trigger support with cron validation.
- Temporal reference validation against state machines/variants.
- SLA summary generation updates.
Tests:
- Unit + integration tests for valid/invalid duration, cron, and onExpiry references.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

10. **Events/Reactions Full (4.2)**
```text
Implement full Task Group 4.2 (Events, Reactions, and System-Initiated Flows).
Scope:
- Add `event` payloads, `reaction` blocks, trigger semantics, and cross-reference validation.
- Validate event references across journeys/reactions/state-machine actions.
- Generate event catalog and event-flow docs/artifacts.
Tests:
- Parser tests for reaction grammar.
- Validator tests for missing trigger/actor and invalid event references.
- Generator snapshot/integration tests for event catalog output.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

11. **Parallel Flows (5.1)**
```text
Implement Task Group 5.1 (Concurrency and Parallel Flows).
Scope:
- Add `parallel` block grammar with join strategies.
- Validator checks for branch count, join validity, and branch dependency constraints.
- Data-flow analysis updates for post-join output availability.
- Diagram/scaffold generation updates.
Tests:
- Parser tests for parallel syntax.
- Validator tests for CHR-049..CHR-052.
- Integration tests demonstrating join semantics.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

12. **Journey Composition (5.2)**
```text
Implement Task Group 5.2 (Inter-Journey Dependencies and Composition).
Scope:
- Add `@requires` and `Invoke(...)` semantics.
- Build dependency graph and cycle detection.
- Validate actor compatibility for invoked journeys.
- Generate dependency diagrams and ordered test scaffolding.
Tests:
- Cycle detection tests.
- Missing target journey tests.
- Actor compatibility positive/negative tests.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

13. **Expanded NFRs (5.3)**
```text
Implement Task Group 5.3 (Expanded Non-Functional Requirements).
Scope:
- Add NFR traits and system-level `nfr` block syntax.
- Validate percentage/rate/duration formats and scope references.
- Generate NFR summary docs and performance-test baseline scaffolds.
Tests:
- Parser tests for NFR syntax.
- Validator tests for format and scope failures.
- Generator output tests for NFR sections.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

### P2 Prompts

14. **API Contracts (6.1)**
```text
Implement Task Group 6.1 (Interface and API Contracts).
Scope:
- Add `api` declarations with method/path/version/request/response/errors.
- Add `@rateLimit`.
- Validate method/path uniqueness and referenced types/errors.
- Generate OpenAPI 3.x and API docs with journey traceability.
Tests:
- Parser + validator tests for CHR-061..CHR-064.
- OpenAPI snapshot/integration tests.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

15. **UI/UX and Accessibility (6.2)**
```text
Implement Task Group 6.2 (UI/UX and Accessibility Requirements).
Scope:
- Add `view` construct and `@accessibility`/`@responsive` traits.
- Validate journey/step references and accessibility/responsive enums.
- Generate accessibility checklist and component spec output.
Tests:
- Parser tests for view/trait syntax.
- Validator tests for CHR-065..CHR-068.
- Generator tests for accessibility output sections.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

### P3 Prompts

16. **Grammar Doc Extractor (D.1)**
```text
Implement Task Group D.1 (Grammar Doc Extractor) with CI enforcement.
Scope:
- Parse structured doc comments from grammar files.
- Extract rule signatures and build doc model by category.
- Render markdown pages + keyword index.
- Add `:generateGrammarDocs` task and CI checks for missing rule docs.
Tests:
- Unit tests for doc parser and signature extraction.
- Golden/snapshot tests for generated markdown.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

17. **Semantic Doc Extractor (D.2)**
```text
Implement Task Group D.2 (Semantic Doc Extractor).
Scope:
- Add `@DocExport` and `@DocParam` annotations.
- Annotate traits/rules/types.
- Implement reflection scanner + renderers for trait/rule/type docs.
- Add `:generateSemanticDocs` task and CI checks for missing annotations.
Tests:
- Annotation scanning tests.
- Output snapshot tests for generated docs.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

18. **Railroad Diagram Generator (D.3)**
```text
Implement Task Group D.3 (Railroad Diagram Generator).
Scope:
- Build ANTLR AST -> EBNF converter.
- Integrate SVG railroad renderer.
- Generate one diagram per selected parser rule.
- Embed diagram links in generated markdown.
Tests:
- Converter unit tests.
- Snapshot tests for generated SVG filenames/references.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

19. **Unified Docs Pipeline (D.4)**
```text
Implement Task Group D.4 (Unified Doc Pipeline).
Scope:
- Add `:generateDocs` aggregate task depending on grammar/semantic/railroad generators.
- Wire into main build/check lifecycle.
- Validate all `@example` blocks by parsing them in CI.
Tests:
- Build task integration tests.
- Example-validation tests with positive and negative fixtures.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

### Additional Recommendation Prompts (A1..A12)

20. **A1 Language Version Declaration**
```text
Add explicit language version declaration support.
Scope:
- Add optional/required `language <semver>` header syntax.
- Validate supported language versions.
- Thread version into parse/IR metadata for future compatibility logic.
Tests:
- Parser tests for valid/invalid versions.
- Compatibility tests for unsupported version diagnostics.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

21. **A2 Deprecation/Migration Metadata**
```text
Add deprecation metadata support for language constructs.
Scope:
- Introduce `@deprecated(reason)` trait behavior.
- Emit diagnostics/warnings for deprecated construct usage.
- Generate deprecation sections in docs output.
Tests:
- Validator tests for deprecated usage reporting.
- Generator tests for deprecation annotations in docs.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

22. **A3 Dependency Manifest + Lockfile**
```text
Implement package/dependency manifest and lockfile support for Chronos libraries.
Scope:
- Define manifest format (`dependencies`, versions, sources).
- Resolve dependencies deterministically and emit lockfile.
- Integrate with compile/build commands.
Tests:
- Resolver tests for version selection and lockfile reproducibility.
- Integration tests for offline/locked builds.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

23. **A4 Import Aliasing**
```text
Add import aliasing and conflict resolution syntax.
Scope:
- Extend `use` syntax to support aliasing.
- Update resolver precedence and ambiguity diagnostics.
- Ensure aliases are reflected in diagnostics and generated docs.
Tests:
- Parser tests for alias syntax.
- Resolver tests for collision and alias lookup behavior.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

24. **A5 Stable Requirement IDs**
```text
Add stable requirement ID support across key declarations.
Scope:
- Add `@id("...")` trait validation with uniqueness constraints.
- Propagate IDs into generated artifacts and test scaffolds.
- Provide diagnostic for missing IDs under strict mode.
Tests:
- Uniqueness tests.
- End-to-end traceability tests (ID appears in PRD + scaffolds).
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

25. **A6 Canonical Formatter (`chronos fmt`)**
```text
Implement `chronos fmt`.
Scope:
- Define canonical formatting rules for all constructs.
- Add CLI command to format files/directories deterministically.
- Ensure idempotent formatting.
Tests:
- Golden formatting tests.
- Idempotence tests (`fmt` twice yields same output).
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

26. **A7 Linter (`chronos lint`)**
```text
Implement `chronos lint` with profile support.
Scope:
- Add non-fatal style/governance rules distinct from compiler errors.
- Support profile-based rule bundles (default, enterprise).
- Provide machine-readable lint output option.
Tests:
- Rule coverage tests.
- Profile behavior tests.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

27. **A8 Language Server (LSP)**
```text
Create a Chronos language server.
Scope:
- Diagnostics, completion, hover docs, go-to-definition, symbol references.
- Incremental parsing and workspace-wide symbol index.
- Integrate rule metadata for richer editor feedback.
Tests:
- Protocol integration tests for core LSP methods.
- Workspace multi-file symbol resolution tests.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

28. **A9 Fuzz/Property Testing**
```text
Add fuzz and property-based tests for parser/compiler/validator.
Scope:
- Build corpus + mutational fuzz harness for grammar/parser.
- Add property tests for resolver invariants and diagnostic stability.
- Integrate into CI with bounded runtime.
Tests:
- Seed corpus tests.
- Regression harness for crashing inputs.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

29. **A10 Performance/Scale Gates**
```text
Add performance and scalability gates.
Scope:
- Define benchmark suites (small/medium/large model sets).
- Track compile latency/memory budgets in CI.
- Alert on regressions against baseline thresholds.
Tests:
- Benchmark harness smoke tests.
- Threshold-check tests for CI pass/fail behavior.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

30. **A11 IR Schema Versioning Contract**
```text
Implement explicit IR schema versioning and compatibility checks.
Scope:
- Embed `irSchemaVersion` in serialized artifacts.
- Provide compatibility policy for readers/writers.
- Add migration hooks or adapters for schema changes.
Tests:
- Backward/forward compatibility tests.
- Serialization contract tests.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```

31. **A12 Library Provenance and Signing**
```text
Add provenance/signature verification for imported requirement libraries.
Scope:
- Define signature metadata format and trust roots.
- Verify signatures during dependency resolution.
- Provide CLI verification command and policy modes (warn/fail).
Tests:
- Valid/invalid signature tests.
- Trust root and revoked key behavior tests.
Verification requirements:
- Run `./gradlew test`.
- Run `./gradlew check` to enforce import/module boundary audits.
- Confirm no dependency boundary violations (including `verifyArchBoundaries` and `verifyNoLegacyImports`) before marking complete.
```
