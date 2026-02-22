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
    | invariantDef
    | denyDef
    | errorDef
    | statemachineDef
    | roleDef
    | eventDef
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
    | 'input'
    | 'output'
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
    | 'statemachine'
    | 'field'
    | 'states'
    | 'initial'
    | 'terminal'
    | 'transitions'
    | 'guard'
    | 'role'
    | 'allow'
    | 'permission'
    | 'event'
    ;

traitArgList
    : '(' traitArg (',' traitArg)* ')'
    ;

// Named arg (key: value) takes priority via ordering; positional falls through.
// Key uses traitId so that contextual keywords (e.g. 'role') can appear as argument names.
traitArg
    : traitId ':' traitValue
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
    : 'entity' ID ('extends' ID)? '{' entityMember* '}'
    ;

entityMember
    : fieldDef
    | entityInvariant
    ;

fieldDef
    : traitApplication* ID ':' typeRef
    ;

entityInvariant
    : 'invariant' ID '{' invariantField+ '}'
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
    : 'actor' ID ('extends' ID)?
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
    : 'action'      ':' STRING                            // what the actor does
    | 'expectation' ':' STRING                            // what the system must do
    | 'outcome'     ':' outcomeExpr                       // state transition
    | 'telemetry'   ':' '[' ID (',' ID)* ']'              // events emitted
    | 'risk'        ':' STRING                            // free-text risk annotation
    | 'input'       ':' '[' dataField (',' dataField)* ']'  // typed inputs consumed
    | 'output'      ':' '[' dataField (',' dataField)* ']'  // typed outputs produced
    ;

// A named, typed data field used in step input/output blocks.
dataField
    : ID ':' typeRef
    ;

// 1.12  State transition expressions used in step outcome and variant outcome.
outcomeExpr
    : 'TransitionTo'  '(' ID ')'
    | 'ReturnToStep'  '(' ID ')'
    ;

// ─── Variants ─────────────────────────────────────────────────────────────────
// 1.14  Named branches for alternative or error flows.
// 2.3   Variant triggers must reference a defined error type (CHR-027).
//
//   variants: {
//       PaymentDeclined: {
//           trigger: PaymentDeclinedError
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
    : 'trigger' ':' ID
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

// ─── Invariants ───────────────────────────────────────────────────────────────
// 2.1  Global invariants and entity-scoped invariants.
//
// Global invariant:
//   invariant ActiveOrderLimit {
//       scope: [Customer, Order]
//       expression: "count(customer.orders, o => o.status == PENDING) <= 10"
//       severity: warning
//       message: "Customer should not exceed 10 pending orders"
//   }
//
// Entity-scoped invariant (inside entity block):
//   invariant TotalMatchesItems {
//       expression: "total.amount == sum(items, i => i.unitPrice.amount * i.quantity)"
//       severity: error
//   }

invariantDef
    : 'invariant' ID '{' invariantField+ '}'
    ;

invariantField
    : 'scope' ':' '[' ID (',' ID)* ']'
    | 'expression' ':' STRING
    | 'severity' ':' (ID | 'error')
    | 'message' ':' STRING
    ;

// ─── Deny (Negative Requirements) ────────────────────────────────────────────
// 2.2  deny blocks express prohibitions — things the system must never do.
//
// Example:
//   deny StorePlaintextPasswords {
//       description: "The system must never store passwords in plaintext"
//       scope: [UserCredential]
//       severity: critical
//   }

denyDef
    : 'deny' ID '{' denyField+ '}'
    ;

denyField
    : 'description' ':' STRING
    | 'scope' ':' '[' ID (',' ID)* ']'
    | 'severity' ':' ID
    ;

// ─── Error (Typed Error Construct) ───────────────────────────────────────────
// 2.3  error blocks define typed error conditions with codes, severity,
//      recoverability, and optional payloads.
//
// Example:
//   error PaymentDeclinedError {
//       code: "PAY-001"
//       severity: high
//       recoverable: true
//       message: "Payment was declined by the gateway"
//       payload: {
//           gatewayCode: String
//           retryable: Boolean
//       }
//   }

errorDef
    : 'error' ID '{' errorField+ '}'
    ;

errorField
    : 'code' ':' STRING
    | 'severity' ':' ID
    | 'recoverable' ':' BOOL
    | 'message' ':' STRING
    | 'payload' ':' '{' fieldDef* '}'
    ;

// ─── State Machine ────────────────────────────────────────────────────────────

// statemachine OrderLifecycle {
//     entity: Order
//     field: status
//     states: [PENDING, PAID, SHIPPED, DELIVERED, CANCELLED]
//     initial: PENDING
//     terminal: [DELIVERED, CANCELLED]
//     transitions: [
//         PENDING -> PAID {
//             guard: "payment.status == APPROVED"
//             action: "Emit OrderPaidEvent"
//         },
//         ...
//     ]
// }

statemachineDef
    : 'statemachine' ID '{' statemachineField* '}'
    ;

statemachineField
    : 'entity' ':' ID
    | 'field' ':' ID
    | 'states' ':' '[' ID (',' ID)* ']'
    | 'initial' ':' ID
    | 'terminal' ':' '[' ID (',' ID)* ']'
    | 'transitions' ':' '[' transition (',' transition)* ']'
    ;

transition
    : ID '->' ID ('{' transitionBody '}')?
    ;

transitionBody
    : transitionField*
    ;

transitionField
    : 'guard' ':' STRING
    | 'action' ':' STRING
    ;

// ─── Role ─────────────────────────────────────────────────────────────────────
// 3.1  role declarations define named permission sets.
//
//   role AdminRole {
//       allow: [create, read, update, delete]
//       deny:  [admin_delete]
//   }

roleDef
    : 'role' ID roleBody
    ;

roleBody
    : '{' roleBodyField* '}'
    ;

roleBodyField
    : 'allow' ':' '[' ID (',' ID)* ']'
    | 'deny'  ':' '[' ID (',' ID)* ']'
    ;

// ─── Event ────────────────────────────────────────────────────────────────────
// 4.2  Typed telemetry event declaration — flat fieldDef list (same as shape).
//
//   event CartReviewed {
//       cartId: String
//       itemCount: Integer
//   }
//
//   event OrderSubmitted {}   // zero-field signal event

eventDef
    : 'event' ID '{' fieldDef* '}'
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
