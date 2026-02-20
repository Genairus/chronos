# ADR-0001: Chronos IR Layer and Multi-Pass Resolver Architecture

| Field       | Value                                            |
|-------------|--------------------------------------------------|
| Status      | Accepted                                         |
| Date        | 2026-02-19                                       |
| Revised     | 2026-02-19 (seven-module refactor; core cleanup) |
| Deciders    | Chronos compiler team                            |
| Supersedes  | —                                                |

---

## Context

The original `com.genairus.chronos.model` package served as both parsed representation
and generator input. Names were raw strings, references were unresolved, and ANTLR types
leaked across package boundaries. Generators therefore needed to re-implement symbol
lookup logic, which scattered business rules and made the codebase brittle.

This ADR establishes a clean layered architecture that separates concerns:
*parsing*, *symbol resolution*, *IR construction*, *validation*, and *generation*
each operate on a well-defined data structure and may not reach back into a lower layer.

**Module refactor (2026-02-19):** The original five-module layout
(`chronos-model`, `chronos-parser`, `chronos-validator`, `chronos-generators`,
`chronos-cli`) has been superseded by a seven-module layout that introduces
`chronos-core` as the common foundation and `chronos-compiler` as the explicit and
exclusive home of all symbol resolution and IR construction logic.  An earlier draft of
this ADR placed `Span`, `Ref<T>`, and diagnostic types inside `chronos-model`; that
placement is incorrect and has been removed.  All foundational types live exclusively in
`chronos-core`.  The module DAG, type inventory, and enforcement rules below are
**authoritative**; the old layout is preserved in this paragraph for historical context
only.

---

## Decision

### 1. Module Structure

#### 1.1 Module Dependency DAG

```
Module                  Depends on (chronos modules only)
─────────────────────────────────────────────────────────────────────
chronos-core            (none)
chronos-model           chronos-core
chronos-parser          chronos-core
chronos-validator       chronos-core, chronos-model
chronos-compiler        chronos-core, chronos-model,
                        chronos-parser, chronos-validator
chronos-generators      chronos-core, chronos-model
chronos-cli             chronos-compiler, chronos-generators
```

#### 1.2 Dependency Diagram

Edges point from dependent to dependency (A ──► B means "A depends on B").

```
                     ┌─────────────────────────────┐
                     │        chronos-core          │
                     │  Span · NamespaceId          │
                     │  QualifiedName · ShapeId     │
                     │  SymbolKind · SymbolRef       │
                     │  DiagnosticSeverity          │
                     │  Diagnostic · DiagnosticCollector │
                     └──┬─────┬──────┬──────┬───────┘
                        │     │      │      │
              ┌─────────┘     │      │      └──────────────┐
              │               │      │                     │
              ▼               │      ▼                     ▼
   ┌──────────────────┐       │  ┌──────────────────┐  ┌────────────────┐
   │  chronos-model   │       │  │  chronos-parser  │  │  (generators,  │
   │  IR node types   │       │  │  ANTLR grammar   │  │   validator,   │
   │  IR shapes       │       │  │  Syntax DTOs     │  │   compiler     │
   │  No ANTLR        │       │  │  Lowering only   │  │   — see below) │
   └──┬────────────┬──┘       │  └────────┬─────────┘  └────────────────┘
      │            │          │           │
      ▼            │          │           │
   ┌──────────────────┐       │           │
   │chronos-validator │       │           │
   │ValidationRule    │       │           │
   │ValidationRegistry│       │           │
   │CHR-xxx impls     │       │           │
   └─────────┬────────┘       │           │
             │                │           │
             └────────┐       │           │
                      ▼       ▼           ▼
              ┌───────────────────────────────────────┐
              │          chronos-compiler             │
              │  SymbolTable · ResolverContext        │
              │  Resolver phases (Passes 2–6)         │
              │  CompileResult · Finalize logic       │
              └──────────────────┬────────────────────┘
                                 │
              ┌──────────────────┤
              │                  │
              ▼                  ▼
   ┌──────────────────┐  ┌───────────────────────────┐
   │chronos-generators│  │       chronos-cli          │
   │Finalized IR only │  │ wires compiler + generators│
   └──────────────────┘  └───────────────────────────┘
              ▲                  │
              └──────────────────┘
```

