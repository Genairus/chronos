# Chronos IR Mapping Reference

Maps every semantic parser rule in `Chronos.g4` to its Syntax DTO, its IR type,
required/normalized fields, reference-resolution obligations, and applicable
validation rules (CHR-xxx).

Convention used throughout:

| Symbol | Meaning |
|--------|---------|
| `Ref<T>` | A resolved IR pointer; replaces a raw String name after Pass 6 |
| `ShapeId` | Opaque stable identity key assigned to every top-level declaration |
| `Span` | `(file, line, col, length)` — present on every Syntax DTO, stripped from Finalized IR |
| `?` | Optional — may be absent/null at parse time |
| `[T]` | `List<T>` (unmodifiable in Finalized IR) |
| `⚠ ref` | This field contains a raw name that **must** be resolved — flagged per the acceptance criteria |

---

## 1. File / Namespace

### 1.1 `namespaceDecl`

| Attribute | Value |
|-----------|-------|
| Grammar rule | `namespaceDecl : 'namespace' qualifiedId` |
| Syntax DTO | `SyntaxNamespace` |
| IR type | `IrNamespace` (embedded in `IrModel`) |

**Fields**

| Field | Syntax DTO | IR type | Notes |
|-------|-----------|---------|-------|
| `qualifiedName` | `String` (dotted) | `String` | No resolution needed — this IS the identity of the file |
| `span` | `Span` | — | Stripped after finalization |

**Validations:** none; missing namespace is a parse error (CHR-000).

---

### 1.2 `useDecl`

| Attribute | Value |
|-----------|-------|
| Grammar rule | `useDecl : 'use' qualifiedId '#' ID` |
| Syntax DTO | `SyntaxUseDecl` |
| IR type | `IrImport` |

**Fields**

| Field | Syntax DTO | IR type | Resolution |
|-------|-----------|---------|-----------|
| `namespace` | `String` (dotted) | `String` | Identifies the source module — resolved at Pass 2 (multi-file, ADR-0003) |
| `memberName` | `String` | `String` | ⚠ ref — the name after `#`; looked up in the imported module's SymbolTable |
| `span` | `Span` | — | |

**Validations:**
- CHR-015 (planned): imported name does not exist in target namespace.

---

## 2. Trait System

### 2.1 `traitApplication`

| Attribute | Value |
|-----------|-------|
| Grammar rule | `traitApplication : '@' traitId traitArgList?` |
| Syntax DTO | `SyntaxTrait` |
| IR type | `IrTrait` |

**Fields**

| Field | Syntax DTO | IR type | Notes |
|-------|-----------|---------|-------|
| `name` | `String` | `String` | Trait name; known names validated in Pass 4 |
| `args` | `List<SyntaxTraitArg>` | `List<IrTraitArg>` | |
| `span` | `Span` | — | |

---

### 2.2 `traitArg`

| Attribute | Value |
|-----------|-------|
| Grammar rule | `traitArg : (ID ':')? traitValue` |
| Syntax DTO | `SyntaxTraitArg` |
| IR type | `IrTraitArg` (sealed: `Named` / `Positional`) |

**Fields**

| Field | Syntax DTO | IR type | Notes |
|-------|-----------|---------|-------|
| `key` | `String?` | `String?` | Present only for named args |
| `value` | `SyntaxTraitValue` | `IrTraitValue` | See §2.3 |
| `span` | `Span` | — | |

**Validations:**
- CHR-020: unknown trait name.
- CHR-021: wrong number of args for known trait.
- CHR-022: named arg key not recognized for this trait.
- CHR-023: positional arg used where named-only trait expected.

---

### 2.3 `traitValue`

| Attribute | Value |
|-----------|-------|
| Grammar rule | `traitValue : STRING \| NUMBER \| BOOL \| qualifiedId` |
| Syntax DTO | `SyntaxTraitValue` (sealed) |
| IR type | `IrTraitValue` (sealed) |

Variants:

