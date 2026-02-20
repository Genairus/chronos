# Chronos — Current State Report

> Generated: 2026-02-19
> Branch: `main` (commit `8b2c7d0`)
> Purpose: architectural baseline before deciding the next major step.
> Scope: read-only inspection — nothing was changed.

---

## 1. Module Graph (Gradle)

### 1.1 Module List

Seven modules are registered in [settings.gradle.kts](../../settings.gradle.kts):

```
chronos-core
chronos-model
chronos-parser
chronos-validator
chronos-compiler
chronos-generators
chronos-cli
```

### 1.2 Dependency Table

| Module | Depends on (project deps) | Key third-party | Notes |
|---|---|---|---|
| **chronos-core** | — | — | DAG root; no project deps |
| **chronos-model** | `:chronos-core` | `jackson-databind:2.18.2`, `jackson-datatype-jdk8`, `jackson-module-parameter-names` | Hosts **both** old `com.genairus.chronos.model.*` and new `com.genairus.chronos.ir.*`; `-parameters` compiler flag enabled |
| **chronos-parser** | `:chronos-core`, `:chronos-model` | `antlr4:4.13.1`, `antlr4-runtime:4.13.1` | ANTLR grammar + `LoweringVisitor`; generates `api` antlr runtime (transitive) |
| **chronos-validator** | `:chronos-core`, `:chronos-model` | — | test-only dep on `:chronos-parser` |
| **chronos-compiler** | `:chronos-core`, `:chronos-model`, `:chronos-parser`, `:chronos-validator` | — | No external deps |
| **chronos-generators** | `:chronos-core`, `:chronos-model` | — | test-only dep on `:chronos-parser`; contains `compat/IrModelAdapter` |
| **chronos-cli** | `:chronos-core`, `:chronos-model`, `:chronos-compiler`, `:chronos-validator`, `:chronos-generators` | `picocli:4.7.5`, `gson:2.10.1`, GraalVM native plugin | Application entry point |

### 1.3 DAG Visualization (ASCII)

```
chronos-core
  ├── chronos-model
  │     ├── chronos-parser
  │     │     └── chronos-compiler
  │     │           └── chronos-cli
  │     ├── chronos-validator
  │     │     └── (chronos-compiler)
  │     ├── chronos-generators
  │     │     └── (chronos-cli)
  │     └── (chronos-cli)
  └── (chronos-cli)
```

**Cycle check: PASS — no cycles exist.**

---

## 2. Canonical Types Location Check

All seven foundational types are unique and live exclusively in `chronos-core`.

| Type | Canonical path | Duplicates? |
|---|---|---|
| `Span` | [chronos-core/…/core/refs/Span.java](../../chronos-core/src/main/java/com/genairus/chronos/core/refs/Span.java) | None |
| `QualifiedName` | [chronos-core/…/core/refs/QualifiedName.java](../../chronos-core/src/main/java/com/genairus/chronos/core/refs/QualifiedName.java) | None |
| `ShapeId` | [chronos-core/…/core/refs/ShapeId.java](../../chronos-core/src/main/java/com/genairus/chronos/core/refs/ShapeId.java) | None |
| `SymbolRef` | [chronos-core/…/core/refs/SymbolRef.java](../../chronos-core/src/main/java/com/genairus/chronos/core/refs/SymbolRef.java) | None |
| `Diagnostic` | [chronos-core/…/core/diagnostics/Diagnostic.java](../../chronos-core/src/main/java/com/genairus/chronos/core/diagnostics/Diagnostic.java) | None |
| `DiagnosticCollector` | [chronos-core/…/core/diagnostics/DiagnosticCollector.java](../../chronos-core/src/main/java/com/genairus/chronos/core/diagnostics/DiagnosticCollector.java) | None |
| `DiagnosticSeverity` | [chronos-core/…/core/diagnostics/DiagnosticSeverity.java](../../chronos-core/src/main/java/com/genairus/chronos/core/diagnostics/DiagnosticSeverity.java) | None |