#### 1.3 What Each Module Contains

| Module | Java packages | Authoritative type inventory |
|--------|--------------|------------------------------|
| `chronos-core` | `com.genairus.chronos.core`<br>`com.genairus.chronos.diagnostics` | `Span`, `NamespaceId`, `QualifiedName`, `ShapeId`, `SymbolKind`, `SymbolRef`<br>`DiagnosticSeverity`, `Diagnostic`, `DiagnosticCollector` |
| `chronos-model` | `com.genairus.chronos.ir` | IR node types and IR shapes: `IrJourney`, `IrActor`, `IrEntity`, `IrField`, `IrTypeRef`, `IrShapeStruct`, `IrEnumDef`, `IrStateMachine`, etc.<br>**No ANTLR imports. Does not define `Span`, `Diagnostic`, or any reference/symbol type.** |
| `chronos-parser` | `com.genairus.chronos.parser`<br>`com.genairus.chronos.syntax` | ANTLR grammar + generated classes (`ChronosLexer`, `ChronosParser`); Syntax DTOs (`SyntaxJourney`, `SyntaxEntity`, …); `ChronosAstVisitor` — **lowering only** (Passes 0–1) |
| `chronos-validator` | `com.genairus.chronos.validation` | `ValidationRule` (interface), `ValidationRegistry`, all CHR-xxx rule implementations (Pass 7) |
| `chronos-compiler` | `com.genairus.chronos.compiler` | `SymbolTable`, `ResolverContext`, all resolver phases (Passes 2–6), `CompileResult`, finalize logic (Pass 8); orchestrates the validator |
| `chronos-generators` | `com.genairus.chronos.generators` | Target-specific generators; consume `FinalizedIrModel` only — no Syntax DTOs, no `SymbolTable`, no diagnostic infrastructure |
| `chronos-cli` | `com.genairus.chronos.cli` | PicoCLI entry point; wires compiler + generators |

**chronos-core is intentionally minimal.**  Any type needed by only one module belongs
in that module, not in core.  `chronos-model` must not define or import `Span`,
`Diagnostic`, `DiagnosticCollector`, `SymbolRef`, or any other type already declared
in `chronos-core`.

---

### 2. Layer Boundaries

```
┌──────────────────────────────────────────────────────────────────────┐
│  .chronos source file(s)                                             │
└────────────────────────────────┬─────────────────────────────────────┘
                                 │  ANTLR 4 runtime
                                 ▼
┌──────────────────────────────────────────────────────────────────────┐
│  ANTLR Parse Tree                              [chronos-parser]       │
│  ChronosParser / ChronosLexer (generated)                            │
│  Package: com.genairus.chronos.parser  (ANTLR types STAY HERE)       │
└────────────────────────────────┬─────────────────────────────────────┘
                                 │  ChronosAstVisitor  (Pass 1 — Lower)
                                 ▼
┌──────────────────────────────────────────────────────────────────────┐
│  Syntax DTOs                                   [chronos-parser]       │
│  Package: com.genairus.chronos.syntax.*                              │
│                                                                      │
│  • Mirror the grammar 1-to-1; one DTO per grammar rule               │
│  • Names stored as raw String; references are NOT resolved           │
│  • Every node carries a Span (file, line, column, length)            │
│  • Allowed to be mutable during construction; sealed after           │
│  • May NOT import ANTLR types                                        │
└────────────────────────────────┬─────────────────────────────────────┘
                                 │  IrBuilder  (Passes 2–6 — Collect → Resolve)
                                 ▼
┌──────────────────────────────────────────────────────────────────────┐
│  Semantic IR  (partial / work-in-progress)                           │
│  IR types: com.genairus.chronos.ir.*           [chronos-model]       │
│  Construction: com.genairus.chronos.compiler.* [chronos-compiler]    │
│                                                                      │
│  • Names replaced by typed IR node references                        │
│  • Trait arguments parsed to typed values                            │
│  • Inheritance chains flattened into resolved field lists            │
│  • May contain Unresolved(...) placeholders during build             │
│  • Diagnostics emitted via DiagnosticCollector; NOT exceptions       │
│  • IR types may NOT import ANTLR types                               │
│  • IR types may NOT define Span, Diagnostic, or SymbolRef            │
└────────────────────────────────┬─────────────────────────────────────┘
                                 │  Validator  (Pass 7) [chronos-validator]
                                 │  Finalizer  (Pass 8) [chronos-compiler]
                                 ▼
┌──────────────────────────────────────────────────────────────────────┐
│  Finalized IR                                  [chronos-model]        │
│  Package: com.genairus.chronos.ir.*  (frozen state)                  │
│                                                                      │
│  • Immutable: Java records or classes with final fields              │
│  • All collections wrapped in Collections.unmodifiableList/Map       │
│  • Zero Unresolved placeholders — finalize() fails if any remain     │
│  • Carries no Span, Diagnostic, or SymbolRef data                    │
│  • Clean generator surface; all cross-references are typed pointers  │
│  • May NOT import ANTLR types                                        │
└────────────────────────────────┬─────────────────────────────────────┘
                                 │  GeneratorRegistry
                                 ▼
┌──────────────────────────────────────────────────────────────────────┐
│  Generators                                    [chronos-generators]   │
│  Package: com.genairus.chronos.generators.*                          │
│                                                                      │
│  • Accept FinalizedIrModel as sole input                             │
│  • Navigate the IR via typed references — NEVER string lookup        │
│  • May NOT import ANTLR types                                        │
│  • May NOT import com.genairus.chronos.syntax.*                      │
│  • May NOT import com.genairus.chronos.compiler.*                    │
└──────────────────────────────────────────────────────────────────────┘
```