| Variant | Syntax DTO | IR type | Resolution |
|---------|-----------|---------|-----------|
| String literal | `SyntaxTraitValue.Str(String raw, Span)` | `IrTraitValue.Str(String)` | — |
| Number literal | `SyntaxTraitValue.Num(String raw, Span)` | `IrTraitValue.Num(double)` | Parsed in Pass 4 |
| Boolean literal | `SyntaxTraitValue.Bool(String raw, Span)` | `IrTraitValue.Bool(boolean)` | Parsed in Pass 4 |
| Shape reference | `SyntaxTraitValue.Ref(String qualifiedName, Span)` | `IrTraitValue.ShapeRef(Ref<IrShape>)` | ⚠ ref — resolved in Pass 6 |

---

## 3. Type References

### 3.1 `typeRef`

| Attribute | Value |
|-----------|-------|
| Grammar rule | `typeRef : 'List' '<' typeRef '>' \| 'Map' '<' typeRef ',' typeRef '>' \| primitiveType \| qualifiedId` |
| Syntax DTO | `SyntaxTypeRef` (sealed) |
| IR type | `IrTypeRef` (sealed) |

Variants:

| Variant | Syntax DTO | IR type | Resolution |
|---------|-----------|---------|-----------|
| Primitive | `SyntaxTypeRef.Primitive(String name, Span)` | `IrTypeRef.Primitive(PrimitiveKind)` | Resolved statically in Pass 3 |
| Named list | `SyntaxTypeRef.ListOf(SyntaxTypeRef element, Span)` | `IrTypeRef.ListOf(IrTypeRef element)` | Recursive resolution |
| Named map | `SyntaxTypeRef.MapOf(SyntaxTypeRef key, SyntaxTypeRef value, Span)` | `IrTypeRef.MapOf(IrTypeRef key, IrTypeRef value)` | Recursive resolution |
| Shape reference | `SyntaxTypeRef.Named(String qualifiedName, Span)` | `IrTypeRef.Shape(Ref<IrShape>)` | ⚠ ref — Pass 3 |

**`PrimitiveKind` enum:** `STRING`, `INTEGER`, `LONG`, `FLOAT`, `BOOLEAN`, `TIMESTAMP`, `BLOB`, `DOCUMENT`.

**Validations:**
- CHR-008: referenced shape name not found in SymbolTable or imports.

---

## 4. Entity

### 4.1 `entityDef`

| Attribute | Value |
|-----------|-------|
| Grammar rule | `entityDef : 'entity' ID ('extends' ID)? '{' entityMember* '}'` |
| Syntax DTO | `SyntaxEntity` |
| IR type | `IrEntity` |

**Fields**

| Field | Syntax DTO | IR type | Resolution |
|-------|-----------|---------|-----------|
| `name` | `String` | `String` | Registered as `ShapeId` in SymbolTable (Pass 2) |
| `traits` | `List<SyntaxTrait>` | `List<IrTrait>` | Normalized in Pass 4 |
| `docComments` | `List<String>` | `List<String>` | Carried verbatim |
| `parentName` | `String?` | `Ref<IrEntity>?` | ⚠ ref — must resolve to another `entity`; Pass 5 |
| `fields` | `List<SyntaxField>` | `List<IrField>` | Flattened with parent fields in Pass 5 |
| `invariants` | `List<SyntaxEntityInvariant>` | `List<IrEntityInvariant>` | See §4.3 |
| `span` | `Span` | — | |

**Normalized form:** after Pass 5, `fields` contains parent fields (in declaration order) followed by own fields.  Duplicate field names → error.

**Validations:**
- CHR-011: inheritance cycle detected.
- CHR-012: `extends` target is not an `entity` (wrong kind).
- CHR-013: duplicate field name in entity (after flattening).

---

### 4.2 `fieldDef`

| Attribute | Value |
|-----------|-------|
| Grammar rule | `fieldDef : traitApplication* ID ':' typeRef` |
| Syntax DTO | `SyntaxField` |
| IR type | `IrField` |

**Fields**

| Field | Syntax DTO | IR type | Resolution |
|-------|-----------|---------|-----------|
| `name` | `String` | `String` | Local to enclosing shape |
| `traits` | `List<SyntaxTrait>` | `List<IrTrait>` | Normalized in Pass 4 |
| `type` | `SyntaxTypeRef` | `IrTypeRef` | ⚠ ref — Pass 3 |
| `span` | `Span` | — | |