**No old `com.genairus.chronos.ir.refs.*` package exists** — confirmed by exhaustive search. The canonical refs are cleanly consolidated in `chronos-core`.

### 2.1 Known Dual-Model Situation

`chronos-model` hosts **two parallel model representations** under the same jar:

| Package | Status | Consumers |
|---|---|---|
| `com.genairus.chronos.model.*` | Legacy (pre-refactor, pre-existing) | `ChronosValidator`, `IrToChronosConverter`, `IrModelAdapter`, all validator tests, generator implementations |
| `com.genairus.chronos.ir.*` | New (added in current refactor) | `ChronosCompiler` pipeline, `IrJsonCodec`, `IrTraversal`, JSON tests |

This dual-model situation is the primary tech-debt item (see §7).

---

## 3. Compiler Pipeline Summary

Entry point: [`ChronosCompiler.compile(String, String)`](../../chronos-compiler/src/main/java/com/genairus/chronos/compiler/ChronosCompiler.java)

Shared thread: `ResolverContext(namespace, uses, SymbolTable, DiagnosticCollector)` — created once, passed to every phase.

| # | Phase class | Input → Output | Action | CHR codes emitted | Resolution? |
|---|---|---|---|---|---|
| 1 | [`ParseAndLowerPhase`](../../chronos-compiler/src/main/java/com/genairus/chronos/compiler/phases/ParseAndLowerPhase.java) | `String → SyntaxModel` (null on error) | ANTLR lex/parse; lower parse tree via `LoweringVisitor` to Syntax DTOs | `CHR-PARSE` | No |
| 2 | [`CollectSymbolsPhase`](../../chronos-compiler/src/main/java/com/genairus/chronos/compiler/phases/CollectSymbolsPhase.java) | `SyntaxModel → SyntaxModel` (pass-through) | Registers every top-level decl in `SymbolTable`; detects duplicates | `CHR-005` | No |
| 3 | [`BuildIrSkeletonPhase`](../../chronos-compiler/src/main/java/com/genairus/chronos/compiler/phases/BuildIrSkeletonPhase.java) | `SyntaxModel → IrModel` (new object) | Converts Syntax DTOs → IR records; cross-refs created as **unresolved** `SymbolRef`s; `docComments` always `List.of()` | — | Creates unresolved refs |
| 4 | [`TypeResolutionPhase`](../../chronos-compiler/src/main/java/com/genairus/chronos/compiler/phases/TypeResolutionPhase.java) | `IrModel → IrModel` (pass-through) | Verifies each `TypeRef.NamedTypeRef` resolves in local symbols or imports; **does not replace the string** | `CHR-008` | Verifies only; no replacement |
| 5 | [`CrossLinkResolutionPhase`](../../chronos-compiler/src/main/java/com/genairus/chronos/compiler/phases/CrossLinkResolutionPhase.java) | `IrModel → IrModel` (new object) | Resolves `JourneyDef.actorRef` and `RelationshipDef.fromEntityRef`/`toEntityRef`; returns rebuilt model with resolved `SymbolRef`s where possible | `CHR-008` (actor), `CHR-011` (relationship entity) | Yes — replaces unresolved SymbolRefs |
| 6 | [`ValidationPhase`](../../chronos-compiler/src/main/java/com/genairus/chronos/compiler/phases/ValidationPhase.java) | `IrModel → IrModel` (pass-through) | Runs `ChronosValidator` (which internally converts via `IrToChronosConverter`); merges results into `DiagnosticCollector` | `CHR-001`–`CHR-034`, `CHR-W001` | No (uses converted legacy model) |
| 7 | [`FinalizeIrPhase`](../../chronos-compiler/src/main/java/com/genairus/chronos/compiler/phases/FinalizeIrPhase.java) | `IrModel → FinalizeIrPhase.Result` | Scans all `SymbolRef` fields via `IrTraversal`; emits `CHR-012` for any remaining unresolved; sets `finalized = !hasErrors()` | `CHR-012` | Verification only |