---

### 3. Multi-Pass Resolver Pipeline

Each pass has a single responsibility and a stated invariant on entry and exit.

```
Source text
    │
    │  Pass 0 — Parse                                    [chronos-parser]
    │  Responsibility : Run ANTLR; produce a concrete parse tree.
    │  Entry  : raw UTF-8 bytes
    │  Exit   : ParseTree + token stream; any syntax error → Diagnostic(CHR-000)
    ▼
Syntax DTOs (via ChronosAstVisitor)
    │
    │  Pass 1 — Lower                                    [chronos-parser]
    │  Responsibility : Walk the ANTLR parse tree; emit one Syntax DTO per grammar
    │                   rule node.  Attach Span to every node.  Strip ANTLR types.
    │  Entry  : ParseTree, no unresolved refs yet (nothing to resolve yet)
    │  Exit   : SyntaxModel; all names are raw Strings; no ANTLR types outside parser
    ▼
SyntaxModel
    │
    │  Pass 2 — Collect Symbols                          [chronos-compiler]
    │  Responsibility : Walk SyntaxModel top-down; register every declared name
    │                   (entity, shape, enum, actor, policy, journey, relationship,
    │                   invariant, deny, error, statemachine, use-imported names)
    │                   in the SymbolTable with their kind and declaration Span.
    │  Entry  : SyntaxModel; SymbolTable is empty
    │  Exit   : SymbolTable fully populated; duplicate-name → Diagnostic(CHR-010)
    ▼
SymbolTable
    │
    │  Pass 3 — Resolve Types                            [chronos-compiler]
    │  Responsibility : For every typeRef in fieldDef, listDef, mapDef: look up the
    │                   raw name in SymbolTable (local then imported); replace raw
    │                   name with a typed IrTypeRef.  Primitives resolve statically.
    │  Entry  : SymbolTable complete; no IR nodes built yet
    │  Exit   : TypeRef map (raw name → IrTypeRef); unresolved → Diagnostic(CHR-008)
    ▼
TypeRef map
    │
    │  Pass 4 — Normalize Traits                         [chronos-compiler]
    │  Responsibility : Parse each TraitApplication's argument list into typed
    │                   IrTraitArg values (StringVal, NumberVal, BoolVal, RefVal).
    │                   Validate known trait names and arities.
    │  Entry  : SyntaxModel with raw trait arg strings
    │  Exit   : List<IrTrait> per shape/field/step; bad trait args → Diagnostic(CHR-020..024)
    ▼
IrTrait lists
    │
    │  Pass 5 — Resolve Inheritance                      [chronos-compiler]
    │  Responsibility : For entity and actor definitions that carry `extends`: look up
    │                   the parent in SymbolTable; verify kind compatibility; merge
    │                   parent fields into child (parent fields first, child fields
    │                   appended).  Detect cycles.
    │  Entry  : SymbolTable; entity/actor syntax nodes
    │  Exit   : Each entity/actor IR node has a flattened field list; cycles →
    │           Diagnostic(CHR-011); wrong-kind parent → Diagnostic(CHR-012)
    ▼
Flattened entity/actor IR nodes
    │
    │  Pass 6 — Resolve Cross-links                      [chronos-compiler]
    │  Responsibility : Resolve all remaining name references that cross shape
    │                   boundaries:
    │                   • journey actor: ID → ActorIr
    │                   • relationship from/to/inverse: ID → EntityIr
    │                   • statemachine entity: ID → EntityIr
    │                   • variant trigger: ID → ErrorIr          (CHR-027)
    │                   • step telemetry: [ID] → list of known names
    │                   • outcomeExpr step/state IDs: verified in journey/SM scope
    │  Entry  : All top-level IR nodes partially built; SymbolTable intact
    │  Exit   : All intra-model references replaced by direct IR node pointers;
    │           unresolved → Diagnostic(CHR-008) or construct-specific code
    ▼
Semantic IR (all references resolved, may still have Diagnostic list)
    │
    │  Pass 7 — Validate                                 [chronos-validator]
    │  Responsibility : Apply business rules that require a fully linked IR:
    │                   CHR-001 journey missing actor
    │                   CHR-002 journey missing outcomes
    │                   CHR-003 step missing action or expectation
    │                   CHR-009 journey missing @kpi trait (warning)
    │                   CHR-027 variant trigger not an error type
    │                   … and all other rules in com.genairus.chronos.validation.*
    │  Entry  : Semantic IR; zero Unresolved placeholders (resolver already emitted
    │           diagnostics for any that remain)
    │  Exit   : DiagnosticCollector populated; severity ≥ ERROR → finalize will refuse
    ▼
Validated Semantic IR + DiagnosticCollector
    │
    │  Pass 8 — Finalize                                 [chronos-compiler]
    │  Responsibility : If DiagnosticCollector contains no ERROR-or-above diagnostics:
    │                   deep-freeze all IR nodes (wrap collections, seal records).
    │                   Return FinalizedIrModel.  Otherwise return partial IR +
    │                   diagnostics so callers can report errors without crashing.
    │  Entry  : Validated semantic IR
    │  Exit   : FinalizedIrModel (success path) OR CompileResult with
    │           partial IR + non-empty DiagnosticCollector (error path)
    ▼
FinalizedIrModel  →  Generators
```