---

### 4.3 `entityInvariant`

| Attribute | Value |
|-----------|-------|
| Grammar rule | `entityInvariant : 'invariant' ID '{' invariantField+ '}'` |
| Syntax DTO | `SyntaxEntityInvariant` |
| IR type | `IrEntityInvariant` |

**Fields** — see §10 (global `invariantDef`) for the shared `invariantField` mapping.
Entity-scoped invariants carry no `scope` field (scope is the enclosing entity).

---

## 5. Shape (Value Object)

### 5.1 `shapeStructDef`

| Attribute | Value |
|-----------|-------|
| Grammar rule | `shapeStructDef : 'shape' ID '{' fieldDef* '}'` |
| Syntax DTO | `SyntaxShapeStruct` |
| IR type | `IrShapeStruct` |

**Fields**

| Field | Syntax DTO | IR type | Resolution |
|-------|-----------|---------|-----------|
| `name` | `String` | `String` | Registered as `ShapeId` in SymbolTable |
| `traits` | `List<SyntaxTrait>` | `List<IrTrait>` | |
| `docComments` | `List<String>` | `List<String>` | |
| `fields` | `List<SyntaxField>` | `List<IrField>` | Each field's type is ⚠ ref |
| `span` | `Span` | — | |

**Note:** `shape` has no `extends`; no inheritance resolution needed.

---

## 6. Named Collection Types

### 6.1 `listDef`

| Attribute | Value |
|-----------|-------|
| Grammar rule | `listDef : 'list' ID '{' 'member' ':' typeRef '}'` |
| Syntax DTO | `SyntaxListDef` |
| IR type | `IrListDef` |

**Fields**

| Field | Syntax DTO | IR type | Resolution |
|-------|-----------|---------|-----------|
| `name` | `String` | `String` | Registered as `ShapeId` |
| `traits` | `List<SyntaxTrait>` | `List<IrTrait>` | |
| `memberType` | `SyntaxTypeRef` | `IrTypeRef` | ⚠ ref — Pass 3 |
| `span` | `Span` | — | |

---

### 6.2 `mapDef`

| Attribute | Value |
|-----------|-------|
| Grammar rule | `mapDef : 'map' ID '{' 'key' ':' typeRef 'value' ':' typeRef '}'` |
| Syntax DTO | `SyntaxMapDef` |
| IR type | `IrMapDef` |

**Fields**

| Field | Syntax DTO | IR type | Resolution |
|-------|-----------|---------|-----------|
| `name` | `String` | `String` | Registered as `ShapeId` |
| `traits` | `List<SyntaxTrait>` | `List<IrTrait>` | |
| `keyType` | `SyntaxTypeRef` | `IrTypeRef` | ⚠ ref — Pass 3 |
| `valueType` | `SyntaxTypeRef` | `IrTypeRef` | ⚠ ref — Pass 3 |
| `span` | `Span` | — | |

---

## 7. Enum

### 7.1 `enumDef`

| Attribute | Value |
|-----------|-------|
| Grammar rule | `enumDef : 'enum' ID '{' enumMember+ '}'` |
| Syntax DTO | `SyntaxEnumDef` |
| IR type | `IrEnumDef` |

**Fields**

| Field | Syntax DTO | IR type | Notes |
|-------|-----------|---------|-------|
| `name` | `String` | `String` | Registered as `ShapeId` |
| `traits` | `List<SyntaxTrait>` | `List<IrTrait>` | |
| `docComments` | `List<String>` | `List<String>` | |
| `members` | `List<SyntaxEnumMember>` | `List<IrEnumMember>` | See §7.2 |
| `span` | `Span` | — | |

---

### 7.2 `enumMember`

| Attribute | Value |
|-----------|-------|
| Grammar rule | `enumMember : ID ('=' NUMBER)?` |
| Syntax DTO | `SyntaxEnumMember` |
| IR type | `IrEnumMember` |

**Fields**