**Key pipeline contract:** Compiler halts after Phase 1 if `syntax == null` (parse failed).
`CompileResult.success()` = `parsed && finalized && no ERROR diagnostics`.

---

## 4. IR Model Inventory

Sealed interface: [`IrShape`](../../chronos-model/src/main/java/com/genairus/chronos/ir/types/IrShape.java) — 13 permits.

| IrShape impl | SymbolRef fields | TypeRef fields | Raw strings that should become refs |
|---|---|---|---|
| [`ActorDef`](../../chronos-model/src/main/java/com/genairus/chronos/ir/types/ActorDef.java) | — | — | `parentType: Optional<String>` → should be `Optional<SymbolRef>` to ActorDef |
| [`EntityDef`](../../chronos-model/src/main/java/com/genairus/chronos/ir/types/EntityDef.java) | — | `fields[*].type: TypeRef` | `parentType: Optional<String>` → should be `Optional<SymbolRef>` to EntityDef |
| [`ShapeStructDef`](../../chronos-model/src/main/java/com/genairus/chronos/ir/types/ShapeStructDef.java) | — | `fields[*].type: TypeRef` | — |
| [`EnumDef`](../../chronos-model/src/main/java/com/genairus/chronos/ir/types/EnumDef.java) | — | — | — |
| [`ListDef`](../../chronos-model/src/main/java/com/genairus/chronos/ir/types/ListDef.java) | — | `memberType: TypeRef` | `TypeRef.NamedTypeRef.qualifiedId: String` — verified but not replaced |
| [`MapDef`](../../chronos-model/src/main/java/com/genairus/chronos/ir/types/MapDef.java) | — | `keyType: TypeRef`, `valueType: TypeRef` | Same as above |
| [`PolicyDef`](../../chronos-model/src/main/java/com/genairus/chronos/ir/types/PolicyDef.java) | — | — | `description: String` (intentional — policy text) |
| [`InvariantDef`](../../chronos-model/src/main/java/com/genairus/chronos/ir/types/InvariantDef.java) | — | — | `scope: List<String>` → should be `List<SymbolRef>` to EntityDef; `expression: String` (DSL, intentional); `severity: String` → should be `DiagnosticSeverity` enum |
| [`DenyDef`](../../chronos-model/src/main/java/com/genairus/chronos/ir/types/DenyDef.java) | — | — | `scope: List<String>` → should be `List<SymbolRef>` to shape; `severity: String` → should be a typed enum |
| [`ErrorDef`](../../chronos-model/src/main/java/com/genairus/chronos/ir/types/ErrorDef.java) | — | `payload[*].type: TypeRef` | `severity: String` → should be a typed enum; `code: String` (intentional) |
| [`RelationshipDef`](../../chronos-model/src/main/java/com/genairus/chronos/ir/types/RelationshipDef.java) | `fromEntityRef: SymbolRef(ENTITY)` ✅ resolved by Phase 5 | — | `inverseField: Optional<String>` → is a field name (may remain string or become field-level ref) |
| [`StateMachineDef`](../../chronos-model/src/main/java/com/genairus/chronos/ir/types/StateMachineDef.java) | — | — | **`entityName: String`** → should be `SymbolRef` to EntityDef; **`fieldName: String`** → should be a field-level ref |
| [`JourneyDef`](../../chronos-model/src/main/java/com/genairus/chronos/ir/types/JourneyDef.java) | `actorRef: SymbolRef(ACTOR)` ✅ resolved by Phase 5; **nullable** | — | `preconditions: List<String>` (natural language — intentional) |

**TypeRef note:** `TypeRef.NamedTypeRef.qualifiedId` is a `String` in both the IR and the old model. Phase 4 verifies it resolves, but the field remains a string. The IR has no `SymbolRef`-typed equivalent for field types.

**`toEntityRef` row omitted above — lives on RelationshipDef:** `toEntityRef: SymbolRef(ENTITY)` ✅ resolved by Phase 5.

