grammar Chronos;

// ─── Entry Point ──────────────────────────────────────────────────────────────
// A model is one namespace declaration, zero or more imports, and any number
// of top-level shape definitions (entities, shapes, enums, actors, policies,
// journeys, and named collection types).

model
    : namespaceDecl useDecl* shapeDefinition* EOF
    ;

// ─── Namespace & Imports ──────────────────────────────────────────────────────

// 1.1  namespace com.example.checkout
namespaceDecl
    : 'namespace' qualifiedId
    ;

// 1.1  use com.example.entities#Order
useDecl
    : 'use' qualifiedId '#' ID
    ;

// ─── Top-Level Shape Definitions ─────────────────────────────────────────────
// Every top-level definition may be preceded by zero or more trait applications.

shapeDefinition
    : traitApplication* shapeDef
    ;

shapeDef
    : entityDef
    | shapeStructDef
    | listDef
    | mapDef
    | enumDef
    | actorDef
    | policyDef
    | journeyDef
    | relationshipDef
    ;

// ─── Trait System ─────────────────────────────────────────────────────────────
// 1.3  Three forms:
//        @pii                            bare
//        @description("text")           single positional arg
//        @kpi(metric: "X", target: "Y") named args

traitApplication
    : '@' traitId traitArgList?
    ;

// Allows any ID _or_ a contextual keyword as a trait name.
// Without this rule, keywords like `description` (used in policy bodies)
// would be tokenised as anonymous literals and not match `ID`.
traitId
    : ID
    | 'description'
    | 'member'
    | 'key'
    | 'value'
    | 'trigger'
    | 'success'
    | 'failure'
    | 'action'
    | 'expectation'
    | 'outcome'
    | 'telemetry'
    | 'risk'
    | 'steps'
    | 'step'
    | 'actor'
    | 'policy'
    | 'entity'
    | 'shape'
    | 'list'
    | 'map'
    | 'enum'
    | 'journey'
    | 'preconditions'
    | 'outcomes'
    | 'variants'
    | 'namespace'
    | 'use'
    | 'relationship'
    | 'from'
    | 'to'
    | 'cardinality'
    | 'semantics'
    | 'inverse'
    ;

traitArgList
    : '(' traitArg (',' traitArg)* ')'
    ;

// Named arg (key: value) takes priority via ordering; positional falls through.
traitArg
    : ID ':' traitValue
    | traitValue
    ;

// Trait values: string literal, number, boolean, or a shape reference.
traitValue
    : STRING
    | NUMBER
    | BOOL
    | qualifiedId
    ;

// ─── Type References ──────────────────────────────────────────────────────────
// 1.4  Primitive types + generic collection types + shape references.

typeRef
    : 'List' '<' typeRef '>'                   // e.g. List<OrderItem>
    | 'Map' '<' typeRef ',' typeRef '>'        // e.g. Map<String, String>
    | primitiveType
    | qualifiedId
    ;

primitiveType
    : 'String'
    | 'Integer'
    | 'Long'
    | 'Float'
    | 'Boolean'
    | 'Timestamp'
    | 'Blob'
    | 'Document'
    ;

// ─── Entity ───────────────────────────────────────────────────────────────────
// 1.5  Top-level business object with identity.
//
//   @pii
//   entity Order {
//       @required
//       id: String
//       items: List<OrderItem>
//   }

entityDef
    : 'entity' ID '{' fieldDef* '}'
    ;

fieldDef
    : traitApplication* ID ':' typeRef
    ;

// ─── Shape (Value Object) ─────────────────────────────────────────────────────
// 1.6  Lightweight value container — same field syntax as entity.
//
//   shape Money {
//       amount: Float
//       currency: String
//   }

shapeStructDef
    : 'shape' ID '{' fieldDef* '}'
    ;

// ─── Named List & Map ─────────────────────────────────────────────────────────
// 1.7  Named collection types for use in complex field declarations.
//
//   list OrderItemList { member: OrderItem }
//   map  MetadataMap   { key: String value: String }

listDef
    : 'list' ID '{' 'member' ':' typeRef '}'
    ;

mapDef
    : 'map' ID '{' 'key' ':' typeRef 'value' ':' typeRef '}'
    ;

// ─── Enum ─────────────────────────────────────────────────────────────────────
// 1.8  Closed set of named values with optional integer ordinals.
//
//   enum OrderStatus {
//       PENDING = 1
//       PAID    = 2
//   }

enumDef
    : 'enum' ID '{' enumMember+ '}'
    ;

enumMember
    : ID ('=' NUMBER)?
    ;

// ─── Actor ────────────────────────────────────────────────────────────────────
// 1.9  Standalone actor declaration (who or what interacts with the system).
//
//   @description("A registered user")
//   actor AuthenticatedUser

actorDef
    : 'actor' ID
    ;

// ─── Policy ───────────────────────────────────────────────────────────────────
// 1.10  Global business or regulatory constraint.
//
//   @compliance("GDPR")
//   policy DataRetention {
//       description: "Personal data must be purged after 7 years"
//   }

policyDef
    : 'policy' ID '{' 'description' ':' STRING '}'
    ;

