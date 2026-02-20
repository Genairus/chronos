# Chronos Current State Report

> Generated: 2026-02-19
> Branch: `main`
> Purpose: Architectural snapshot to guide next steps.

---

## 1. Module Graph (Gradle)

### Included Modules (settings.gradle.kts)

7 modules total. All discovered via
[settings.gradle.kts](../../settings.gradle.kts).

| Module | Depends On (projects) | Notes |
|---|---|---|
| `chronos-core` | _(none)_ | Root of DAG; Java 21; JUnit 5.10.0 via root `build.gradle.kts` |
| `chronos-model` | `chronos-core` | Jackson 2.18.2 (databind, jdk8, parameter-names); `-parameters` compiler flag for JSON codec |
| `chronos-parser` | `chronos-core`, `chronos-model` | ANTLR 4.13.1 grammar; `chronos-model` is `implementation` (not `api`), so downstream must add it explicitly |
| `chronos-validator` | `chronos-core`, `chronos-model` | `chronos-parser` as `testImplementation` only |
| `chronos-generators` | `chronos-core`, `chronos-model` | `chronos-parser` as `testImplementation` only |
| `chronos-compiler` | `chronos-core`, `chronos-model`, `chronos-parser`, `chronos-validator` | 7-phase pipeline; most-connected non-CLI module |
| `chronos-cli` | `chronos-core`, `chronos-model`, `chronos-parser`, `chronos-validator`, `chronos-generators` | PicoCLI 4.7.5; GSON 2.10.1; GraalVM native build tools 0.9.28; **does NOT depend on `chronos-compiler`** |

### Dependency DAG (textual)

```
chronos-core
├── chronos-model
│   └── chronos-parser
│       ├── chronos-compiler
│       │   └── (not wired to CLI — see § 5)
│       └── (test dep only in validator/generators)
├── chronos-validator  ──────────────────────────────► chronos-cli
├── chronos-generators ──────────────────────────────► chronos-cli
└── chronos-core ────────────────────────────────────► chronos-cli
```

**Cycles: NONE.** The DAG is acyclic. ✓

### Key Observation

`chronos-compiler` is **not in `chronos-cli`'s dependency list** ([chronos-cli/build.gradle.kts](../../chronos-cli/build.gradle.kts#L16-L20)).
The CLI commands (`build`, `validate`, `generate`) use the **legacy**
`ChronosModelParser.parseFile()` + `ChronosValidator.validate()` path — bypassing
the 7-phase compiler pipeline entirely. This is the single most significant
architectural gap in the codebase today.

---

## 2. Canonical Types Location Checks

All foundational types are defined **exactly once**, in `chronos-core`. No duplicate packages or stale `*.ir.refs.*` remnants were found.

| Type | File | Package |
|---|---|---|
| `Span` | [chronos-core/…/refs/Span.java](../../chronos-core/src/main/java/com/genairus/chronos/core/refs/Span.java) | `com.genairus.chronos.core.refs` |
| `QualifiedName` | [chronos-core/…/refs/QualifiedName.java](../../chronos-core/src/main/java/com/genairus/chronos/core/refs/QualifiedName.java) | `com.genairus.chronos.core.refs` |
| `ShapeId` | [chronos-core/…/refs/ShapeId.java](../../chronos-core/src/main/java/com/genairus/chronos/core/refs/ShapeId.java) | `com.genairus.chronos.core.refs` |
| `SymbolRef` | [chronos-core/…/refs/SymbolRef.java](../../chronos-core/src/main/java/com/genairus/chronos/core/refs/SymbolRef.java) | `com.genairus.chronos.core.refs` |
| `Diagnostic` | [chronos-core/…/diagnostics/Diagnostic.java](../../chronos-core/src/main/java/com/genairus/chronos/core/diagnostics/Diagnostic.java) | `com.genairus.chronos.core.diagnostics` |
| `DiagnosticCollector` | [chronos-core/…/diagnostics/DiagnosticCollector.java](../../chronos-core/src/main/java/com/genairus/chronos/core/diagnostics/DiagnosticCollector.java) | `com.genairus.chronos.core.diagnostics` |
| `DiagnosticSeverity` | [chronos-core/…/diagnostics/DiagnosticSeverity.java](../../chronos-core/src/main/java/com/genairus/chronos/core/diagnostics/DiagnosticSeverity.java) | `com.genairus.chronos.core.diagnostics` |