| Field | Syntax DTO | IR type | Notes |
|-------|-----------|---------|-------|
| `name` | `String` | `String` | |
| `ordinal` | `String?` (raw NUMBER token) | `Integer?` | Parsed to int in Pass 4 |
| `span` | `Span` | — | |

**Normalized form:** if `ordinal` is absent, IR assigns auto-increment ordinals starting at 0 (or 1 — TBD by convention; document in ADR-0002).

**Validations:**
- CHR-014: duplicate enum member name within same enum.

---

## 8. Actor

### 8.1 `actorDef`

| Attribute | Value |
|-----------|-------|
| Grammar rule | `actorDef : 'actor' ID ('extends' ID)?` |
| Syntax DTO | `SyntaxActor` |
| IR type | `IrActor` |

**Fields**

| Field | Syntax DTO | IR type | Resolution |
|-------|-----------|---------|-----------|
| `name` | `String` | `String` | Registered as `ShapeId` |
| `traits` | `List<SyntaxTrait>` | `List<IrTrait>` | |
| `docComments` | `List<String>` | `List<String>` | |
| `parentName` | `String?` | `Ref<IrActor>?` | ⚠ ref — must resolve to another `actor`; Pass 5 |
| `span` | `Span` | — | |

**Validations:**
- CHR-012: `extends` target is not an `actor`.
- CHR-011: actor inheritance cycle.

---

## 9. Policy

### 9.1 `policyDef`

| Attribute | Value |
|-----------|-------|
| Grammar rule | `policyDef : 'policy' ID '{' 'description' ':' STRING '}'` |
| Syntax DTO | `SyntaxPolicy` |
| IR type | `IrPolicy` |

**Fields**

| Field | Syntax DTO | IR type | Notes |
|-------|-----------|---------|-------|
| `name` | `String` | `String` | Registered as `ShapeId` |
| `traits` | `List<SyntaxTrait>` | `List<IrTrait>` | |
| `docComments` | `List<String>` | `List<String>` | |
| `description` | `String` | `String` | String literal; unquoted in Pass 1 |
| `span` | `Span` | — | |

---

## 10. Journey

### 10.1 `journeyDef`

| Attribute | Value |
|-----------|-------|
| Grammar rule | `journeyDef : 'journey' ID '{' journeyBody '}'` |
| Syntax DTO | `SyntaxJourney` |
| IR type | `IrJourney` |

**Fields**

| Field | Syntax DTO | IR type | Resolution |
|-------|-----------|---------|-----------|
| `name` | `String` | `String` | Registered as `ShapeId` |
| `traits` | `List<SyntaxTrait>` | `List<IrTrait>` | |
| `docComments` | `List<String>` | `List<String>` | |
| `actorName` | `String?` | `Ref<IrActor>?` | ⚠ ref — resolved in Pass 6 |
| `preconditions` | `List<String>` | `List<String>` | String literals; no resolution needed |
| `steps` | `List<SyntaxStep>` | `List<IrStep>` | See §10.3 |
| `variants` | `List<SyntaxVariant>` | `List<IrVariant>` | See §10.5 |
| `outcomes` | `SyntaxJourneyOutcomes?` | `IrJourneyOutcomes?` | See §10.7 |
| `span` | `Span` | — | |

**Validations:**
- CHR-001: `actorName` absent (null).
- CHR-002: `outcomes` block absent.
- CHR-009: no `@kpi` trait present (WARNING).

---

### 10.2 `actorDecl` _(inside journeyBody)_

| Grammar rule | `actorDecl : 'actor' ':' ID` |
|---|---|
| Storage | `SyntaxJourney.actorName` (raw String) |
| IR storage | `IrJourney.actor` (`Ref<IrActor>`) |
| ⚠ ref | The ID must resolve to an `actor` declaration in SymbolTable — Pass 6 |

---

### 10.3 `step`

| Attribute | Value |
|-----------|-------|
| Grammar rule | `step : traitApplication* 'step' ID '{' stepField* '}'` |
| Syntax DTO | `SyntaxStep` |
| IR type | `IrStep` |

**Fields**

