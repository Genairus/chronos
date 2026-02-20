# IR Type Canonicalization Status

**Date:** 2026-02-19
**Purpose:** Track migration from legacy `com.genairus.chronos.model.*` to canonical `com.genairus.chronos.ir.types.*`

---

## Problem

Two `JourneyDef` classes exist with the same simple name:

| Class | Package | Status |
|-------|---------|--------|
| `JourneyDef` | `com.genairus.chronos.ir.types` | **Canonical IR** — produced by compiler pipeline |
| `JourneyDef` | `com.genairus.chronos.model` | **Legacy** — produced by old ChronosModelParser |

The legacy model has 33 classes that mirror the IR types (same simple names, different packages), creating import ambiguity and confusion.

---

## Legacy Model Files (`com.genairus.chronos.model`)

33 classes total in `chronos-model/src/main/java/com/genairus/chronos/model/`:

**Shape types** (all have IR equivalents):
`ActorDef`, `DenyDef`, `EntityDef`, `EnumDef`, `ErrorDef`, `InvariantDef`, `JourneyDef`, `ListDef`, `MapDef`, `PolicyDef`, `RelationshipDef`, `ShapeStructDef`, `StateMachineDef`

**Support types**:
`Cardinality`, `ChronosModel`, `EnumMember`, `EntityInvariant`, `FieldDef`, `InheritanceResolver`, `JourneyOutcomes`, `OutcomeExpr`, `RelationshipSemantics`, `ShapeDefinition`, `SourceLocation`, `Step`, `StepField`, `TraitApplication`, `TraitArg`, `TraitValue`, `Transition`, `TypeRef`, `UseDecl`, `Variant`

---

## Pre-Migration Import Audit

### Files importing legacy `com.genairus.chronos.model.*` (41 total)

#### VIOLATIONS — modules that must not import legacy model

**chronos-cli (3 production files):**
- `DiffCommand.java` — uses `IrModelAdapter` + legacy `ShapeDefinition` for shape dispatch
- `ModelProjection.java` — uses legacy model types for projection filtering
- `SelectCommand.java` — uses `IrModelAdapter` + legacy `ShapeDefinition` for shape dispatch

> Note: `GenerateCommand.java` also used `IrModelAdapter` as an intermediary.

**chronos-generators (7 production files):**
- `ChronosGenerator.java` — interface takes `ChronosModel`
- `MarkdownPrdGenerator.java` — full dependency on legacy model API
- `MermaidStateDiagramGenerator.java` — uses legacy `StateMachineDef`, `Transition`
- `StateMachineTestGenerator.java` — uses legacy `StateMachineDef`, `Transition`
- `TestScaffoldGenerator.java` — uses legacy `EntityDef`, `EntityInvariant`, `InvariantDef`, `DenyDef`
- `TypeScriptTypesGenerator.java` — uses legacy `ChronosModel`, `FieldDef`, `TypeRef`, `EnumDef`, `ErrorDef`
- `compat/IrModelAdapter.java` — intentional bridge: converts `IrModel` → `ChronosModel` (now deleted)

#### EXPECTED — legacy usage that remains

**chronos-parser (14 files):**
- `ChronosModelBuilder.java`, `ChronosModelParser.java` — legacy parser pipeline (intentional)
- 12 test files — test the legacy parser (intentional)

**chronos-validator (12 files):**
- `ChronosValidator.java` — validator operates on legacy model
- `IrToChronosConverter.java` — bridge from IR to legacy for validation
- 10 test files — test the validator

#### CLEAN — correct IR usage

**chronos-compiler (all production files):** ✓ Zero legacy model imports
**chronos-model (IR codec):** ✓ Uses only `com.genairus.chronos.ir.types.*`

---

## Post-Migration State

After this cleanup, the following hold:

| Module | Legacy `model.*` imports | Status |
|--------|--------------------------|--------|
| chronos-compiler | 0 | ✓ Clean |
| chronos-generators | 0 | ✓ Clean (migrated) |
| chronos-cli | 0 | ✓ Clean (migrated) |
| chronos-parser | ~14 | Expected (legacy parser path) |
| chronos-validator | ~12 | Expected (validated separately) |

---

## Disambiguation (Task 3)

Legacy shape classes in `com.genairus.chronos.model` are marked `@Deprecated` to:
- Surface IDE warnings if accidentally imported in new code
- Make intent clear (these are legacy, IR types are canonical)

The `scripts/audit-forbidden-imports.sh` script enforces the boundary at the CI level.

---

## Canonical IR Types

For the main pipeline (compiler → generators → CLI), use only:

```
com.genairus.chronos.ir.model.IrModel      ← root model
com.genairus.chronos.ir.types.IrShape      ← sealed base interface
com.genairus.chronos.ir.types.EntityDef    ← entity
com.genairus.chronos.ir.types.JourneyDef   ← journey (THE canonical JourneyDef)
com.genairus.chronos.ir.types.ActorDef     ← actor
... (all 13 IR shape types)
```

The legacy `com.genairus.chronos.model.JourneyDef` is `@Deprecated` and should only appear in the legacy parser/validator path.