**No stale packages:** No files found under `com/genairus/chronos/ir/refs/` or any old duplicate path.

---

## 3. Compiler Pipeline Summary

Pipeline entry: [ChronosCompiler.java](../../chronos-compiler/src/main/java/com/genairus/chronos/compiler/ChronosCompiler.java)
Shared state threaded through: [ResolverContext.java](../../chronos-compiler/src/main/java/com/genairus/chronos/compiler/ResolverContext.java) — holds `SymbolTable`, `UseDecl` list, and `DiagnosticCollector`.

| # | Phase | File | Input → Output | Mutates / Returns | CHR Codes Emitted | Resolution? |
|---|---|---|---|---|---|---|
| 1 | `ParseAndLowerPhase` | [phases/ParseAndLowerPhase.java](../../chronos-compiler/src/main/java/com/genairus/chronos/compiler/phases/ParseAndLowerPhase.java) | `String` → `SyntaxModel` (null on failure) | New `SyntaxModel` or `null` | `CHR-PARSE` | No — syntax validation only |
| 2 | `CollectSymbolsPhase` | [phases/CollectSymbolsPhase.java](../../chronos-compiler/src/main/java/com/genairus/chronos/compiler/phases/CollectSymbolsPhase.java) | `SyntaxModel` → `SyntaxModel` (pass-through) | Mutates `SymbolTable` in ctx | `CHR-005` (duplicate name) | No — registration only |
| 3 | `BuildIrSkeletonPhase` | [phases/BuildIrSkeletonPhase.java](../../chronos-compiler/src/main/java/com/genairus/chronos/compiler/phases/BuildIrSkeletonPhase.java) | `SyntaxModel` → `IrModel` | New `IrModel`; unresolved `SymbolRef`s written for actor/entity refs | None | No — structural conversion |
| 4 | `TypeResolutionPhase` | [phases/TypeResolutionPhase.java](../../chronos-compiler/src/main/java/com/genairus/chronos/compiler/phases/TypeResolutionPhase.java) | `IrModel` → `IrModel` (pass-through) | Read-only verification of `TypeRef.NamedTypeRef` strings | `CHR-008` (unresolved type) | Verification only (no mutation) |
| 5 | `CrossLinkResolutionPhase` | [phases/CrossLinkResolutionPhase.java](../../chronos-compiler/src/main/java/com/genairus/chronos/compiler/phases/CrossLinkResolutionPhase.java) | `IrModel` → new `IrModel` | New `IrModel` with resolved `SymbolRef`s for actors and relationship endpoints | `CHR-008` (undefined actor), `CHR-011` (undefined relationship entity) | **Yes — links SymbolRefs** |
| 6 | `ValidationPhase` | [phases/ValidationPhase.java](../../chronos-compiler/src/main/java/com/genairus/chronos/compiler/phases/ValidationPhase.java) | `IrModel` → `IrModel` (pass-through) | Delegates to `ChronosValidator`; merges results into collector | `CHR-001`–`CHR-034`, `CHR-W001` | No — semantic rules only |
| 7 | `FinalizeIrPhase` | [phases/FinalizeIrPhase.java](../../chronos-compiler/src/main/java/com/genairus/chronos/compiler/phases/FinalizeIrPhase.java) | `IrModel` → `Result(model, finalized)` | Sets `finalized = !hasErrors()` after scanning all remaining unresolved `SymbolRef`s | `CHR-012` (lingering unresolved ref) | No — audit only |