| Field | Syntax DTO | IR type | Resolution |
|-------|-----------|---------|-----------|
| `name` | `String` | `String` | Scoped to enclosing journey; validated as unique within journey |
| `traits` | `List<SyntaxTrait>` | `List<IrTrait>` | |
| `action` | `String?` | `String?` | String literal |
| `expectation` | `String?` | `String?` | String literal |
| `outcome` | `SyntaxOutcomeExpr?` | `IrOutcomeExpr?` | ⚠ ref — see §10.4 |
| `telemetry` | `List<String>` | `List<String>` | ⚠ ref — IDs reference named events (Pass 6; CHR-028 planned) |
| `risk` | `String?` | `String?` | Free-text annotation; no resolution |
| `span` | `Span` | — | |

**Validations:**
- CHR-003: `action` or `expectation` absent.
- CHR-025 (planned): duplicate step name within journey.

---

### 10.4 `outcomeExpr`

| Attribute | Value |
|-----------|-------|
| Grammar rule | `outcomeExpr : 'TransitionTo' '(' ID ')' \| 'ReturnToStep' '(' ID ')'` |
| Syntax DTO | `SyntaxOutcomeExpr` (sealed) |
| IR type | `IrOutcomeExpr` (sealed) |

Variants:

| Variant | Syntax DTO | IR type | Resolution |
|---------|-----------|---------|-----------|
| `TransitionTo` | `SyntaxOutcomeExpr.TransitionTo(String stateName, Span)` | `IrOutcomeExpr.TransitionTo(Ref<IrState>)` | ⚠ ref — state name must exist in a `statemachine` reachable from this journey; Pass 6 |
| `ReturnToStep` | `SyntaxOutcomeExpr.ReturnToStep(String stepName, Span)` | `IrOutcomeExpr.ReturnToStep(Ref<IrStep>)` | ⚠ ref — step name must exist within the enclosing journey (or variant's parent journey); Pass 6 |

**Validations:**
- CHR-026 (planned): `ReturnToStep` target step name not found in journey.
- CHR-029 (planned): `TransitionTo` state not found in any linked statemachine.

---

### 10.5 `variantEntry` / `variantsDecl`

| Attribute | Value |
|-----------|-------|
| Grammar rule | `variantEntry : ID ':' '{' variantBody '}'` |
| Syntax DTO | `SyntaxVariant` |
| IR type | `IrVariant` |

**Fields**

| Field | Syntax DTO | IR type | Resolution |
|-------|-----------|---------|-----------|
| `name` | `String` | `String` | Scoped to enclosing journey |
| `triggerName` | `String` | `Ref<IrError>` | ⚠ ref — must resolve to an `error` declaration; Pass 6 |
| `steps` | `List<SyntaxStep>` | `List<IrStep>` | Same rules as §10.3 |
| `outcome` | `SyntaxOutcomeExpr?` | `IrOutcomeExpr?` | ⚠ ref — same rules as §10.4 |
| `span` | `Span` | — | |

**Validations:**
- CHR-027: `triggerName` does not resolve to an `error` shape.

---

### 10.6 `preconditionsDecl`

| Grammar rule | `preconditionsDecl : 'preconditions' ':' '[' STRING (',' STRING)* ']'` |
|---|---|
| Storage | `SyntaxJourney.preconditions : List<String>` |
| IR storage | `IrJourney.preconditions : List<String>` |
| Resolution | None — string literals only |

---

### 10.7 `outcomesDecl` / `outcomeEntry`

| Attribute | Value |
|-----------|-------|
| Grammar rule | `outcomesDecl : 'outcomes' ':' '{' outcomeEntry (',' outcomeEntry)* '}'` |
| Syntax DTO | `SyntaxJourneyOutcomes` |
| IR type | `IrJourneyOutcomes` |

**Fields**

| Field | Syntax DTO | IR type | Notes |
|-------|-----------|---------|-------|
| `success` | `String?` | `String?` | String literal; may be absent |
| `failure` | `String?` | `String?` | String literal; may be absent |
| `span` | `Span` | — | |

**Normalized form:** at least one of `success` or `failure` must be present; both-absent is a parse-level error (grammar requires ≥1 `outcomeEntry`). No reference resolution needed.

---

## 11. Relationship

### 11.1 `relationshipDef`

| Attribute | Value |
|-----------|-------|
| Grammar rule | `relationshipDef : 'relationship' ID '{' relationshipBody '}'` |
| Syntax DTO | `SyntaxRelationship` |
| IR type | `IrRelationship` |

**Fields**

| Field | Syntax DTO | IR type | Resolution |
|-------|-----------|---------|-----------|
| `name` | `String` | `String` | Registered as `ShapeId` |
| `traits` | `List<SyntaxTrait>` | `List<IrTrait>` | |
| `docComments` | `List<String>` | `List<String>` | |
| `fromName` | `String` | `Ref<IrEntity>` | ⚠ ref — must resolve to an `entity`; Pass 6 |
| `toName` | `String` | `Ref<IrEntity>` | ⚠ ref — must resolve to an `entity`; Pass 6 |
| `cardinality` | `String` (one of three keywords) | `Cardinality` (enum) | Parsed to enum in Pass 1 (lexically known) |
| `semantics` | `String?` | `RelationshipSemantics?` | Parsed to enum in Pass 1 if present |
| `inverseName` | `String?` | `Ref<IrRelationship>?` | ⚠ ref — must resolve to another `relationship`; Pass 6 |
| `span` | `Span` | — | |

**`Cardinality` enum:** `ONE_TO_ONE`, `ONE_TO_MANY`, `MANY_TO_MANY`.
**`RelationshipSemantics` enum:** `ASSOCIATION`, `AGGREGATION`, `COMPOSITION`.

**Validations:**
- CHR-008: `fromName`, `toName`, or `inverseName` not found.
- CHR-016 (planned): `inverseName` does not resolve to a `relationship`.
- (planned, new code TBD): `from`/`to` do not resolve to an `entity`.

---

## 12. Invariant (Global)

### 12.1 `invariantDef`

| Attribute | Value |
|-----------|-------|
| Grammar rule | `invariantDef : 'invariant' ID '{' invariantField+ '}'` |
| Syntax DTO | `SyntaxInvariant` |
| IR type | `IrInvariant` |

**Fields**

| Field | Syntax DTO | IR type | Resolution |
|-------|-----------|---------|-----------|
| `name` | `String` | `String` | Registered as `ShapeId` |
| `traits` | `List<SyntaxTrait>` | `List<IrTrait>` | |
| `scope` | `List<String>?` | `List<Ref<IrShape>>?` | ⚠ ref — each ID resolves to any shape kind; Pass 6 |
| `expression` | `String?` | `String` | String literal; expression language TBD |
| `severity` | `String?` | `Severity` | Parsed to enum; see below |
| `message` | `String?` | `String?` | String literal |
| `span` | `Span` | — | |

**`Severity` enum (shared):** `WARNING`, `ERROR`, `CRITICAL` (plus INFO if needed).

**Normalized form:** `expression` is required for a global invariant; absent → validation error.

**Validations:**
- CHR-018 (planned): `expression` field missing.
- CHR-008: any name in `scope` list not found in SymbolTable.

---

### 12.2 `entityInvariant` _(entity-scoped)_

Same `invariantField` grammar as global `invariantDef` but:
- No `scope` field (entity is implicit scope).
- Registered only in the enclosing entity's field list, not in top-level SymbolTable.

| Syntax DTO | IR type |
|-----------|---------|
| `SyntaxEntityInvariant` | `IrEntityInvariant` |

| Field | Resolution |
|-------|-----------|
| `expression` | String literal (no resolution) |
| `severity` | Parsed to `Severity` enum |
| `message` | String literal |

---

## 13. Deny

### 13.1 `denyDef`

| Attribute | Value |
|-----------|-------|
| Grammar rule | `denyDef : 'deny' ID '{' denyField+ '}'` |
| Syntax DTO | `SyntaxDeny` |
| IR type | `IrDeny` |

**Fields**

| Field | Syntax DTO | IR type | Resolution |
|-------|-----------|---------|-----------|
| `name` | `String` | `String` | Registered as `ShapeId` |
| `traits` | `List<SyntaxTrait>` | `List<IrTrait>` | |
| `description` | `String` | `String` | String literal |
| `scope` | `List<String>` | `List<Ref<IrShape>>` | ⚠ ref — each ID resolves to any shape kind; Pass 6 |
| `severity` | `String` | `Severity` | Parsed to enum in Pass 4 |
| `span` | `Span` | — | |

**Validations:**
- CHR-008: name in `scope` list not found in SymbolTable.
- CHR-019 (planned): `severity` value not one of the known keywords.

---

## 14. Error

### 14.1 `errorDef`

| Attribute | Value |
|-----------|-------|
| Grammar rule | `errorDef : 'error' ID '{' errorField+ '}'` |
| Syntax DTO | `SyntaxError` |
| IR type | `IrError` |

**Fields**

| Field | Syntax DTO | IR type | Resolution |
|-------|-----------|---------|-----------|
| `name` | `String` | `String` | Registered as `ShapeId` — variant triggers resolve to this |
| `traits` | `List<SyntaxTrait>` | `List<IrTrait>` | |
| `code` | `String` | `String` | String literal (e.g. `"PAY-001"`) |
| `severity` | `String` | `Severity` | Parsed to enum |
| `recoverable` | `String` (BOOL token) | `boolean` | Parsed in Pass 1/4 |
| `message` | `String` | `String` | String literal |
| `payloadFields` | `List<SyntaxField>` | `List<IrField>` | Each field type is ⚠ ref — Pass 3 |
| `span` | `Span` | — | |

**Validations:**
- CHR-027 (consumer-side): any `variantEntry.triggerName` that resolves here must find an `IrError`; validated when resolving the variant, not the error itself.

---

## 15. State Machine

### 15.1 `statemachineDef`

| Attribute | Value |
|-----------|-------|
| Grammar rule | `statemachineDef : 'statemachine' ID '{' statemachineField* '}'` |
| Syntax DTO | `SyntaxStateMachine` |
| IR type | `IrStateMachine` |

**Fields**

| Field | Syntax DTO | IR type | Resolution |
|-------|-----------|---------|-----------|
| `name` | `String` | `String` | Registered as `ShapeId` |
| `traits` | `List<SyntaxTrait>` | `List<IrTrait>` | |
| `entityName` | `String?` | `Ref<IrEntity>?` | ⚠ ref — must resolve to an `entity`; Pass 6 |
| `fieldName` | `String?` | `String?` | Name of field on `entityName`; ⚠ ref — must exist on the resolved entity; Pass 6 |
| `states` | `List<String>` | `List<IrState>` | State names declared inline; registered in a per-SM scope |
| `initialState` | `String?` | `Ref<IrState>?` | ⚠ ref — must be in `states` list; Pass 6 |
| `terminalStates` | `List<String>` | `List<Ref<IrState>>` | ⚠ ref — each must be in `states` list; Pass 6 |
| `transitions` | `List<SyntaxTransition>` | `List<IrTransition>` | See §15.2 |
| `span` | `Span` | — | |

**Normalized form:**
- `states` list is de-duplicated (parse allows repetition by accident); duplicates → CHR-030 (planned).
- `IrState` is an inner record `{String name}` scoped to this SM; `TransitionTo` expressions in journey steps reference these.

**Validations:**
- CHR-008 (planned): `entityName` not found or not an `entity`.
- CHR-030 (planned): duplicate state name.
- CHR-031 (planned): `initialState` not in `states`.
- CHR-032 (planned): `terminalState` not in `states`.
- CHR-033 (planned): transition `from` or `to` state not in `states`.
- CHR-034 (planned): `fieldName` not found on resolved entity.

---

### 15.2 `transition`

| Attribute | Value |
|-----------|-------|
| Grammar rule | `transition : ID '->' ID ('{' transitionBody '}')?` |
| Syntax DTO | `SyntaxTransition` |
| IR type | `IrTransition` |

**Fields**

| Field | Syntax DTO | IR type | Resolution |
|-------|-----------|---------|-----------|
| `fromState` | `String` | `Ref<IrState>` | ⚠ ref — must be in enclosing SM's `states`; Pass 6 |
| `toState` | `String` | `Ref<IrState>` | ⚠ ref — must be in enclosing SM's `states`; Pass 6 |
| `guard` | `String?` | `String?` | Free-text expression; no resolution in current grammar |
| `action` | `String?` | `String?` | Free-text description; no resolution in current grammar |
| `span` | `Span` | — | |

---

## 16. Shared / Lexical Constructs

### 16.1 `qualifiedId`

| Grammar rule | `qualifiedId : ID ('.' ID)*` |
|---|---|
| Syntax DTO | `String` (dotted form, e.g. `com.example.checkout`) |
| IR usage | Used as-is for namespace and import paths; **all other uses are ⚠ refs resolved to typed IR pointers** |

### 16.2 `ID` (plain identifier)

Every occurrence of a plain `ID` that references a previously declared construct is a **⚠ ref** regardless of context.  The full list of `ID`-as-reference sites:

| Location | Grammar snippet | Resolves to |
|----------|----------------|-------------|
| entity `extends` | `'entity' ID ('extends' ID)?` | `Ref<IrEntity>` |
| actor `extends` | `'actor' ID ('extends' ID)?` | `Ref<IrActor>` |
| journey `actor:` | `'actor' ':' ID` | `Ref<IrActor>` |
| journey step `telemetry:` | `'[' ID (',' ID)* ']'` | `List<String>` (event names; CHR-028 planned) |
| step `outcome` / variant `outcome` | `ReturnToStep(ID)` | `Ref<IrStep>` |
| step `outcome` / variant `outcome` | `TransitionTo(ID)` | `Ref<IrState>` |
| variant `trigger:` | `'trigger' ':' ID` | `Ref<IrError>` |
| relationship `from:` | `'from' ':' ID` | `Ref<IrEntity>` |
| relationship `to:` | `'to' ':' ID` | `Ref<IrEntity>` |
| relationship `inverse:` | `'inverse' ':' ID` | `Ref<IrRelationship>` |
| statemachine `entity:` | `'entity' ':' ID` | `Ref<IrEntity>` |
| statemachine `field:` | `'field' ':' ID` | field name on resolved entity |
| statemachine `initial:` | `'initial' ':' ID` | `Ref<IrState>` (SM-local) |
| statemachine `terminal:` | `'[' ID (',' ID)* ']'` | `List<Ref<IrState>>` (SM-local) |
| statemachine transition `FROM -> TO` | `ID '->' ID` | `Ref<IrState>` × 2 (SM-local) |
| invariant/deny `scope:` | `'[' ID (',' ID)* ']'` | `List<Ref<IrShape>>` |
| typeRef (non-primitive) | `qualifiedId` in typeRef | `Ref<IrShape>` |
| traitValue (shape ref) | `qualifiedId` in traitValue | `Ref<IrShape>` |
| use `#` anchor | `'use' qualifiedId '#' ID` | cross-module lookup (ADR-0003) |

---

## Summary — Reference Resolution Checklist

| Pass | Refs resolved |
|------|--------------|
| Pass 3 — Resolve Types | `typeRef` → `IrTypeRef` for all fields, list members, map key/value, error payload |
| Pass 4 — Normalize Traits | trait arg `qualifiedId` values → `IrTraitValue.ShapeRef` |
| Pass 5 — Resolve Inheritance | `entity extends ID` → `Ref<IrEntity>`; `actor extends ID` → `Ref<IrActor>` |
| Pass 6 — Resolve Cross-links | journey actor, relationship from/to/inverse, variant trigger, statemachine entity/field/initial/terminal/transitions, step outcomeExpr IDs, invariant/deny scope IDs |

Any `ID` or `qualifiedId` not converted to a typed `Ref<T>` by the end of Pass 6 is an **unresolved reference** and produces a `Diagnostic(CHR-008, ...)`.  The Finalizer (Pass 8) refuses to produce a `FinalizedIrModel` if any `Unresolved` placeholder remains.