---

### 4. Package Responsibilities and Import Rules

| Package | Module | May import | Must NOT import |
|---------|--------|-----------|-----------------|
| `com.genairus.chronos.core` | `chronos-core` | _(nothing project-specific)_ | ANTLR, all other project packages |
| `com.genairus.chronos.diagnostics` | `chronos-core` | `core.*` | ANTLR, all other project packages |
| `com.genairus.chronos.ir` | `chronos-model` | `core.*`, `diagnostics.*` | ANTLR, `syntax.*`, `compiler.*`, `generators.*` |
| `com.genairus.chronos.syntax` | `chronos-parser` | `core.*`, `diagnostics.*` | ANTLR, `ir.*`, `compiler.*`, `generators.*` |
| `com.genairus.chronos.parser` | `chronos-parser` | ANTLR runtime, `syntax.*` | `ir.*`, `compiler.*`, `generators.*` |
| `com.genairus.chronos.validation` | `chronos-validator` | `ir.*`, `core.*`, `diagnostics.*` | ANTLR, `syntax.*`, `compiler.*`, `generators.*` |
| `com.genairus.chronos.compiler` | `chronos-compiler` | `syntax.*`, `ir.*`, `core.*`, `diagnostics.*`, `validation.*` | ANTLR, `generators.*` |
| `com.genairus.chronos.generators` | `chronos-generators` | `ir.*` | ANTLR, `syntax.*`, `compiler.*`, `validation.*`, `diagnostics.*` |

The "May import" column is a ceiling, not a floor; import only what is actually used.
Notably, `chronos-generators` has no reason to import `diagnostics.*` — generators
receive a `FinalizedIrModel` and either succeed or throw; they do not emit `Diagnostic`
objects.

---