**Supporting utilities:**
- [resolve/RefResolver.java](../../chronos-compiler/src/main/java/com/genairus/chronos/compiler/resolve/RefResolver.java) — used by Phase 5 to look up symbols and swap unresolved → resolved `SymbolRef`
- [symbols/SymbolTable.java](../../chronos-compiler/src/main/java/com/genairus/chronos/compiler/symbols/SymbolTable.java) — flat name → `Symbol` registry; emits `CHR-005` on duplicate
- [util/IrTraversal.java](../../chronos-compiler/src/main/java/com/genairus/chronos/compiler/util/IrTraversal.java) — scans `JourneyDef.actorRef`, `RelationshipDef.fromEntityRef`, `RelationshipDef.toEntityRef`

---

## 4. IR Model Inventory

All `IrShape` implementations live in `com.genairus.chronos.ir.types` under
[chronos-model/src/main/java/com/genairus/chronos/ir/types/](../../chronos-model/src/main/java/com/genairus/chronos/ir/types/).
Sealed interface: [IrShape.java](../../chronos-model/src/main/java/com/genairus/chronos/ir/types/IrShape.java)
Container: [IrModel.java](../../chronos-model/src/main/java/com/genairus/chronos/ir/model/IrModel.java) — `namespace: String`, `imports: List<UseDecl>`, `shapes: List<IrShape>`

| IrShape | SymbolRef Fields | TypeRef Fields | Raw String Fields (eventual refs) |
|---|---|---|---|
| `EntityDef` | — | — (in nested `FieldDef.type`) | `parentType: Optional<String>` — parent entity name |
| `ShapeStructDef` | — | — (in nested `FieldDef.type`) | — |
| `EnumDef` | — | — | — |
| `ListDef` | — | `memberType: TypeRef` | — |
| `MapDef` | — | `keyType: TypeRef`, `valueType: TypeRef` | — |
| `ActorDef` | — | — | `parentType: Optional<String>` — parent actor name |
| `PolicyDef` | — | — | — |
| `JourneyDef` | `actorRef: SymbolRef` (nullable) | — | `preconditions: List<String>` |
| `RelationshipDef` | `fromEntityRef: SymbolRef`, `toEntityRef: SymbolRef` | — | `inverseField: Optional<String>` |
| `InvariantDef` | — | — | `scope: List<String>` — entity names; `severity: String` |
| `DenyDef` | — | — | `scope: List<String>` — entity names; `severity: String` |
| `ErrorDef` | — | — (in `payload: List<FieldDef>`) | `code: String`, `severity: String` |
| `StateMachineDef` | — | — | `entityName: String`, `fieldName: String`, `states: List<String>`, `initialState: String`, `terminalStates: List<String>` |

**Supporting types:**
- `FieldDef` — `type: TypeRef`, `traits: List<TraitApplication>`
- `TypeRef` (sealed, 4 variants) — `PrimitiveType`, `ListType`, `MapType`, `NamedTypeRef(qualifiedId: String)` — the last variant is an unresolved string ref
- `Transition` — `fromState: String`, `toState: String` (raw strings, no SymbolRef)
- `TraitApplication` — `name: String` (trait names are raw strings)

### Raw String Ref Summary

The following IR fields contain **names that should logically be cross-references** but are stored as plain `String` today. They are not currently covered by `IrTraversal` or `RefResolver`:

| Field | Location | Conceptual Target |
|---|---|---|
| `EntityDef.parentType` | [EntityDef.java](../../chronos-model/src/main/java/com/genairus/chronos/ir/types/EntityDef.java) | Entity or Shape (inheritance) |
| `ActorDef.parentType` | [ActorDef.java](../../chronos-model/src/main/java/com/genairus/chronos/ir/types/ActorDef.java) | Actor (inheritance) |
| `TypeRef.NamedTypeRef.qualifiedId` | [TypeRef.java](../../chronos-model/src/main/java/com/genairus/chronos/ir/types/TypeRef.java) | Any named shape |
| `InvariantDef.scope` / `DenyDef.scope` | [InvariantDef.java](../../chronos-model/src/main/java/com/genairus/chronos/ir/types/InvariantDef.java), [DenyDef.java](../../chronos-model/src/main/java/com/genairus/chronos/ir/types/DenyDef.java) | Entity names |
| `StateMachineDef.entityName` | [StateMachineDef.java](../../chronos-model/src/main/java/com/genairus/chronos/ir/types/StateMachineDef.java) | Entity |
| `Transition.fromState` / `toState` | [Transition.java](../../chronos-model/src/main/java/com/genairus/chronos/ir/types/Transition.java) | State names (within same StateMachineDef) |

