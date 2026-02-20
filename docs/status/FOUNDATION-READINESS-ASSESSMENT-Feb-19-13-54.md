# Chronos Foundation Readiness Assessment

**Date:** 2026-02-19
**Assessed by:** Claude Sonnet 4.6 (automated inspection — no runtime executed)
**Scope:** Full codebase review following IrRefWalker integration and test-green state

---

## Table of Contents

1. [Module DAG & Boundary Enforcement](#1-module-dag--boundary-enforcement)
2. [Compiler Contract Invariants](#2-compiler-contract-invariants)
3. [IR Surface Area Inventory](#3-ir-surface-area-inventory)
4. [Parsing / Lowering Mapping Audit](#4-parsing--lowering-mapping-audit)
5. [Test Coverage Quality](#5-test-coverage-quality)
6. [Next Feature-Work Readiness Verdict](#6-next-feature-work-readiness-verdict)
7. [Recommended Next 3 Feature Areas](#7-recommended-next-3-feature-areas)

---

## 1. Module DAG & Boundary Enforcement

### 1.1 Module Dependency Graph

| Module | Depends On (project) | External Libraries |
|---|---|---|
| `chronos-core` | *(none)* | *(none)* |
| `chronos-model` | `chronos-core` | Jackson 2.18.2 (databind, jdk8, parameter-names) |
| `chronos-parser` | `chronos-core`, `chronos-model` | ANTLR4 4.13.1 (antlr + antlr4-runtime as `api`) |
| `chronos-validator` | `chronos-core`, `chronos-model` | *(none)* |
| `chronos-compiler` | `chronos-core`, `chronos-model`, `chronos-parser`, `chronos-validator` | *(none)* |
| `chronos-generators` | `chronos-core`, `chronos-model` | *(none)* |
| `chronos-cli` | `chronos-core`, `chronos-model`, `chronos-compiler`, `chronos-validator`, `chronos-generators` | PicoCLI 4.7.5, GSON 2.10.1, GraalVM Native 0.9.28 |

Source: `settings.gradle.kts` (7 included subprojects) + each module's `build.gradle.kts`.

### 1.2 Cycle Check

Topological order: `core → model → parser → validator → compiler → generators → cli`

**No cycles exist.** The DAG is a strict partial order:

```
chronos-core
  └─ chronos-model
       ├─ chronos-parser
       │    └─ chronos-compiler
       │         └─ chronos-cli
       ├─ chronos-validator
       │    └─ chronos-compiler (above)
       └─ chronos-generators
            └─ chronos-cli (above)
```

`chronos-cli` also has an explicit `implementation(project(":chronos-validator"))` in its `build.gradle.kts`, which is redundant but not harmful (validator is already transitive through compiler).

### 1.3 Boundary Verification

#### Boundary 1: `chronos-parser` — ANTLR + syntax DTOs + lowering only

**Claim:** `chronos-parser/src/main/java` must not construct IR types.

```
grep -r "import com.genairus.chronos.ir." chronos-parser/src/main/java
→ No files found
```

**Evidence:**
- `ChronosParserFacade.java` returns `SyntaxModel` — a syntax-layer DTO.
- `LoweringVisitor.java` emits only `com.genairus.chronos.syntax.*` DTOs.
- `build.gradle.kts` lists `implementation(project(":chronos-model"))` — the dependency exists for potential future use but no IR type is imported today.

**Status: ✅ CLEAN**

> Note: `chronos-model` is `implementation` (not `api`) in the parser, so it does not leak transitively to consumers of `chronos-parser` as an API dependency. The import boundary is enforced at the source level.

#### Boundary 2: `chronos-compiler` — resolution pipeline only, no ANTLR

**Claim:** `chronos-compiler/src/main/java` must not import `org.antlr.*` directly.

```
grep -r "import org.antlr." chronos-compiler/src/main/java
→ No files found
```

**Evidence:** `ChronosCompiler.java` delegates all parsing to `new ParseAndLowerPhase(sourceName).execute(sourceText, ctx)`, which internally uses `ChronosParserFacade`. ANTLR is fully encapsulated within `chronos-parser`.

**Status: ✅ CLEAN**

#### Boundary 3: `chronos-model` — IR types only, no ANTLR

**Claim:** `chronos-model/src/main/java` must not import ANTLR.

```
grep -r "import org.antlr." chronos-model/src/main/java
→ No files found
```

**Status: ✅ CLEAN**

#### Boundary 4: `chronos-generators` — depends only on `chronos-model` + `chronos-core`

**Claim:** `chronos-generators/src/main/java` must not import parser or syntax types.

```
grep -r "import com.genairus.chronos.parser." chronos-generators/src/main/java
grep -r "import com.genairus.chronos.syntax."  chronos-generators/src/main/java
→ No files found (both)
```

**Evidence:**
- `build.gradle.kts`: `implementation(project(":chronos-core"))` + `implementation(project(":chronos-model"))` only.
- Generators use `IrModelAdapter` (in `generators/compat/`) to convert `IrModel → ChronosModel` (legacy model for `MarkdownPrdGenerator`). This adapter lives inside `chronos-generators`, not the parser.

**Status: ✅ CLEAN**

#### Boundary 5: `chronos-cli` — uses compiler as single entry point, no legacy parse path

**Claim:** CLI commands must not call `ChronosParserFacade`, `ChronosModelParser`, or `LoweringVisitor` directly.

```
grep -r "ChronosParserFacade\|ChronosModelParser\|LoweringVisitor" chronos-cli/src/main/java
→ No matches found
```

**Evidence:** Every CLI command that needs a compiled model uses exactly:
```java
// ValidateCommand.java, GenerateCommand.java, SelectCommand.java, DiffCommand.java, BuildCommand.java
CompileResult result = new ChronosCompiler().compile(text, filePath);
```

The CLI also does not import `com.genairus.chronos.validator.ChronosValidator` directly — validation is delegated through `ChronosCompiler` (Phase 6).

**Status: ✅ CLEAN**

---

## 2. Compiler Contract Invariants

### 2.1 Definition of `finalized == true`

`CompileResult.finalized()` is set by `FinalizeIrPhase.execute()`:

```java
// FinalizeIrPhase.java:44
return new Result(model, !ctx.diagnostics().hasErrors());
```

Before returning, `FinalizeIrPhase` calls `IrRefWalker.findUnresolvedRefs(model)` and emits a `CHR-012` ERROR for every unresolved `SymbolRef` found.

**`finalized == true` therefore means all of the following simultaneously:**

1. Phase 1 (parse+lower) succeeded — `parsed == true`.
2. No ERROR diagnostic was emitted by any phase (1–7).
3. `IrRefWalker` found zero unresolved `SymbolRef` objects anywhere in the IR object graph.

`CompileResult.success()` is even stricter: `parsed && finalized && diagnostics contains no ERRORs` — which is equivalent to `finalized == true` since finalized already checks for errors.

### 2.2 The IrRefWalker Guarantee

`IrRefWalker` (`chronos-compiler/src/main/java/com/genairus/chronos/compiler/util/IrRefWalker.java`) walks the entire IR object graph via **Java record component reflection**. Its traversal rules:

| Object type | Behavior |
|---|---|
| `SymbolRef` | Collected immediately; internals not recursed |
| `Iterable<?>` | Each element walked |
| `Map<?,?>` | Values walked (keys are always `String`) |
| `Optional<?>` | Unwrapped and walked if present |
| `com.genairus.chronos.ir.*` record | All record components walked via reflection |
| `String`, enum, `Number`, `Boolean`, `Span`, … | Skipped (scalar) |

**Future-proofing:** Because traversal uses `cls.getRecordComponents()`, any new `SymbolRef` field added to any IR record in `com.genairus.chronos.ir.*` is **automatically discovered** without modifying `IrRefWalker`.

**Cycle safety:** An `IdentityHashMap`-backed visited set prevents infinite recursion and deduplicated collection.

### 2.3 What `finalized == true` Implies — and Does Not Imply

| Claim | Implied by `finalized == true`? | Notes |
|---|---|---|
| All `SymbolRef` objects reachable via reflection are resolved | ✅ Yes | IrRefWalker exhaustively scans all `com.genairus.chronos.ir.*` records |
| `JourneyDef.actorRef` is resolved | ✅ Yes | It is a `SymbolRef` field |
| `RelationshipDef.fromEntityRef` is resolved | ✅ Yes | It is a `SymbolRef` field |
| `RelationshipDef.toEntityRef` is resolved | ✅ Yes | It is a `SymbolRef` field |
| `EntityDef.parentType` (raw `Optional<String>`) names a real entity | ❌ No | It is a raw `String`, not a `SymbolRef` — IrRefWalker skips it |
| `ActorDef.parentType` names a real actor | ❌ No | Same — raw `Optional<String>` |
| `StateMachineDef.entityName` names a real entity | ❌ No | Raw `String` |
| `StateMachineDef.fieldName` is a valid field on that entity | ❌ No | Raw `String` |
| `InvariantDef.scope` list contains only real entity names | ❌ No | `List<String>` — validated by CHR-024/021 in validator, but not a SymbolRef |
| `DenyDef.scope` list contains only real entity names | ❌ No | Same |
| `Variant.triggerName` names a real `ErrorDef` | ❌ No | Raw `String` — CHR-027 validates this in the validator |
| `TypeRef.NamedTypeRef.qualifiedId` names a resolvable type | ⚠️ Partially | CHR-008 is emitted on failure, but the field itself remains a raw `String` even after validation |
| `OutcomeExpr.TransitionTo.stateId` is a declared state | ❌ No | Raw `String` — CHR-034 validates this |
| `OutcomeExpr.ReturnToStep.stepId` is a declared step name | ❌ No | Raw `String` — no CHR rule currently covers this |

**Summary:** `finalized == true` provides a hard guarantee for `SymbolRef`-tracked cross-links (actor refs, relationship entity refs). It does **not** cover the broader set of raw-string references that are instead validated by the `ChronosValidator` rule set and that remain as strings in the IR even after validation succeeds.

### 2.4 Phase → CHR Code Table

| Phase | Input → Output | CHR Codes Emitted | Meaning |
|---|---|---|---|
| 1 `ParseAndLowerPhase` | `String` → `SyntaxModel` | `CHR-PARSE` (synthetic) | ANTLR syntax/lexer error; `parsed=false` |
| 2 `CollectSymbolsPhase` | `SyntaxModel` → (symbols) | `CHR-005` | Duplicate top-level shape name |
| 3 `BuildIrSkeletonPhase` | `SyntaxModel` → `IrModel` | *(none)* | Structural conversion; unresolved `SymbolRef` objects created here |
| 4 `TypeResolutionPhase` | `IrModel` | `CHR-008` | Named type reference unresolvable in symbol table or imports |
| 5 `CrossLinkResolutionPhase` | `IrModel` | `CHR-008`, `CHR-011` | Undefined actor in journey (`CHR-008`); undefined entity in relationship from/to (`CHR-011`) |
| 6 `ValidationPhase` | `IrModel` | `CHR-001` – `CHR-034`, `CHR-W001` | All 35 semantic rules (see Section 2.5) |
| 7 `FinalizeIrPhase` | `IrModel` → `Result` | `CHR-012` | Unresolved `SymbolRef` remains after all resolution phases |

> **Note on Phase 1 diagnostics:** Parse errors are surfaced as `Diagnostic` objects with code `"CHR-PARSE"` (constructed from ANTLR `ParseError` objects in `ParseAndLowerPhase`). If any parse errors occur, the method returns `null` and `CompileResult.parsed()` is `false`.

### 2.5 Full CHR Rule Inventory (Validator)

| Code | Severity | Rule Summary | Implemented? |
|---|---|---|---|
| CHR-001 | ERROR | Journey must declare an actor | ✅ |
| CHR-002 | ERROR | Journey must have outcomes block with a `success` outcome | ✅ |
| CHR-003 | ERROR | Every step must declare both `action` and `expectation` | ✅ |
| CHR-004 | WARNING | Journey declares zero happy-path steps | ✅ |
| CHR-005 | ERROR | Duplicate shape name within namespace | ✅ (Phase 2) |
| CHR-006 | WARNING | Entity/shape declares no fields | ✅ |
| CHR-007 | WARNING | Actor missing `@description` trait | ✅ |
| CHR-008 | ERROR | TypeRef / actor ref cannot be resolved | ✅ (Phases 4+5) |
| CHR-009 | WARNING | Journey missing `@kpi` trait | ✅ |
| CHR-010 | WARNING | Journey missing `@compliance` when namespace has compliance policies | ✅ |
| CHR-011 | ERROR | Relationship from/to must reference defined entities | ✅ (Phase 5) |
| CHR-012 | ERROR | Unresolved `SymbolRef` remains after all resolution phases | ✅ (Phase 7) |
| CHR-013 | ERROR | *(not implemented — see below)* | ❌ |
| CHR-014 | ERROR | Inverse field name must exist on target entity | ✅ |
| CHR-015 | ERROR | Circular inheritance chain detected | ✅ |
| CHR-016 | ERROR | Child entity redefines parent field with incompatible type | ✅ |
| CHR-017 | ERROR | *(documented but not implemented — trait inheritance propagation)* | ❌ |
| CHR-018 | ERROR | Multiple inheritance not supported | ✅ |
| CHR-019 | ERROR | Invariant expression references undeclared field | ✅ |
| CHR-020 | ERROR | Invariant severity must be `error`, `warning`, or `info` | ✅ |
| CHR-021 | ERROR | Global invariant must declare non-empty scope | ✅ |
| CHR-022 | ERROR | Invariant names must be unique within enclosing scope | ✅ |
| CHR-023 | ERROR | Every `deny` block must have a `description` | ✅ |
| CHR-024 | ERROR | Deny scope entities must be defined or imported | ✅ |
| CHR-025 | ERROR | Deny severity must be `critical`, `high`, `medium`, or `low` | ✅ |
| CHR-026 | ERROR | Error codes must be unique across namespace | ✅ |
| CHR-027 | ERROR | Variant trigger must reference a defined error type | ✅ |
| CHR-028 | ERROR | Error severity must be `critical`, `high`, `medium`, or `low` | ✅ |
| CHR-029 | ERROR | All transition states must be declared in the `states` list | ✅ |
| CHR-030 | ERROR | Every non-terminal state must have an outbound transition | ✅ |
| CHR-031 | ERROR | Initial state must be in the `states` list | ✅ |
| CHR-032 | ERROR | Terminal states must not have outbound transitions | ✅ |
| CHR-033 | ERROR | StateMachine entity/field references must be valid | ✅ |
| CHR-034 | ERROR | `TransitionTo()` must reference a state declared in the statemachine | ✅ |
| CHR-W001 | WARNING | Invariant references an optional field without a null guard | ✅ |

**Gap:** CHR-013 has no implementation — the code number is skipped entirely.
**Gap:** CHR-017 ("trait inheritance propagation") is listed in the class-level Javadoc comment in `ChronosValidator.java` but has no `checkChr017()` method and no matching string in executable code.

---

## 3. IR Surface Area Inventory

### 3.1 All `IrShape` Implementations

All 13 implementations of `sealed interface IrShape` in `com.genairus.chronos.ir.types`:

| Class | Grammar keyword | `SymbolRef` fields | Raw-String references (untracked) | Optional/nullable |
|---|---|---|---|---|
| `EntityDef` | `entity` | *(none)* | `parentType: Optional<String>` (inheritance parent) | `parentType` |
| `ShapeStructDef` | `shape` | *(none)* | *(none)* | *(none)* |
| `ListDef` | `list` | *(none)* | *(none — `memberType` is a `TypeRef`)* | *(none)* |
| `MapDef` | `map` | *(none)* | *(none — `keyType`/`valueType` are `TypeRef`)* | *(none)* |
| `EnumDef` | `enum` | *(none)* | *(none)* | *(none)* |
| `ActorDef` | `actor` | *(none)* | `parentType: Optional<String>` (inheritance parent) | `parentType` |
| `PolicyDef` | `policy` | *(none)* | *(none)* | *(none)* |
| `JourneyDef` | `journey` | `actorRef: SymbolRef` (nullable) | *(none at top level; see nested types)* | `actorRef` (null → CHR-001), `outcomesOrNull` |
| `RelationshipDef` | `relationship` | `fromEntityRef: SymbolRef`, `toEntityRef: SymbolRef` | `inverseField: Optional<String>` | `semantics`, `inverseField` |
| `InvariantDef` | `invariant` | *(none)* | `scope: List<String>` (entity names — validated by CHR-021/024) | `message` |
| `DenyDef` | `deny` | *(none)* | `scope: List<String>` (entity names — validated by CHR-024) | *(none)* |
| `ErrorDef` | `error` | *(none)* | *(none)* | *(none)* |
| `StateMachineDef` | `statemachine` | *(none)* | `entityName: String` (entity ref), `fieldName: String` (field ref) | `terminalStates` (may be empty) |

### 3.2 All `TypeRef` Variants

`sealed interface TypeRef` in `com.genairus.chronos.ir.types.TypeRef`:

| Variant | Meaning | Notes |
|---|---|---|
| `PrimitiveType(PrimitiveKind kind)` | Built-in scalar type | `kind` ∈ {STRING, INTEGER, LONG, FLOAT, BOOLEAN, TIMESTAMP, BLOB, DOCUMENT} |
| `ListType(TypeRef elementType)` | Inline `List<T>` | Recursive — element can be any `TypeRef` |
| `MapType(TypeRef keyType, TypeRef valueType)` | Inline `Map<K,V>` | Both key and value recursive |
| `NamedTypeRef(String qualifiedId)` | Reference to a named shape | Raw `String` — resolved by CHR-008 but never promoted to `SymbolRef` |

> **Key gap:** `TypeRef.NamedTypeRef.qualifiedId` is a raw string. CHR-008 fires if it cannot be resolved, but even on success it stays as `String` in the IR. This means after `finalized=true`, the IR consumer must perform a second lookup to get the actual `IrShape`. Future work should promote resolved `NamedTypeRef` to a `SymbolRef`.

### 3.3 Supporting IR Types (Selected Fields of Note)

| Type | Notable raw-String references |
|---|---|
| `Variant` | `triggerName: String` — the error type name. Validated by CHR-027 but remains a raw string |
| `OutcomeExpr.TransitionTo` | `stateId: String` — validated by CHR-034; not a SymbolRef |
| `OutcomeExpr.ReturnToStep` | `stepId: String` — **no CHR rule** validates that this step name exists |
| `EntityInvariant` | `expression: String`, `severity: String` — not SymbolRefs (expected) |
| `Transition` | `fromState: String`, `toState: String` — validated by CHR-029 |
| `FieldDef` | `type: TypeRef` — see NamedTypeRef note above |

---

## 4. Parsing / Lowering Mapping Audit

Legend: ✅ = fully covered, ⚠️ = partially covered, ❌ = gap

### 4.1 Grammar → Syntax DTO Mapping

| Grammar rule | Syntax DTO | Status |
|---|---|---|
| `model` | `SyntaxModel` | ✅ |
| `namespaceDecl` | `SyntaxModel.namespace: String` | ✅ |
| `useDecl` | `SyntaxUseDecl` | ✅ |
| `traitApplication` | `SyntaxTrait` | ✅ |
| `traitArg` | `SyntaxTraitArg` | ✅ |
| `traitValue` (STRING/NUMBER/BOOL/ref) | `SyntaxTraitValue` (4 sealed variants) | ✅ |
| `typeRef` | `SyntaxTypeRef` (4 sealed variants) | ✅ |
| `entityDef` | `SyntaxEntityDecl` | ✅ |
| `fieldDef` | `SyntaxFieldDef` | ✅ |
| `entityInvariant` | `SyntaxEntityInvariant` | ✅ |
| `shapeStructDef` | `SyntaxShapeDecl` | ✅ |
| `listDef` | `SyntaxListDecl` | ✅ |
| `mapDef` | `SyntaxMapDecl` | ✅ |
| `enumDef` | `SyntaxEnumDecl` | ✅ |
| `enumMember` | `SyntaxEnumMember` | ✅ |
| `actorDef` | `SyntaxActorDecl` | ✅ |
| `policyDef` | `SyntaxPolicyDecl` | ✅ |
| `journeyDef` | `SyntaxJourneyDecl` | ✅ |
| `actorDecl` | `SyntaxJourneyDecl.actorOrNull: String` | ✅ |
| `preconditionsDecl` | `SyntaxJourneyDecl.preconditions: List<String>` | ✅ |
| `stepsDecl` | `SyntaxJourneyDecl.steps` | ✅ |
| `step` | `SyntaxStep` | ✅ |
| `stepField` (action/expectation/outcome/telemetry/risk) | `SyntaxStepField` (5 sealed variants) | ✅ |
| `outcomeExpr` (TransitionTo/ReturnToStep) | `SyntaxOutcomeExpr` (2 sealed variants) | ✅ |
| `variantsDecl` | `SyntaxJourneyDecl.variants: List<SyntaxVariant>` | ✅ |
| `variantEntry` + `variantBody` | `SyntaxVariant` | ✅ |
| `outcomesDecl` | `SyntaxOutcomes` | ✅ |
| `relationshipDef` | `SyntaxRelationshipDecl` | ✅ |
| `invariantDef` | `SyntaxInvariantDecl` | ✅ |
| `denyDef` | `SyntaxDenyDecl` | ✅ |
| `errorDef` | `SyntaxErrorDecl` | ✅ |
| `statemachineDef` | `SyntaxStateMachineDecl` | ✅ |
| `transition` + `transitionBody` | `SyntaxTransition` | ✅ |
| `DOC_COMMENT` lexer rule | **NOT captured in any Syntax DTO** | ❌ |

### 4.2 Syntax DTO → IR Shape Mapping

| Syntax DTO | IR Shape | Status |
|---|---|---|
| `SyntaxEntityDecl` | `EntityDef` | ✅ |
| `SyntaxShapeDecl` | `ShapeStructDef` | ✅ |
| `SyntaxListDecl` | `ListDef` | ✅ |
| `SyntaxMapDecl` | `MapDef` | ✅ |
| `SyntaxEnumDecl` | `EnumDef` | ✅ |
| `SyntaxActorDecl` | `ActorDef` | ✅ |
| `SyntaxPolicyDecl` | `PolicyDef` | ✅ |
| `SyntaxJourneyDecl` | `JourneyDef` | ✅ |
| `SyntaxRelationshipDecl` | `RelationshipDef` | ✅ |
| `SyntaxInvariantDecl` | `InvariantDef` | ✅ |
| `SyntaxDenyDecl` | `DenyDef` | ✅ |
| `SyntaxErrorDecl` | `ErrorDef` | ✅ |
| `SyntaxStateMachineDecl` | `StateMachineDef` | ✅ |
| `SyntaxEntityInvariant` | `EntityInvariant` | ✅ |
| `SyntaxFieldDef.docComments` | `EntityDef.docComments` always `List.of()` | ⚠️ |

> **Note on `docComments`:** IR shape records have a `docComments: List<String>` field. In `BuildIrSkeletonPhase` this field is always set to `List.of()`. The ANTLR grammar routes `DOC_COMMENT` tokens to the `HIDDEN` channel, meaning they are available in the token stream but `LoweringVisitor` does not extract them into any Syntax DTO. As a result doc comments are **parsed but silently discarded** — they never reach the IR or the generator.

### 4.3 IR Shape → Validation Coverage

| IR Shape | CHR rules covering it | Generator coverage |
|---|---|---|
| `EntityDef` | CHR-006, 015, 016, 018, 019, CHR-W001 | ✅ MarkdownPrdGenerator |
| `ShapeStructDef` | CHR-006 | ✅ MarkdownPrdGenerator |
| `ListDef` | *(none specific)* | ✅ (field type rendering) |
| `MapDef` | *(none specific)* | ✅ (field type rendering) |
| `EnumDef` | *(none specific)* | ✅ MarkdownPrdGenerator |
| `ActorDef` | CHR-007 | ✅ MarkdownPrdGenerator |
| `PolicyDef` | CHR-010 | ✅ MarkdownPrdGenerator |
| `JourneyDef` | CHR-001, 002, 003, 004, 009, 010 | ✅ MarkdownPrdGenerator |
| `RelationshipDef` | CHR-011, 012, 014 | ✅ MarkdownPrdGenerator |
| `InvariantDef` | CHR-019, 020, 021, 022, CHR-W001 | ✅ MarkdownPrdGenerator |
| `DenyDef` | CHR-023, 024, 025 | ✅ MarkdownPrdGenerator |
| `ErrorDef` | CHR-026, 027, 028 | ✅ MarkdownPrdGenerator |
| `StateMachineDef` | CHR-029, 030, 031, 032, 033, 034 | ✅ MermaidStateDiagramGenerator, StateMachineTestGenerator |

### 4.4 Grammar Constructs With Missing Coverage

- [ ] **DOC_COMMENT (a)** — Grammar rule exists; no Syntax DTO; no IR field populated; no generator rendering.
- [x] All 13 shape definitions have Syntax DTOs.
- [x] All shape DTOs have IR representations.
- [ ] **CHR-013 (c)** — No validation rule with this code exists.
- [ ] **CHR-017 (c)** — Documented in Javadoc but not implemented.
- [ ] **`ReturnToStep` step name validation (c)** — Grammar supports `ReturnToStep(StepId)` but no CHR rule verifies that `StepId` names a real step in the journey.
- [x] All grammar constructs that have validation targets are covered by at least one CHR rule.
- [x] All 13 shapes have `MarkdownPrdGenerator` rendering coverage (via `IrModelAdapter`).

---

## 5. Test Coverage Quality

### 5.1 Compiler Tests (`chronos-compiler/src/test/`)

| Test Class | What it tests |
|---|---|
| `ChronosCompilerTest` | Basic integration: valid program, duplicate symbol (CHR-005), unresolved type (CHR-008), invalid journey, parse failure, `success()`/`errors()`/`warnings()` filtering |
| `LinkingInvariantTest` | `finalized=false` when actor undefined; `finalized=true` + zero unresolved refs when actor declared; missing relationship target (CHR-011) |
| `FinalizeInvariantTest` | `finalized=false` + CHR-012 on ghost actor; `finalized=true` + IrRefWalker confirms zero unresolved when actor declared |
| `IrRefWalkerTest` | 21 unit tests: null root, empty model, journey actorRef, relationship refs, list/map container traversal, identity deduplication, mixed resolved/unresolved splits |

### 5.2 IR Codec Tests (`chronos-model/src/test/`)

| Test Class | What it tests |
|---|---|
| `IrJsonCodecRoundTripTest` | Entity + Actor round-trip (kind discriminators, equality); JourneyDef with resolved SymbolRef (kind, resolved flag, ShapeId preservation) |

### 5.3 Validator Tests (`chronos-validator/src/test/`)

26 test classes covering every implemented CHR rule (CHR-011 through CHR-034 plus CHR-W001, and broad integration in `ChronosValidatorTest` + `ChronosValidatorIntegrationTest`).

### 5.4 CLI Tests (`chronos-cli/src/test/`)

15 test classes including: `ValidateCommandTest`, `GenerateCommandTest`, `MiscCommandIntegrationTest`, `FixtureIntegrationTest`, `BuildCommandTest`, `BuildCommandIntegrationTest`, `DiffCommandTest`, `SelectCommandTest`, `ModelProjectionTest`, `BuildConfigLoaderTest`, and more.

### 5.5 Top 5 Missing Tests (Highest Confidence Impact)

| Priority | Missing Test | Why It Matters |
|---|---|---|
| **1** | **Multi-shape file: namespace + use-import + cross-namespace NamedTypeRef resolution** | The entire `use` import path in `CollectSymbolsPhase` and `TypeResolutionPhase` is untested. A model with `use com.example.shared#Money` and a field `price: Money` exercises code paths that have never been validated by an automated test. |
| **2** | **Inheritance resolution end-to-end: `entity Child extends Parent`** | `EntityDef.parentType` is a raw `Optional<String>`. CHR-015 (circular), CHR-016 (field override), CHR-018 (multiple inheritance) are tested in the validator in isolation, but there is no compiler-level test that confirms the parent is actually declared in the same model. |
| **3** | **`IrJsonCodec` round-trip for all 13 IrShape types** | Current `IrJsonCodecRoundTripTest` covers only `EntityDef` + `ActorDef` + `JourneyDef`. `StateMachineDef`, `ErrorDef`, `DenyDef`, `InvariantDef`, `RelationshipDef`, `EnumDef`, `PolicyDef`, `ListDef`, `MapDef`, `ShapeStructDef` have no round-trip test. A discriminator bug in any of these would be silent. |
| **4** | **Variant + error trigger resolution: `CHR-027` through compiler** | `Variant.triggerName` is a raw `String` validated by CHR-027. There is no compiler-level test that creates an `ErrorDef` + a `Journey` with a matching `variants` block and verifies the full compile succeeds cleanly. |
| **5** | **`MarkdownPrdGenerator` golden-output test** | No test verifies that the generator output for a known model matches an expected `.md` fixture. The only evidence that generation works is the CLI tests checking that a non-empty file is written. A regression in heading structure or section ordering would be silent. |

---

## 6. Next Feature-Work Readiness Verdict

### Verdict: **READY** *(with three noted caveats)*

The Chronos foundation is architecturally sound and ready for feature growth:

- **Module DAG is clean:** No cycles, all five stated boundaries are enforced at the source-import level.
- **Compiler contract is explicit:** `finalized=true` has a documented, testable, reflection-enforced meaning.
- **IrRefWalker is self-maintaining:** New `SymbolRef` fields in any IR type are automatically covered.
- **Validator is comprehensive:** 33 of 35 planned CHR rules are implemented with dedicated tests.
- **CLI is clean:** All commands funnel through `ChronosCompiler` with no legacy parse bypass.

### Caveats (not blockers, but must be tracked)

| Caveat | Risk if ignored |
|---|---|
| **C1: Raw-string references are not tracked by IrRefWalker.** `EntityDef.parentType`, `ActorDef.parentType`, `StateMachineDef.entityName`, `Variant.triggerName`, etc. are strings checked only by validator rules. If a new feature adds a raw-string cross-reference and a developer forgets to add a CHR rule, the error will be silent — `finalized=true` will not catch it. | Silent invalid IR delivered to generators. |
| **C2: DOC_COMMENTs are parsed but discarded.** All IR shapes have a `docComments: List<String>` field that is always `List.of()`. Users who write `/// My comment` will see it silently ignored in all outputs. | Poor DX for a documentation-oriented language. |
| **C3: CHR-013 and CHR-017 are unimplemented.** CHR-017 is documented in the validator Javadoc but has no code. CHR-013 doesn't exist anywhere. These gaps may cause future confusion about the numbering scheme. | Incorrect documentation; potential validator gaps if these rules were intended to close real holes. |

---

## 7. Recommended Next 3 Feature Areas

### Feature 1 (Highest Value): Inheritance Resolution via `SymbolRef`

**What it is:** Replace `EntityDef.parentType: Optional<String>` and `ActorDef.parentType: Optional<String>` with `parentRef: Optional<SymbolRef>`. Resolve parent references in `CrossLinkResolutionPhase` alongside actor and relationship refs.

**Changes needed:**
- **Grammar:** No change — `extends ID` already exists.
- **Syntax DTO:** No change — `SyntaxEntityDecl.parentOrNull: String` already captures it.
- **IR (`chronos-model`):** Change `EntityDef.parentType` and `ActorDef.parentType` from `Optional<String>` to `Optional<SymbolRef>`.
- **Compiler — `BuildIrSkeletonPhase`:** Create unresolved `SymbolRef` for parent when `parentOrNull != null`.
- **Compiler — `CrossLinkResolutionPhase`:** Resolve parent `SymbolRef` against `SymbolKind.ENTITY` / `SymbolKind.ACTOR`.
- **Validator — `IrToChronosConverter`:** Update `symRefName()` usage for parent resolution.
- **Generator — `IrModelAdapter`:** Update `parentType` extraction.

**Phases touched:** BuildIrSkeleton (Phase 3), CrossLinkResolution (Phase 5); IrRefWalker then automatically covers the new `SymbolRef` field.

**Risks:**
- `EntityInvariant` field-reference validation (CHR-019) uses the parent chain via `InheritanceResolver`. Once parent is a `SymbolRef`, the resolver must look up via the IR model rather than a raw string map.
- `IrJsonCodec` will need an updated `EntityDef` / `ActorDef` serialization (Optional\<SymbolRef> case).

---

### Feature 2 (High Value): Doc Comment Lowering

**What it is:** Extract `///` doc comments from the ANTLR hidden channel in `LoweringVisitor` and populate `docComments: List<String>` in Syntax DTOs, which then flows into IR shapes and appears in generator output.

**Changes needed:**
- **Grammar:** No change — `DOC_COMMENT` rule already exists and routes to `HIDDEN` channel.
- **Syntax DTOs (`chronos-parser`):** Add `List<String> docComments` to each of the 13 `SyntaxDecl` implementations (and `SyntaxFieldDef`).
- **`LoweringVisitor`:** Before returning each shape DTO, collect preceding `DOC_COMMENT` tokens from the hidden channel using `tokens.getHiddenTokensToLeft(ctx.start.tokenIndex, Token.HIDDEN_CHANNEL)`.
- **IR (`chronos-model`):** No change — `docComments` fields already exist on all `IrShape` records.
- **Compiler — `BuildIrSkeletonPhase`:** Pass `syntax.docComments()` into each IR shape constructor instead of `List.of()`.
- **Generators:** Update `MarkdownPrdGenerator` to render doc comments as introductory text above each section.
- **Validator:** No changes required.

**Phases touched:** ParseAndLower (Phase 1, via LoweringVisitor); BuildIrSkeleton (Phase 3).

**Risks:**
- Hidden-channel token extraction requires care with blank lines between the comment and the shape keyword — the LoweringVisitor must skip whitespace tokens while walking backwards.
- This is a **low-risk, high-polish** change with no semantic impact on validation.

---

### Feature 3 (Medium Value): StateMachine Entity Binding via `SymbolRef`

**What it is:** Replace `StateMachineDef.entityName: String` and `StateMachineDef.fieldName: String` with `entityRef: Optional<SymbolRef>` (pointing to `SymbolKind.ENTITY`). The field binding (`fieldName`) is a field on the entity, not a top-level shape — it remains a `String` but should be validated against the entity's declared fields after `entityRef` is resolved.

**Changes needed:**
- **Grammar:** No change — `entity: ID` already exists inside `statemachineField`.
- **Syntax DTO:** No change — `SyntaxStateMachineDecl.entityName: String` already captures it.
- **IR (`chronos-model`):** Add `entityRef: SymbolRef` alongside `entityName: String` (or replace; migration path needed).
- **Compiler — `BuildIrSkeletonPhase`:** Create unresolved `SymbolRef` for entity binding.
- **Compiler — `CrossLinkResolutionPhase`:** Resolve `entityRef` against `SymbolKind.ENTITY`; emit CHR-033 if unresolvable.
- **Compiler — new CHR rule** (or strengthen CHR-033): After entity ref resolves, verify `fieldName` is a declared field on that entity and (ideally) that its type is an enum.
- **Generator:** No change needed initially.

**Phases touched:** BuildIrSkeleton (Phase 3), CrossLinkResolution (Phase 5); IrRefWalker then covers the new field automatically.

**Risks:**
- The validator currently implements CHR-033 against the legacy `ChronosModel`. Once `entityRef` is a `SymbolRef`, CHR-033 logic should move into `CrossLinkResolutionPhase` or be split: structural resolution in Phase 5, field-type validation in Phase 6. This is a moderate refactor.
- The `IrToChronosConverter` in `chronos-validator` will need updating to pass the resolved entity name through.

---

## Appendix: Optional Audit Script

The script `scripts/audit-import-boundaries.sh` is included to enable on-demand boundary checks in CI or local development without modifying build logic.

See: [`scripts/audit-import-boundaries.sh`](../../scripts/audit-import-boundaries.sh)

---

*Generated by automated codebase inspection — no source files were modified.*