### 5. Module Boundary Rules

These rules are **hard constraints** enforced at the module level by the Gradle build and
by ArchUnit tests in CI.  A PR that violates any rule must not be merged.

#### Rule 1 — ANTLR containment

> **No ANTLR class (`org.antlr.*`) may appear outside `chronos-parser`.**

`chronos-parser` is the only module that lists the ANTLR runtime as a compile-time
dependency.  All other modules declare no ANTLR dependency, making accidental leakage a
build error.  ArchUnit additionally verifies at test time that no class outside
`com.genairus.chronos.parser` imports from `org.antlr`.

#### Rule 2 — Symbol resolution containment

> **No symbol resolution logic may exist outside `chronos-compiler`.**

Passes 2–6 (symbol collection, type resolution, trait normalization, inheritance
flattening, cross-link resolution) and Pass 8 (finalization) are implemented exclusively
in `com.genairus.chronos.compiler`.  Modules such as `chronos-generators` and
`chronos-validator` receive IR nodes with all references already resolved as typed
pointers; they must never query a `SymbolTable` or look up a name as a raw `String`.

#### Rule 3 — chronos-core is foundational only

> **`chronos-core` contains ONLY:**
> `Span`, `NamespaceId`, `QualifiedName`, `ShapeId`, `SymbolKind`, `SymbolRef`,
> `DiagnosticSeverity`, `Diagnostic`, `DiagnosticCollector`.

No IR node types, no ANTLR types, no syntax DTOs, no `SymbolTable`, and no business
rules belong in `chronos-core`.  In particular, `chronos-model` must not define or
re-export any of these types; it must import them from `chronos-core`.  If a type is
needed by only one module, it belongs in that module, not in core.

---

### 6. "What Belongs Where" Examples

#### Foundational types in chronos-core

```java
// com.genairus.chronos.core  (chronos-core)
record Span(Path file, int line, int col, int length) {}
record ShapeId(String namespace, String name) {}
record SymbolRef(ShapeId id, SymbolKind kind) {}   // unresolved ref placeholder

enum SymbolKind { ENTITY, ACTOR, SHAPE, ENUM, JOURNEY, RELATIONSHIP,
                  STATEMACHINE, POLICY, ERROR, INVARIANT, DENY }
```

```java
// com.genairus.chronos.diagnostics  (chronos-core)
record Diagnostic(
    String code,                // e.g. "CHR-001"
    DiagnosticSeverity severity,
    String message,
    Span span
) {}

class DiagnosticCollector {
    void add(Diagnostic d) { ... }
    List<Diagnostic> all()  { ... }
    boolean hasErrors()     { ... }
}
```

#### Syntax DTO in chronos-parser — raw, unresolved

```java
// com.genairus.chronos.syntax  (chronos-parser)
// imports com.genairus.chronos.core.Span — NOT from chronos-model
record SyntaxJourney(
    String name,          // raw String, not a reference
    String actorName,     // raw String — may be null
    List<SyntaxStep> steps,
    Span span             // Span is defined in chronos-core
) {}
```

#### IR node in chronos-model — resolved, typed reference

```java
// com.genairus.chronos.ir  (chronos-model)
// Does NOT define Span, Diagnostic, SymbolRef, or DiagnosticCollector.
// Imports ShapeId from chronos-core only where an identity key is needed.
record IrJourney(
    String name,
    IrActor actor,        // direct object reference, never a String lookup
    List<IrStep> steps,
    List<IrTrait> traits
) {}
```

#### Symbol resolution in chronos-compiler only

```java
// com.genairus.chronos.compiler  (chronos-compiler)
class CrossLinkResolver {
    private final SymbolTable table;        // lives in chronos-compiler
    private final ResolverContext ctx;      // lives in chronos-compiler
    private final DiagnosticCollector diag; // imported from chronos-core

    // Pass 6: replaces SyntaxJourney.actorName (String) with IrJourney.actor (IrActor)
    IrJourney resolve(SyntaxJourney syntax) {
        IrActor actor = table.lookupActor(syntax.actorName())
            .orElseGet(() -> { diag.add(CHR_008(syntax.span())); return null; });
        return new IrJourney(syntax.name(), actor, ...);
    }
}
```

#### Validation rule in chronos-validator