---

## 5. "Finalized IR" Invariant Audit

### 5.1 How `finalized` is computed

[`FinalizeIrPhase`](../../chronos-compiler/src/main/java/com/genairus/chronos/compiler/phases/FinalizeIrPhase.java) calls [`IrTraversal.findUnresolvedRefs(model)`](../../chronos-compiler/src/main/java/com/genairus/chronos/compiler/util/IrTraversal.java), which scans:

```
JourneyDef.actorRef     (if non-null)
RelationshipDef.fromEntityRef
RelationshipDef.toEntityRef
```

For each unresolved ref found, `CHR-012` is emitted. Then:

```java
return new Result(model, !ctx.diagnostics().hasErrors());
```

`hasErrors()` includes **all** accumulated errors from every prior phase, not just CHR-012.

### 5.2 Invariant truth table

| Claim | Status | Evidence |
|---|---|---|
| `finalized==true` → no ERROR diagnostics | ✅ TRUE | `finalized = !hasErrors()` — definition |
| `finalized==true` → no unresolved `SymbolRef` in JourneyDef/RelationshipDef | ✅ TRUE | CHR-012 emitted for each; would cause `hasErrors()` |
| `finalized==true` → `TypeRef.NamedTypeRef` strings are resolved | ❌ FALSE | Phase 4 verifies existence (CHR-008) but strings remain untyped |
| `finalized==true` → `parentType` (entity/actor) is a real ref | ❌ FALSE | `Optional<String>` — validated by CHR-015/016/018 but never converted |
| `finalized==true` → `InvariantDef.scope` / `DenyDef.scope` are real refs | ❌ FALSE | `List<String>` — validated by CHR-021/024 but never converted |
| `finalized==true` → `StateMachineDef.entityName/fieldName` are real refs | ❌ FALSE | `String` — validated by CHR-033 but never converted |

### 5.3 Specific gaps by IR location

| IR field | Gap | Validated? | CHR code |
|---|---|---|---|
| `TypeRef.NamedTypeRef.qualifiedId` (all fields) | String, not a ref; not replaced after verification | ✅ CHR-008 | Phase 4 |
| `EntityDef.parentType: Optional<String>` | Not a SymbolRef; validator works on string identity | ✅ CHR-015, 016, 018 | Phase 6 |
| `ActorDef.parentType: Optional<String>` | Same as above | ✅ CHR-015 | Phase 6 |
| `InvariantDef.scope: List<String>` | Not SymbolRefs | ✅ CHR-021 | Phase 6 |
| `DenyDef.scope: List<String>` | Not SymbolRefs | ✅ CHR-024 | Phase 6 |
| `StateMachineDef.entityName: String` | Not a SymbolRef | ✅ CHR-033 | Phase 6 |
| `StateMachineDef.fieldName: String` | Not a field-level ref | ✅ CHR-033 | Phase 6 |
| `InvariantDef.severity: String` | Not a typed enum | ✅ CHR-020 | Phase 6 |
| `DenyDef.severity: String` | Not a typed enum | ✅ CHR-025 | Phase 6 |
| `ErrorDef.severity: String` | Not a typed enum | ✅ CHR-028 | Phase 6 |

**Summary:** `finalized==true` guarantees no errors and that the three cross-shape `SymbolRef` fields (actorRef, fromEntityRef, toEntityRef) are resolved. All other "cross-references" are string-based and are validated (not null, exists in model) but are never reified into typed refs.

### 5.4 IrTraversal coverage gap

[`IrTraversal.findUnresolvedRefs`](../../chronos-compiler/src/main/java/com/genairus/chronos/compiler/util/IrTraversal.java) is manually maintained. It currently covers only 3 fields. **If a new `IrShape` gains a `SymbolRef` field in the future, IrTraversal will silently miss it** unless it is explicitly added. There is no compile-time enforcement.

---

## 6. Tests & Coverage Reality Check

### 6.1 Test inventory by module