---

## 5. "Finalized IR" Invariant Audit

### How `finalized` Is Computed

[FinalizeIrPhase.java](../../chronos-compiler/src/main/java/com/genairus/chronos/compiler/phases/FinalizeIrPhase.java), line 38:

```java
return new Result(model, !ctx.diagnostics().hasErrors());
```

Before setting the flag, Phase 7 calls `IrTraversal.findUnresolvedRefs(model)` and emits `CHR-012` ERROR for each unresolved `SymbolRef` found. Since `finalized = !hasErrors()`, any lingering unresolved `SymbolRef` will produce an error and set `finalized = false`.

### Does `finalized == true` Imply…?

| Invariant | Status | Evidence |
|---|---|---|
| No ERROR diagnostics | ✓ **Guaranteed** | By definition: `finalized = !hasErrors()` |
| No unresolved `SymbolRef` (actor, relationship entities) | ✓ **Guaranteed** | `IrTraversal` emits `CHR-012` ERROR for each; covered fields: `JourneyDef.actorRef`, `RelationshipDef.fromEntityRef/toEntityRef` |
| No unresolved `TypeRef.NamedTypeRef` strings | ✓ **Indirectly guaranteed** | Phase 4 emits `CHR-008` ERROR for any unresolvable name; errors prevent `finalized=true` |
| No raw-string entity refs (inheritance, scope, stateMachine) | ✗ **NOT guaranteed** | `EntityDef.parentType`, `ActorDef.parentType`, `InvariantDef.scope`, `DenyDef.scope`, `StateMachineDef.entityName` are checked by the **validator** (`CHR-015`, `CHR-019`, `CHR-029` etc.) but as _string_ comparisons — if validation passes, the strings remain strings even after finalization |

### Gaps