```java
// com.genairus.chronos.validation  (chronos-validator)
// Imports only ir.* and core/diagnostics — never compiler.* or syntax.*
public class JourneyMissingActorRule implements ValidationRule {
    @Override
    public void validate(IrJourney journey, DiagnosticCollector diag) {
        if (journey.actor() == null) {
            diag.add(new Diagnostic("CHR-001", DiagnosticSeverity.ERROR,
                "Journey '" + journey.name() + "' has no actor declaration", null));
        }
    }
}
```

#### Generator in chronos-generators — consumes only FinalizedIrModel

```java
// com.genairus.chronos.generators  (chronos-generators)
// Does NOT import DiagnosticCollector, SymbolTable, or Syntax DTOs.
public class MarkdownPrdGenerator implements Generator {
    @Override
    public GeneratorOutput generate(FinalizedIrModel model) {
        // Navigate: model.journeys() returns List<IrJourney>
        // Each IrJourney carries a direct IrActor reference — no string lookup.
        for (IrJourney journey : model.journeys()) {
            IrActor actor = journey.actor();   // typed reference
            // NOT: model.findActor(journey.actorName())  ← FORBIDDEN
        }
    }
}
```

---

### 7. Acceptance Criteria

- [x] ADR includes a module dependency DAG and visual diagram covering all seven modules.
- [x] ADR provides an authoritative type inventory for every module, using exact class names.
- [x] ADR states explicitly: **`chronos-core` contains exactly**
      `Span`, `NamespaceId`, `QualifiedName`, `ShapeId`, `SymbolKind`, `SymbolRef`,
      `DiagnosticSeverity`, `Diagnostic`, `DiagnosticCollector` — nothing else.
- [x] ADR states explicitly: **`chronos-model` contains IR types and IR shapes only**.
      It does not define `Span`, `Diagnostic`, `DiagnosticCollector`, `SymbolRef`, or
      any other type already declared in `chronos-core`.
- [x] ADR states explicitly: **`chronos-parser` performs lowering only** (Passes 0–1).
      It owns ANTLR grammar, generated classes, and Syntax DTOs.
- [x] ADR states explicitly: **`chronos-compiler` owns `SymbolTable`, `ResolverContext`,
      all resolver phases (Passes 2–6), `CompileResult`, and finalization (Pass 8)**.
- [x] ADR states explicitly: **`chronos-validator` owns `ValidationRule`,
      `ValidationRegistry`, and all CHR-xxx rule implementations** (Pass 7).
- [x] ADR states explicitly: **no ANTLR class may appear outside `chronos-parser`**.
      CI enforces this with a build-level dependency boundary and an ArchUnit check.
- [x] ADR states explicitly: **no symbol resolution may occur outside `chronos-compiler`**.
      Generators and validators receive pre-resolved typed IR pointers only.
- [x] ADR states: **generators consume `FinalizedIrModel` only** — no Syntax DTOs,
      no `SymbolTable`, no `DiagnosticCollector`; all cross-references are typed pointers.
- [x] "What belongs where" section gives concrete Java examples for all seven modules.

---

## Consequences

**Positive**
- Module boundaries are enforced by the build system, not just by convention: a class in
  `chronos-generators` that accidentally imports from ANTLR will fail to compile.
- Generators become pure transformations over a well-typed graph; no defensive null
  checks for string-based lookups.
- All diagnostic logic is centralized in `chronos-core` (`diagnostics.*`); generators
  never throw.
- Each pass is independently unit-testable.
- `chronos-compiler` is the single place to look for any symbol-resolution bug.
- The explicit `chronos-core` dependency keeps foundational types stable and prevents
  circular dependencies between higher-level modules.

**Negative / Risks**
- Seven modules require more Gradle boilerplate than five.
- The split of IR type definitions (`chronos-model`) from IR construction logic
  (`chronos-compiler`) is a non-obvious seam; new contributors must read this ADR before
  adding IR-related code.
- Pass ordering is strict; mistakes in pass sequencing produce confusing NPEs during IR
  build (mitigated by entry invariant assertions in each pass).

---

## Related ADRs

- ADR-0002 (planned): Diagnostic code registry and severity escalation rules.
- ADR-0003 (planned): Multi-file compilation and cross-namespace import resolution.