| Module | Test count | Test files | Coverage areas |
|---|---|---|---|
| **chronos-core** | 5 files | `SpanTest`, `NamespaceIdTest`, `QualifiedNameTest`, `ShapeIdTest`, `SymbolRefTest` | Canonical type unit tests; `requireResolvedOrReport`, equality, validation |
| **chronos-model** | 19 files | 18 model type tests + `IrJsonCodecRoundTripTest` | IR record unit tests; JSON round-trip codec |
| **chronos-parser** | ≥2 files | `ChronosModelParserIntegrationTest`, `ChronosParserFacadeTest` | ANTLR parse; lowering |
| **chronos-validator** | ~40 files | `ChronosValidatorTest` (CHR-001–010), individual `Chr011`–`Chr034` tests, `ChrW001`, integration + result tests | All 34 CHR rules + W001; uses legacy `com.genairus.chronos.model.*` API directly |
| **chronos-compiler** | 2 files | [`ChronosCompilerTest`](../../chronos-compiler/src/test/java/com/genairus/chronos/compiler/ChronosCompilerTest.java) (7 cases), [`LinkingInvariantTest`](../../chronos-compiler/src/test/java/com/genairus/chronos/compiler/LinkingInvariantTest.java) (3 cases) | End-to-end compile; CHR-005, CHR-008, CHR-011, CHR-012; finalized invariant |
| **chronos-generators** | Unknown | Not read | Generator output tests |
| **chronos-cli** | 15 files | `BuildCommandTest`, `ValidateCommandTest`, `GenerateCommandTest`, `FixtureIntegrationTest`, etc. | CLI command round-trips; fixture integration |

**Total confirmed: 85+ test files across all modules.**

### 6.2 Snapshot/golden tests

| Type | Present? | Location |
|---|---|---|
| Golden IR JSON snapshot | ❌ No | — |
| Golden PRD markdown snapshot | ⚠️ Partial | `examples/integration/checkout-prd.golden.md` (modified in git status — may be stale) |
| Full end-to-end fixture integration | ⚠️ Partial | `FixtureIntegrationTest` exists but is untracked (not yet committed) |

### 6.3 JSON codec tests

`IrJsonCodecRoundTripTest` exists at [`chronos-model/src/test/java/com/genairus/chronos/ir/json/`](../../chronos-model/src/test/java/com/genairus/chronos/ir/) — untracked (not yet committed). Tests round-trip fidelity but details not inspected.

### 6.4 Top 5 highest-leverage missing tests

| Priority | Missing test | What it locks | Module |
|---|---|---|---|
| 1 | **Golden IR JSON snapshot** — compile a known multi-construct `.chronos` file, assert exact JSON output | Locks `IrJsonCodec` field names, discriminators, ordering; catches silent regressions in finalized IR shape | `chronos-model` |
| 2 | **`IrTraversal` direct unit test** — assert that adding a new shape with an unresolved ref is always detected | Prevents future IrShape additions from silently bypassing the finalize check | `chronos-compiler` |
| 3 | **`IrToChronosConverter` fidelity test** — compile with `ChronosCompiler`, then assert `ChronosValidator.validate(IrModel)` and `ChronosValidator.validate(converted ChronosModel)` produce identical diagnostics | Proves the compat bridge is lossless; required before deleting it | `chronos-validator` |
| 4 | **Multi-file / cross-namespace `use` import resolution test** — two namespaces where one references a type from the other via `use` | Only integration path for cross-namespace `TypeRef.NamedTypeRef` resolution; currently untested | `chronos-compiler` |
| 5 | **Inheritance chain type resolution** — entity B extends A where A's field type is a named type; validate CHR-016 fires correctly and resolver traverses the parent chain | Inheritance + named-type resolution interact; no combined test exists | `chronos-validator` |

---

## 7. Recommended Next Steps

### Option A — Eliminate the IrToChronosConverter compat layer