// ─── Relationship ─────────────────────────────────────────────────────────
// 1.11  First-class relationship between entities.
//
//   @description("Order contains line items")
//   relationship OrderItems {
//       from: Order
//       to: OrderItem
//       cardinality: one_to_many
//       semantics: composition
//   }

relationshipDef
    : 'relationship' ID '{' relationshipBody '}'
    ;

relationshipBody
    : 'from' ':' ID
      'to' ':' ID
      'cardinality' ':' cardinalityValue
      ('semantics' ':' semanticsValue)?
      ('inverse' ':' ID)?
    ;

cardinalityValue
    : 'one_to_one'
    | 'one_to_many'
    | 'many_to_many'
    ;

semanticsValue
    : 'association'
    | 'aggregation'
    | 'composition'
    ;

// ─── Journey ──────────────────────────────────────────────────────────────────
// Central construct. All fields are optional at parse time; the validator
// enforces semantic requirements (CHR-001, CHR-002, CHR-003, etc.).

journeyDef
    : 'journey' ID '{' journeyBody '}'
    ;

journeyBody
    : actorDecl?
      preconditionsDecl?
      stepsDecl?
      variantsDecl?
      outcomesDecl?
    ;

// actor: Customer
actorDecl
    : 'actor' ':' ID
    ;

// 1.11  preconditions: ["Cart is not empty", "Actor is not logged in"]
preconditionsDecl
    : 'preconditions' ':' '[' STRING (',' STRING)* ']'
    ;

// steps: [ step Foo { ... }, step Bar { ... } ]
stepsDecl
    : 'steps' ':' '[' step (',' step)* ']'
    ;

// 1.13  Steps may be preceded by step-level trait applications (e.g. @slo).
step
    : traitApplication* 'step' ID '{' stepField* '}'
    ;

// 1.12  All step fields are optional at parse time; CHR-003 enforces action +
//       expectation at validation time.
stepField
    : 'action'      ':' STRING           // what the actor does
    | 'expectation' ':' STRING           // what the system must do
    | 'outcome'     ':' outcomeExpr      // state transition
    | 'telemetry'   ':' '[' ID (',' ID)* ']'  // events emitted
    | 'risk'        ':' STRING           // free-text risk annotation
    ;

// 1.12  State transition expressions used in step outcome and variant outcome.
outcomeExpr
    : 'TransitionTo'  '(' ID ')'
    | 'ReturnToStep'  '(' ID ')'
    ;

// ─── Variants ─────────────────────────────────────────────────────────────────
// 1.14  Named branches for alternative or error flows.
//
//   variants: {
//       PaymentDeclined: {
//           trigger: "Gateway returns declined status"
//           steps: [ step Notify { expectation: "..." } ]
//           outcome: ReturnToStep(ChoosePayment)
//       }
//   }

variantsDecl
    : 'variants' ':' '{' variantEntry (',' variantEntry)* '}'
    ;

variantEntry
    : ID ':' '{' variantBody '}'
    ;

variantBody
    : 'trigger' ':' STRING
      ('steps' ':' '[' step (',' step)* ']')?
      ('outcome' ':' outcomeExpr)?
    ;

// ─── Journey Outcomes ─────────────────────────────────────────────────────────
// 1.11  Terminal state descriptions. Either success or failure key may come first.
//
//   outcomes: {
//       success: "Order record exists with status PAID",
//       failure: "Cart remains intact with error message"
//   }

outcomesDecl
    : 'outcomes' ':' '{' outcomeEntry (',' outcomeEntry)* '}'
    ;

outcomeEntry
    : ('success' | 'failure') ':' STRING
    ;

// ─── Shared ───────────────────────────────────────────────────────────────────

// Dot-separated identifier path: com.example.checkout
// The '#' in use declarations is handled at the call site, not here.
qualifiedId
    : ID ('.' ID)*
    ;

// ─── Lexer Rules ──────────────────────────────────────────────────────────────
// Order matters for same-length token matches:
//   BOOL before ID so that `true`/`false` don't tokenize as identifiers.
//   DOC_COMMENT before COMMENT so that `///` matches the longer rule.
//   WS before ID (WS is skipped so ordering here doesn't affect parsing).

// 1.3 / 1.4  Boolean and numeric literals used in trait values and enum ordinals.
BOOL        : 'true' | 'false' ;
NUMBER      : [0-9]+ ('.' [0-9]+)? ;

// String literal: double-quoted, supports backslash escape sequences.
STRING      : '"' (~["\r\n\\] | '\\' .)* '"' ;

// 1.2  Doc comment — kept on the HIDDEN channel so the model builder can
//      associate documentation with the shape that follows it.
DOC_COMMENT : '///' ~[\r\n]* -> channel(HIDDEN) ;

// 1.2  Regular line comment — discarded entirely.
COMMENT     : '//' ~[\r\n]* -> skip ;

// Whitespace (including newlines) is insignificant in Chronos.
WS          : [ \t\r\n]+ -> skip ;

// Identifier: starts with a letter, followed by letters, digits, or underscores.
ID          : [a-zA-Z][a-zA-Z0-9_]* ;