1. **`IrTraversal` is incomplete.** It only scans `JourneyDef` and `RelationshipDef`. Future shapes that gain `SymbolRef` fields (e.g., if `StateMachineDef.entityName` is ever upgraded to a `SymbolRef`) must also be added. The Javadoc comment on [IrTraversal.java:14](../../chronos-compiler/src/main/java/com/genairus/chronos/compiler/util/IrTraversal.java#L14) acknowledges this explicitly.

2. **`finalized` lives on `CompileResult`, not on `IrModel`.** An `IrModel` can escape the compiler (e.g., from the CLI's legacy parse path) with no `finalized` guarantee at all. There is no way to ask an `IrModel` instance directly whether it is finalized.

3. **CLI bypasses `chronos-compiler` entirely.** All CLI commands (`build`, `validate`, `generate`) use `ChronosModelParser.parseFile()` + `ChronosValidator.validate()` — the legacy path. The 7-phase compiler, `SymbolRef` resolution, and `finalized` invariant are **never exercised by the CLI**. Concretely:
   - [BuildCommand.java:94](../../chronos-cli/src/main/java/com/genairus/chronos/cli/BuildCommand.java#L94) — `ChronosModelParser.parseFile(source)`
   - [ValidateCommand.java:49](../../chronos-cli/src/main/java/com/genairus/chronos/cli/ValidateCommand.java#L49) — `ChronosModelParser.parseFile(inputFile.toPath())`
   - `chronos-compiler` is not in `chronos-cli/build.gradle.kts`

---

## 6. Tests & Coverage Reality Check

### Test Count by Module

| Module | Test Classes | @Test Methods | Notable |
|---|---|---|---|
| `chronos-core` | 5 | 47 | Covers all foundational types (`NamespaceId`, `QualifiedName`, `ShapeId`, `Span`, `SymbolRef`) |
| `chronos-model` | 18 | 103 | Covers all IR records + `IrJsonCodecRoundTripTest` (2 tests) |
| `chronos-parser` | 11 | 146 | Grammar branch coverage, `ChronosParserFacade`, relationships, state machines, error/deny/invariant parsing |
| `chronos-compiler` | 2 | 10 | `ChronosCompilerTest` (7 tests); `LinkingInvariantTest` (3 tests) |
| `chronos-validator` | 28 | 161 | One class per CHR rule (CHR-011 through CHR-034, CHR-W001) |
| `chronos-generators` | 7 | 73 | Markdown PRD snapshot (`MarkdownPrdGeneratorSnapshotTest`), TypeScript types, Mermaid, state machine scaffolding |
| `chronos-cli` | 14 | 78 | Build/validate/generate commands + build config + model projection |
| **Total** | **85** | **618** | |

### Snapshot Tests

| Test | Golden File | Notes |
|---|---|---|
| [MarkdownPrdGeneratorSnapshotTest.java](../../chronos-generators/src/test/java/com/genairus/chronos/generators/MarkdownPrdGeneratorSnapshotTest.java) | [examples/integration/checkout-prd.golden.md](../../examples/integration/checkout-prd.golden.md) | Character-for-character comparison of full PRD output |

### JSON Codec Tests

| Test | Location | Notes |
|---|---|---|
| [IrJsonCodecRoundTripTest.java](../../chronos-model/src/test/java/com/genairus/chronos/ir/json/IrJsonCodecRoundTripTest.java) | `chronos-model` | 2 round-trip tests; covers type discriminators and resolved `SymbolRef` serialization |

### Compiler-Specific Tests

| Test | @Test Count | What It Covers |
|---|---|---|
| [ChronosCompilerTest.java](../../chronos-compiler/src/test/java/com/genairus/chronos/compiler/ChronosCompilerTest.java) | 7 | CHR-005, CHR-008, valid/invalid programs, syntax errors, `success()` flag filtering |
| [LinkingInvariantTest.java](../../chronos-compiler/src/test/java/com/genairus/chronos/compiler/LinkingInvariantTest.java) | 3 | `finalized` invariant; undefined actor → `finalized=false`; valid actor → `finalized=true`; undefined relationship entity |

### Highest-Leverage Missing Tests (Top 5)

| # | Missing Test | Why It Matters | Suggested Location |
|---|---|---|---|
| 1 | **Phase 5 round-trip: SymbolRef resolved → JSON → deserialized** | Proves the IR JSON codec correctly encodes resolved `SymbolRef`s that come out of `CrossLinkResolutionPhase`; the current round-trip test uses manually constructed refs | `chronos-model` — expand `IrJsonCodecRoundTripTest` |
| 2 | **`IrTraversal` completeness: assert no SymbolRef fields exist outside the scanned set** | Guards against adding a new `IrShape` with a `SymbolRef` field and forgetting to update `IrTraversal`; currently there is no such regression check | `chronos-compiler` — `IrTraversalCompletenessTest` |
| 3 | **CLI `validate` with a model that has an unresolved actor** | Confirms the CLI exits 1 and emits a useful error; today the CLI uses the legacy parser which doesn't do `SymbolRef` resolution, so actor refs are never checked — this test would expose the gap | `chronos-cli` — `ValidateCommandTest` |
| 4 | **`FinalizeIrPhase` alone unit test** | `LinkingInvariantTest` tests the full compiler; there is no isolated test that drives `FinalizeIrPhase` directly with a hand-crafted model containing a known unresolved ref | `chronos-compiler` — `FinalizeIrPhaseTest` |
| 5 | **`CollectSymbolsPhase` + `CrossLinkResolutionPhase` in isolation** | Phases 2 and 5 have no dedicated unit tests; currently only exercised end-to-end via `ChronosCompilerTest` | `chronos-compiler` — `CollectSymbolsPhaseTest`, `CrossLinkResolutionPhaseTest` |

---

## 7. Recommended Next Steps

### Option A — Wire `chronos-compiler` into the CLI (Low Risk, High Impact)

**What changes:**
Add `implementation(project(":chronos-compiler"))` to
[chronos-cli/build.gradle.kts](../../chronos-cli/build.gradle.kts).
Replace `ChronosModelParser.parseFile()` + `ChronosValidator.validate()` in
`BuildCommand`, `ValidateCommand`, and `GenerateCommand` with
`ChronosCompiler.compile(source, name)`.
Check `result.finalized()` instead of `result.hasErrors()` as the success gate.

**Why it matters:**
The 7-phase compiler, `SymbolRef` resolution, and `finalized` invariant exist but
are never used in production. This is the single change that closes the gap between
the architecture as designed and the architecture as deployed.

**Estimated risk:** Low — the compiler is already fully tested; it's a drop-in
replacement for the parse+validate pattern the CLI already follows.

**Modules touched:** `chronos-cli`

---

### Option B — Upgrade Remaining Raw-String Refs to `SymbolRef` (Medium Risk, Architectural)

**What changes:**
Convert the raw-string "eventual refs" in
`EntityDef.parentType`, `ActorDef.parentType`, `InvariantDef.scope`,
`DenyDef.scope`, and `StateMachineDef.entityName` from `String`/`Optional<String>`
to `SymbolRef` (or `List<SymbolRef>`).
Add corresponding resolution logic in `CrossLinkResolutionPhase`.
Extend `IrTraversal.findUnresolvedRefs()` to scan the new fields.
Update JSON codec mixins.

**Why it matters:**
Completes the resolution model so `finalized == true` is a **complete** structural guarantee (no raw-string references to other shapes anywhere in the IR), not just a partial one. Enables correct cross-model analysis, IDE tooling, and future refactoring support.

**Estimated risk:** Medium — touches `chronos-model`, `chronos-compiler`, `chronos-validators`, JSON codec, and many tests. The validator already checks these as string comparisons; those checks become simpler (compare `ShapeId`s) but require rewriting.

**Modules touched:** `chronos-model`, `chronos-compiler`, `chronos-validator`, `chronos-model` (JSON codec)

---

### Option C — Add `IrTraversal` Completeness Guard + Phase Unit Tests (Low Risk, Defensive)

**What changes:**
1. Add a `IrTraversalCompletenessTest` that uses reflection to enumerate all `IrShape` subtype fields of type `SymbolRef` and asserts the known set matches what `IrTraversal` scans.
2. Add isolated unit tests for `CollectSymbolsPhase`, `TypeResolutionPhase`, `CrossLinkResolutionPhase`, and `FinalizeIrPhase`.
3. Expand `IrJsonCodecRoundTripTest` with a full-compiler-output round-trip.

**Why it matters:**
The compiler has only 10 tests across 7 phases. These defensive tests lock the
invariants so that Options A and B can be implemented safely without silent
regressions.

**Estimated risk:** Low — tests only, no production code changes.

**Modules touched:** `chronos-compiler`, `chronos-model`

---

## Appendix: File Paths Reference

```
chronos-core/src/main/java/com/genairus/chronos/core/
  refs/                   Span, QualifiedName, ShapeId, SymbolRef, SymbolKind
  diagnostics/            Diagnostic, DiagnosticCollector, DiagnosticSeverity

chronos-model/src/main/java/com/genairus/chronos/ir/
  model/IrModel.java
  types/                  IrShape (sealed) + 13 implementations
  json/IrJsonCodec.java

chronos-compiler/src/main/java/com/genairus/chronos/compiler/
  ChronosCompiler.java    Pipeline entry point
  CompileResult.java      Result record (model, diagnostics, parsed, finalized)
  ResolverContext.java    Shared phase state
  phases/                 7 phase implementations
  resolve/RefResolver.java
  symbols/SymbolTable.java, Symbol.java
  util/IrTraversal.java

chronos-cli/src/main/java/com/genairus/chronos/cli/
  BuildCommand.java       ← uses legacy ChronosModelParser.parseFile (NOT compiler)
  ValidateCommand.java    ← uses legacy ChronosModelParser.parseFile (NOT compiler)
  GenerateCommand.java    ← uses legacy ChronosModelParser.parseFile (NOT compiler)
```