**What changes:** Migrate `ChronosValidator` to operate directly on `com.genairus.chronos.ir.*` types. Delete `IrToChronosConverter`, `IrModelAdapter`, and eventually `com.genairus.chronos.model.*`. Update all ~40 validator tests to use IR types.

**Why it matters:** The two-converter pattern (`IrToChronosConverter` in validator, `IrModelAdapter` in generators) is active tech debt. Every new IR field that lands requires a simultaneous change in both converters or the validator silently operates on stale data. It also means diagnostic `Span` information is converted through `SourceLocation` and back, risking loss of precision.

**Estimated risk:** HIGH. Touches every validator test, both compat classes, the generators, and likely CLI integration tests. A partial migration (validator first, then generators) is advisable.

**Modules touched:** `chronos-validator`, `chronos-generators`, `chronos-model` (legacy package removal), `chronos-compiler` (ValidationPhase simplifies), `chronos-cli`.

---

### Option B — Promote TypeRef.NamedTypeRef to a resolved SymbolRef in the finalized IR

**What changes:** After `TypeResolutionPhase` confirms a `NamedTypeRef` resolves, replace it with a new `TypeRef.ResolvedRef(SymbolRef)` variant (or change `NamedTypeRef` to carry a `ShapeId` after resolution). Extend `IrTraversal` to scan all TypeRef trees. The finalized IR then has zero raw-string cross-references for field types.

**Why it matters:** Generators that need to emit typed code (e.g., OpenAPI, Kotlin, TypeScript) currently re-resolve type names at generation time. A fully typed IR would make generator implementations simpler and safer.

**Estimated risk:** MEDIUM. Changes `TypeRef` sealed interface (adds a new permit or mutates `NamedTypeRef`), `BuildIrSkeletonPhase`, `TypeResolutionPhase`, `IrTraversal`, `IrJsonCodec` (new discriminator value), and JSON round-trip tests. Does not require touching the validator if done before Option A.

**Modules touched:** `chronos-model` (TypeRef, IrJsonCodec), `chronos-compiler` (phases 3, 4, 7), tests in both modules.

---

### Option C — Add IrTraversal exhaustive coverage + golden snapshot tests

**What changes:** Expand `IrTraversal.findUnresolvedRefs` to enumerate **all** `IrShape` subclasses rather than only `JourneyDef`/`RelationshipDef`. Add a direct unit test for `IrTraversal`. Commit the `IrJsonCodecRoundTripTest` and add a golden snapshot test using the existing example fixture files (`examples/integration/*.chronos`).

**Why it matters:** This is purely additive — no production behavior changes, no refactoring. It immediately locks the current invariants against regression, creates a safety net for Options A and B, and costs almost no risk. The golden snapshot also serves as living documentation of what "finalized IR" looks like.

**Estimated risk:** LOW. Only new test files and minor expansion of `IrTraversal`. No change to production logic.

**Modules touched:** `chronos-compiler` (`IrTraversal`, new test), `chronos-model` (golden snapshot test).

---

## Appendix: Untracked Files of Interest

The following files exist on disk but are not yet staged/committed (`??` in git status):

| Path | Purpose |
|---|---|
| `chronos-compiler/src/main/java/…/compiler/util/` | Contains `IrTraversal.java` |
| `chronos-compiler/src/test/…/compiler/LinkingInvariantTest.java` | Tests finalized invariant (3 cases) |
| `chronos-generators/src/main/java/…/generators/compat/` | Contains `IrModelAdapter.java` |
| `chronos-model/src/main/java/…/ir/json/` | Contains `IrJsonCodec.java` |
| `chronos-model/src/test/…/ir/` | Contains `IrJsonCodecRoundTripTest` |
| `chronos-cli/src/test/…/cli/FixtureIntegrationTest.java` | CLI fixture integration test |
| `examples/integration/*.chronos` | Test fixtures (`actor-and-journey`, `minimal-entity`, `relationship-basic`) |
| `docs/status/` | This report |

These should be staged before the next commit to ensure the pipeline is complete.
