package com.genairus.chronos.mcp.knowledge;

import com.genairus.chronos.core.diagnostics.DiagnosticSeverity;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand-authored, build-enforced knowledge base for all 54 Chronos diagnostic codes.
 *
 * <p>Coverage is enforced by {@code DiagnosticKnowledgeCoverageTest}: the test fails
 * if {@link #REGISTRY} does not contain exactly the same set of codes as
 * {@link com.genairus.chronos.core.diagnostics.DiagnosticCodeRegistry#ALL_KNOWN_CODES}.
 *
 * <p>Priority entries (CHR-001, 002, 003, 008, 020, 025, 027, 034, 041) include
 * full bad/good code examples; all others include title, description, and fixes.
 */
public final class DiagnosticKnowledge {

    public record CodeExample(String label, String bad, String good) {}

    public record DiagnosticEntry(
            String code,
            DiagnosticSeverity severity,
            String title,
            String description,
            List<String> likelyCauses,
            List<String> fixes,
            List<CodeExample> examples
    ) {}

    public static final Map<String, DiagnosticEntry> REGISTRY;

    static {
        var map = new HashMap<String, DiagnosticEntry>();

        // ── High-frequency codes with full examples ───────────────────────────

        put(map, "CHR-001", DiagnosticSeverity.ERROR,
                "Journey must declare an actor",
                "Every journey must include an 'actor:' field naming the actor type responsible for performing the journey. " +
                "This establishes who initiates the flow and is required for authorization and PRD generation.",
                List.of(
                        "The 'actor:' field is missing from the journey body",
                        "The actor name was misspelled"
                ),
                List.of(
                        "Add 'actor: <ActorName>' inside the journey body",
                        "Declare the actor type with 'actor <Name>' at the top level if it does not exist"
                ),
                List.of(new CodeExample("Missing actor declaration",
                        """
                        actor Customer

                        journey PlaceOrder {
                            // actor: Customer  ← missing!
                            steps: [
                                step Submit {
                                    action: "Customer submits order"
                                    expectation: "Order is created"
                                }
                            ]
                            outcomes: { success: "Order placed" }
                        }
                        """,
                        """
                        actor Customer

                        journey PlaceOrder {
                            actor: Customer
                            steps: [
                                step Submit {
                                    action: "Customer submits order"
                                    expectation: "Order is created"
                                }
                            ]
                            outcomes: { success: "Order placed" }
                        }
                        """
                ))
        );

        put(map, "CHR-002", DiagnosticSeverity.ERROR,
                "Journey must declare an outcomes block",
                "Every journey must include an 'outcomes:' block with at least a 'success:' entry. " +
                "This documents the intended result of the journey and is required for PRD generation.",
                List.of(
                        "The 'outcomes:' block is missing from the journey body",
                        "The journey was stubbed out without outcomes"
                ),
                List.of(
                        "Add 'outcomes: { success: \"<description of the successful outcome>\" }' to the journey body",
                        "If the journey has multiple outcomes, list them all in the outcomes block"
                ),
                List.of(new CodeExample("Missing outcomes block",
                        """
                        actor Customer

                        journey BuyProduct {
                            actor: Customer
                            steps: [
                                step AddToCart {
                                    action: "Customer adds product"
                                    expectation: "Product in cart"
                                }
                            ]
                            // outcomes block missing!
                        }
                        """,
                        """
                        actor Customer

                        journey BuyProduct {
                            actor: Customer
                            steps: [
                                step AddToCart {
                                    action: "Customer adds product"
                                    expectation: "Product in cart"
                                }
                            ]
                            outcomes: { success: "Product added to cart successfully" }
                        }
                        """
                ))
        );

        put(map, "CHR-003", DiagnosticSeverity.ERROR,
                "Every step must declare action and expectation",
                "Each step in a journey must include both 'action:' (what the actor does) and " +
                "'expectation:' (the expected system response). Both fields are required.",
                List.of(
                        "The 'action:' field is missing from the step",
                        "The 'expectation:' field is missing from the step",
                        "Both fields are missing (empty step body)"
                ),
                List.of(
                        "Add 'action: \"<what the actor does>\"' to the step",
                        "Add 'expectation: \"<what the system does in response>\"' to the step"
                ),
                List.of(new CodeExample("Step missing action and expectation",
                        """
                        step ProcessPayment {
                            // missing action: and expectation:
                        }
                        """,
                        """
                        step ProcessPayment {
                            action: "Customer enters payment details and submits"
                            expectation: "Payment is processed and order status changes to CONFIRMED"
                        }
                        """
                ))
        );

        put(map, "CHR-008", DiagnosticSeverity.ERROR,
                "NamedTypeRef or symbol cannot be resolved",
                "A symbol reference (type name, actor reference, etc.) could not be resolved to a known declaration. " +
                "This typically means a type is used before it is declared, or it lives in another namespace and needs a 'use' import.",
                List.of(
                        "The type or symbol is defined in another namespace and needs a 'use' import",
                        "The name is misspelled",
                        "The declaration was accidentally deleted"
                ),
                List.of(
                        "Add 'use <namespace>#<TypeName>' at the top of the file to import from another namespace",
                        "Check the spelling of the type name",
                        "Declare the missing type in the same namespace"
                ),
                List.of(new CodeExample("Unresolved cross-namespace type",
                        """
                        // In namespace shop.orders
                        namespace shop.orders

                        // Trying to use Customer from shop.common without importing
                        journey PlaceOrder {
                            actor: Customer  // ← CHR-008: Customer not in scope
                            steps: [
                                step Submit {
                                    action: "submit"
                                    expectation: "created"
                                }
                            ]
                            outcomes: { success: "Order placed" }
                        }
                        """,
                        """
                        namespace shop.orders

                        use shop.common#Customer  // ← import Customer

                        journey PlaceOrder {
                            actor: Customer
                            steps: [
                                step Submit {
                                    action: "Customer submits order"
                                    expectation: "Order is created"
                                }
                            ]
                            outcomes: { success: "Order placed" }
                        }
                        """
                ))
        );

        put(map, "CHR-020", DiagnosticSeverity.ERROR,
                "Invariant severity must be error, warning, or info",
                "The 'severity:' field in an invariant block must be one of: error, warning, info. " +
                "Note this is DIFFERENT from deny/error severity which uses critical/high/medium/low.",
                List.of(
                        "Used a deny/error severity value (critical, high, medium, low) in an invariant",
                        "Misspelled the severity value"
                ),
                List.of(
                        "Change severity to one of: error, warning, info",
                        "Remember: invariant uses error/warning/info; deny/error uses critical/high/medium/low"
                ),
                List.of(new CodeExample("Wrong severity in invariant",
                        """
                        entity Order {
                            total: Float
                            invariant positiveTotal {
                                expression: "total > 0"
                                severity: critical  // ← wrong! critical is for deny/error
                            }
                        }
                        """,
                        """
                        entity Order {
                            total: Float
                            invariant positiveTotal {
                                expression: "total > 0"
                                severity: error  // ← correct for invariant
                            }
                        }
                        """
                ))
        );

        put(map, "CHR-025", DiagnosticSeverity.ERROR,
                "Deny severity must be critical, high, medium, or low",
                "The 'severity:' field in a deny block must be one of: critical, high, medium, low. " +
                "Note this is DIFFERENT from invariant severity which uses error/warning/info.",
                List.of(
                        "Used an invariant severity value (error, warning, info) in a deny block",
                        "Misspelled the severity value"
                ),
                List.of(
                        "Change severity to one of: critical, high, medium, low",
                        "Remember: deny uses critical/high/medium/low; invariant uses error/warning/info"
                ),
                List.of(new CodeExample("Wrong severity in deny",
                        """
                        deny BlockGuestCheckout {
                            description: "Guests cannot check out"
                            scope: [Order]
                            severity: error  // ← wrong! error is for invariant
                        }
                        """,
                        """
                        deny BlockGuestCheckout {
                            description: "Guests cannot check out"
                            scope: [Order]
                            severity: high  // ← correct for deny
                        }
                        """
                ))
        );

        put(map, "CHR-027", DiagnosticSeverity.ERROR,
                "Variant trigger must reference a defined error type",
                "A journey variant's 'trigger:' field must reference an error type declared in the same namespace " +
                "or imported via a 'use' statement. The trigger identifies which error condition activates the variant.",
                List.of(
                        "The error type named in trigger: is not declared",
                        "The error type is in another namespace and needs a 'use' import",
                        "Misspelling in the error type name"
                ),
                List.of(
                        "Declare the error type: 'error <TypeName> { code: \"E001\" severity: high }'",
                        "Or import it from another namespace with 'use <namespace>#<ErrorType>'"
                ),
                List.of(new CodeExample("Undefined error type in variant trigger",
                        """
                        journey CheckoutFlow {
                            actor: Customer
                            steps: [
                                step Pay {
                                    action: "Customer pays"
                                    expectation: "Payment processed"
                                }
                            ]
                            outcomes: { success: "Order placed" }
                            variants: {
                                PaymentFailed: {
                                    trigger: PaymentError  // ← CHR-027: PaymentError not declared
                                }
                            }
                        }
                        """,
                        """
                        error PaymentError {
                            code: "PAY-001"
                            severity: high
                        }

                        journey CheckoutFlow {
                            actor: Customer
                            steps: [
                                step Pay {
                                    action: "Customer pays"
                                    expectation: "Payment processed"
                                }
                            ]
                            outcomes: { success: "Order placed" }
                            variants: {
                                PaymentFailed: {
                                    trigger: PaymentError  // ← now valid
                                }
                            }
                        }
                        """
                ))
        );

        put(map, "CHR-034", DiagnosticSeverity.ERROR,
                "TransitionTo() must reference a declared statemachine state",
                "A TransitionTo() outcome expression must name a state that is declared in a statemachine. " +
                "The target state must exist in the 'states:' list of the statemachine bound to the relevant entity field.",
                List.of(
                        "The state name is misspelled",
                        "The state was removed from the statemachine but is still referenced in a journey",
                        "The state belongs to a different entity's statemachine"
                ),
                List.of(
                        "Check the statemachine's 'states:' list and use an exact match",
                        "Add the state to the statemachine if it is genuinely needed",
                        "Use chronos.describe_shape with 'statemachine' to see the required fields"
                ),
                List.of(new CodeExample("TransitionTo references undeclared state",
                        """
                        statemachine OrderLifecycle {
                            entity: Order
                            field: status
                            initial: PENDING
                            states: [PENDING, CONFIRMED, CANCELLED]
                            terminal: [CANCELLED]
                            transitions: [
                                PENDING -> CONFIRMED,
                                PENDING -> CANCELLED
                            ]
                        }

                        // In a journey step outcome:
                        // TransitionTo(SHIPPED)  ← CHR-034: SHIPPED not in states list
                        """,
                        """
                        statemachine OrderLifecycle {
                            entity: Order
                            field: status
                            initial: PENDING
                            states: [PENDING, CONFIRMED, SHIPPED, CANCELLED]
                            terminal: [CANCELLED]
                            transitions: [
                                PENDING -> CONFIRMED,
                                CONFIRMED -> SHIPPED,
                                PENDING -> CANCELLED
                            ]
                        }

                        // Now TransitionTo(SHIPPED) is valid
                        """
                ))
        );

        put(map, "CHR-041", DiagnosticSeverity.ERROR,
                "Step telemetry event must be a declared or imported event type",
                "A step's 'telemetry:' field lists event type names that must be declared as 'event' shapes " +
                "in the same namespace or imported via 'use'. Each event name must resolve to a known event type.",
                List.of(
                        "The event type is not declared",
                        "The event type is in another namespace and needs a 'use' import",
                        "The event name is misspelled"
                ),
                List.of(
                        "Declare the event type: 'event <Name> { <fields> }'",
                        "Or import it from another namespace with 'use <namespace>#<EventType>'"
                ),
                List.of(new CodeExample("Undeclared telemetry event",
                        """
                        journey Checkout {
                            actor: Customer
                            steps: [
                                step Pay {
                                    action: "Customer submits payment"
                                    expectation: "Payment processed"
                                    telemetry: [PaymentProcessed]  // ← CHR-041: not declared
                                }
                            ]
                            outcomes: { success: "Order placed" }
                        }
                        """,
                        """
                        event PaymentProcessed {
                            orderId: String
                            amount: Float
                        }

                        journey Checkout {
                            actor: Customer
                            steps: [
                                step Pay {
                                    action: "Customer submits payment"
                                    expectation: "Payment processed"
                                    telemetry: [PaymentProcessed]  // ← now valid
                                }
                            ]
                            outcomes: { success: "Order placed" }
                        }
                        """
                ))
        );

        // ── Remaining codes — title, description, causes, fixes ───────────────

        put(map, "CHR-004", DiagnosticSeverity.WARNING,
                "Journey declares zero happy-path steps",
                "A journey with an empty steps list provides no implementation guidance. Consider adding at least one step.",
                List.of("The steps: [] array is empty", "Steps were accidentally removed"),
                List.of("Add at least one step with action: and expectation:"), List.of());

        put(map, "CHR-005", DiagnosticSeverity.ERROR,
                "Duplicate shape name in namespace",
                "Two or more shapes in the same namespace share the same name. Shape names must be unique within a namespace.",
                List.of("The same name was used for two different shapes", "A shape was accidentally declared twice",
                        "A cross-file duplicate exists when using multi-file compilation"),
                List.of("Rename one of the duplicate shapes", "Remove the accidental duplicate"), List.of());

        put(map, "CHR-006", DiagnosticSeverity.WARNING,
                "Entity or shape declares no fields",
                "An entity or shape with no fields provides no structural information. Consider adding at least one field.",
                List.of("Fields were not yet added to a stub declaration", "All fields were accidentally removed"),
                List.of("Add at least one field: 'fieldName: TypeName'"), List.of());

        put(map, "CHR-007", DiagnosticSeverity.WARNING,
                "Actor missing @description trait",
                "Actors should carry a @description trait to document their role in the system.",
                List.of("The @description trait was not added when creating the actor"),
                List.of("Add '@description(\"<actor role description>\")' above the actor declaration"), List.of());

        put(map, "CHR-009", DiagnosticSeverity.WARNING,
                "Journey missing @kpi trait",
                "Journeys should carry a @kpi trait to document their success metric.",
                List.of("The @kpi trait was not added"),
                List.of("Add '@kpi(\"<KPI description>\")' above the journey declaration"), List.of());

        put(map, "CHR-010", DiagnosticSeverity.WARNING,
                "Journey missing @compliance trait",
                "Journeys should carry a @compliance trait if they touch regulated data or processes.",
                List.of("The @compliance trait was not evaluated for this journey"),
                List.of("Add '@compliance(\"<standard>\")' if applicable, or note N/A in documentation"), List.of());

        put(map, "CHR-011", DiagnosticSeverity.ERROR,
                "Relationship targets must reference defined entities",
                "Both the source and target of a relationship must be declared entity types.",
                List.of("An entity name is misspelled in the relationship", "The entity is not yet declared"),
                List.of("Declare the missing entity", "Import it with 'use <namespace>#<Entity>'"), List.of());

        put(map, "CHR-012", DiagnosticSeverity.ERROR,
                "Unresolved symbol reference after all resolution phases",
                "A symbol reference (e.g. an actor reference, type reference, or parent ref) was not resolved " +
                "after all compiler phases completed. This is a catch-all for references that slipped through earlier phases.",
                List.of("The referenced symbol is in another file not included in the compilation",
                        "The symbol was deleted", "An import is missing"),
                List.of("Include all required .chronos files in the compilation",
                        "Add the missing 'use' import", "Declare the missing symbol"), List.of());

        put(map, "CHR-013", DiagnosticSeverity.ERROR,
                "Type name cannot be resolved during type resolution",
                "A field type name could not be resolved to a known shape (entity, struct, enum, list, or map).",
                List.of("The type is not declared in the namespace",
                        "The type is in another namespace and needs a 'use' import",
                        "The type name is misspelled"),
                List.of("Declare the type in the same namespace",
                        "Add 'use <namespace>#<TypeName>' to import from another namespace"), List.of());

        put(map, "CHR-014", DiagnosticSeverity.ERROR,
                "Inverse field name must exist on target entity",
                "A relationship's 'inverse:' field must name a field that actually exists on the target entity.",
                List.of("The inverse field name is misspelled", "The field was renamed on the target entity",
                        "The inverse field was not yet added to the target entity"),
                List.of("Check the target entity's fields and use an exact field name match",
                        "Add the inverse field to the target entity if missing"), List.of());

        put(map, "CHR-015", DiagnosticSeverity.ERROR,
                "Circular inheritance chain detected",
                "Entity or actor inheritance forms a cycle (e.g. A extends B extends A). Inheritance must be acyclic.",
                List.of("Two entities accidentally extend each other", "An inheritance chain loops back"),
                List.of("Remove one of the circular extends references",
                        "Restructure the hierarchy to be a tree"), List.of());

        put(map, "CHR-016", DiagnosticSeverity.ERROR,
                "Unknown import target",
                "A 'use' statement references a name that does not exist in the target namespace.",
                List.of("The imported name is misspelled", "The shape was renamed or deleted in the target namespace"),
                List.of("Check the exact shape name in the target namespace using 'chronos.list_symbols'",
                        "Correct the spelling in the 'use' statement"), List.of());

        put(map, "CHR-017", DiagnosticSeverity.ERROR,
                "Ambiguous import: same simple name resolves to different targets",
                "Two 'use' imports bring in shapes with the same simple name from different namespaces.",
                List.of("Two namespaces each define a type with the same name and both are imported"),
                List.of("Use the fully-qualified reference (<namespace>#<Name>) instead of the simple name",
                        "Rename one of the conflicting types"), List.of());

        put(map, "CHR-018", DiagnosticSeverity.ERROR,
                "Multiple inheritance is not supported",
                "An entity or actor declares more than one 'extends' parent. Only single inheritance is allowed.",
                List.of("The 'extends' clause lists more than one parent"),
                List.of("Keep only one parent in the 'extends' clause",
                        "Use composition (shape fields) for additional behavior"), List.of());

        put(map, "CHR-019", DiagnosticSeverity.ERROR,
                "Invariant expression references undeclared field",
                "A field name used in an invariant expression does not exist as a direct field on the entity. " +
                "Only direct fields are valid — dot-path navigation (e.g. 'address.zip') is not supported.",
                List.of("The field name is misspelled", "The field was renamed or removed",
                        "A dot-path navigation was used (not supported in entity invariants)"),
                List.of("Use only direct field names of the entity",
                        "For nested access, consider adding a derived field to the entity"), List.of());

        put(map, "CHR-021", DiagnosticSeverity.ERROR,
                "Global invariant must declare a non-empty scope",
                "A top-level invariant must have a 'scope:' list with at least one entity reference.",
                List.of("The 'scope:' list is empty or missing", "Scope entities are in another namespace (not supported)"),
                List.of("Add entities to 'scope: [Entity1, Entity2]'",
                        "Scope entities must be in the same namespace"), List.of());

        put(map, "CHR-022", DiagnosticSeverity.ERROR,
                "Invariant names must be unique within scope",
                "Two invariants in the same entity or top-level scope share the same name.",
                List.of("The same invariant name was used twice in the same entity"),
                List.of("Rename one of the duplicate invariants"), List.of());

        put(map, "CHR-023", DiagnosticSeverity.ERROR,
                "Every deny must include a description",
                "Deny declarations require a 'description:' field explaining what is being denied and why.",
                List.of("The 'description:' field was omitted"),
                List.of("Add 'description: \"<explanation of what is denied and why>\"' to the deny block"), List.of());

        put(map, "CHR-024", DiagnosticSeverity.ERROR,
                "Deny scope entities must be defined or imported",
                "All entities listed in a deny's 'scope:' must be declared in the same namespace or imported.",
                List.of("An entity in 'scope:' is not declared", "An entity was renamed"),
                List.of("Declare the entity or import it with 'use <namespace>#<Entity>'"), List.of());

        put(map, "CHR-026", DiagnosticSeverity.ERROR,
                "Error codes must be unique across namespace",
                "Two error declarations in the same namespace share the same 'code:' value.",
                List.of("The same error code string was reused in two error declarations"),
                List.of("Assign a unique code to each error type"), List.of());

        put(map, "CHR-028", DiagnosticSeverity.ERROR,
                "Error severity must be critical, high, medium, or low",
                "The 'severity:' field in an error declaration must be one of: critical, high, medium, low.",
                List.of("Used an invariant severity (error, warning, info) in an error declaration",
                        "Misspelled the severity value"),
                List.of("Change to one of: critical, high, medium, low"), List.of());

        put(map, "CHR-029", DiagnosticSeverity.ERROR,
                "All transition states must be declared in the states list",
                "Every state name used in a 'transitions:' entry (from: or to:) must appear in the 'states:' list.",
                List.of("A state was added to transitions but forgotten in 'states:'",
                        "A state name was misspelled in transitions"),
                List.of("Add the missing state to 'states: [...]'", "Fix the spelling in the transitions entry"), List.of());

        put(map, "CHR-030", DiagnosticSeverity.ERROR,
                "Every non-terminal state must have an outbound transition",
                "States not listed in 'terminal:' must have at least one outbound transition from them.",
                List.of("A non-terminal state has no 'from: STATE to: ...' transitions defined",
                        "A state was added to 'states:' but no transitions were added"),
                List.of("Add at least one transition 'from: <STATE> to: <NEXT>'",
                        "Or add the state to 'terminal:' if it is an end state"), List.of());

        put(map, "CHR-031", DiagnosticSeverity.ERROR,
                "The initial state must be in the states list",
                "The 'initial:' state must appear in the 'states:' list.",
                List.of("The initial state name was misspelled", "The initial state was removed from 'states:'"),
                List.of("Add the initial state to 'states: [...]' or correct the spelling"), List.of());

        put(map, "CHR-032", DiagnosticSeverity.ERROR,
                "Terminal states must not have outbound transitions",
                "States listed in 'terminal:' must not have any 'from: STATE to: ...' transitions.",
                List.of("A state was added to 'terminal:' but a transition from it was not removed",
                        "A transition was added with a terminal state as the source"),
                List.of("Remove the transition from the terminal state", "Or remove the state from 'terminal:'"), List.of());

        put(map, "CHR-033", DiagnosticSeverity.ERROR,
                "Statemachine entity and field must be defined",
                "The 'entity:' and 'field:' referenced by a statemachine must both exist: the entity must be declared, " +
                "and the field must be a field on that entity.",
                List.of("The entity name is misspelled or not declared",
                        "The field name does not exist on the entity",
                        "The entity is in another namespace and needs a 'use' import"),
                List.of("Declare the entity with the correct field", "Import the entity if it is in another namespace"), List.of());

        put(map, "CHR-035", DiagnosticSeverity.ERROR,
                "Output field names must be unique across all steps in the journey scope",
                "Two steps in the same journey (or variant) declare output fields with the same name.",
                List.of("Two steps produce an output field with the same name",
                        "A step was copy-pasted and the output field names were not updated"),
                List.of("Rename one of the duplicate output fields",
                        "Or combine the steps if they produce the same data"), List.of());

        put(map, "CHR-036", DiagnosticSeverity.ERROR,
                "Step input field must be produced by a preceding step's output",
                "A step's input field references data that is not produced by any earlier step in the journey.",
                List.of("The input field name doesn't match any preceding step's output field name",
                        "The step order was changed so a dependency now comes after its consumer"),
                List.of("Ensure the producing step comes before the consuming step in the steps array",
                        "Check that the input field name exactly matches the output field name of the producing step"), List.of());

        put(map, "CHR-037", DiagnosticSeverity.ERROR,
                "@authorize role name must reference a declared role",
                "The role name in an @authorize trait must be declared as a 'role' shape.",
                List.of("The role was not declared", "The role name is misspelled"),
                List.of("Declare the role: 'role <Name> { allow: [...] }'"), List.of());

        put(map, "CHR-038", DiagnosticSeverity.ERROR,
                "@authorize permission must be listed in the role's allow list",
                "The permission in @authorize must appear in the role's 'allow:' list.",
                List.of("The permission was not added to the role's allow list", "The permission name is misspelled"),
                List.of("Add the permission to the role's 'allow: [...]' list"), List.of());

        put(map, "CHR-039", DiagnosticSeverity.ERROR,
                "Journey actor must carry @authorize(role: X) matching the journey's required role",
                "If a journey declares a required role, the actor type must carry an @authorize trait with that role.",
                List.of("The actor is missing @authorize for the role required by the journey",
                        "The role name in the actor's @authorize doesn't match the journey's required role"),
                List.of("Add '@authorize(role: <RoleName>)' to the actor declaration"), List.of());

        put(map, "CHR-040", DiagnosticSeverity.ERROR,
                "@authorize permission must not be in the role's deny list",
                "A permission cannot appear in both the role's 'allow:' and 'deny:' lists.",
                List.of("A permission was accidentally added to both allow and deny"),
                List.of("Remove the permission from the deny list, or from the allow list"), List.of());

        put(map, "CHR-042", DiagnosticSeverity.ERROR,
                "Invariant expression failed to parse",
                "The expression string in an invariant could not be parsed by the expression parser.",
                List.of("Syntax error in the expression (unmatched parentheses, unknown operator)",
                        "The expression uses unsupported syntax"),
                List.of("Check the expression syntax: field comparisons (>, <, ==, !=), logical operators (&&, ||, !)",
                        "Use 'chronos.describe_shape' with 'invariant' for expression syntax guidance"), List.of());

        put(map, "CHR-043", DiagnosticSeverity.WARNING,
                "Type mismatch in invariant expression",
                "The invariant expression compares values of incompatible types (e.g. comparing a String field to an Integer).",
                List.of("A numeric field is compared to a string literal",
                        "A boolean field is used in a numeric comparison"),
                List.of("Ensure the comparison operand matches the field type"), List.of());

        put(map, "CHR-044", DiagnosticSeverity.ERROR,
                "Statemachine state is not a member of the bound enum",
                "Every state in the 'states:' list must correspond to a member of the enum bound via 'field:'.",
                List.of("A state was added to the statemachine that is not in the enum",
                        "The enum was updated but the statemachine was not"),
                List.of("Add the missing value to the enum", "Or remove the state from the statemachine"), List.of());

        put(map, "CHR-045", DiagnosticSeverity.WARNING,
                "Bound enum member not covered by any statemachine state",
                "An enum member exists that is not represented in any statemachine state. This may be intentional (subset coverage is valid).",
                List.of("A new enum value was added but the statemachine was not updated",
                        "An enum value is unused in the state machine by design"),
                List.of("Add the enum value to the statemachine's 'states:' list if it should be reachable",
                        "Suppress by documenting that the omission is intentional"), List.of());

        put(map, "CHR-046", DiagnosticSeverity.ERROR,
                "TransitionTo() target state is ambiguous — declared in multiple statemachines",
                "The state name in TransitionTo() appears in more than one statemachine, making it ambiguous which machine should transition.",
                List.of("The same state name exists in multiple statemachines on different entities",
                        "The journey uses entities with statemachines that share state names"),
                List.of("Rename states in one of the statemachines to be unique",
                        "Use fully-qualified state references if supported"), List.of());

        put(map, "CHR-047", DiagnosticSeverity.WARNING,
                "TransitionTo() target state has no declared incoming transitions",
                "The state named in TransitionTo() is valid but no statemachine transition leads to it, " +
                "suggesting the state machine may be incomplete.",
                List.of("The statemachine is missing a transition to the target state",
                        "The state is declared but no 'from: X to: TARGET' transition exists"),
                List.of("Add 'from: <CURRENT_STATE> to: <TARGET_STATE>' to the statemachine's transitions"), List.of());

        put(map, "CHR-048", DiagnosticSeverity.ERROR,
                "Composition target cannot be referenced by multiple composing entities",
                "A shape used as a composition target can only be composed by one entity.",
                List.of("Two entities both declare a field of the same composed shape type"),
                List.of("Extract the shared shape into a base entity using inheritance (extends)",
                        "Or create separate shape types for each entity"), List.of());

        put(map, "CHR-049", DiagnosticSeverity.ERROR,
                "Child entity redefines parent field with incompatible type",
                "A child entity declares a field with the same name as a parent field but a different type.",
                List.of("A field was overridden with a different type in the child",
                        "A field in the child accidentally has the same name as a parent field"),
                List.of("Rename the child's field to avoid the conflict",
                        "Or remove the field from the child if the parent's type is correct"), List.of());

        put(map, "CHR-050", DiagnosticSeverity.ERROR,
                "@timeout/@ttl duration argument is not a valid duration literal",
                "Duration values must follow the format: number + unit, e.g. '30s', '5m', '2h', '1d'.",
                List.of("The duration uses an unsupported unit or format", "Missing the unit suffix"),
                List.of("Use format: '<number><unit>' where unit is s (seconds), m (minutes), h (hours), d (days)"), List.of());

        put(map, "CHR-051", DiagnosticSeverity.ERROR,
                "@timeout onExpiry references an undeclared variant",
                "The variant name in @timeout's 'onExpiry:' must match a variant declared in the journey.",
                List.of("The variant name is misspelled", "The variant was not yet declared"),
                List.of("Check the journey's variant declarations and use the exact name",
                        "Declare the variant if it doesn't exist yet"), List.of());

        put(map, "CHR-052", DiagnosticSeverity.ERROR,
                "@ttl action must be one of: delete, archive, notify",
                "The 'action:' in a @ttl trait must be one of the three supported actions.",
                List.of("An unsupported action was specified", "Misspelled action name"),
                List.of("Change to one of: delete, archive, notify"), List.of());

        put(map, "CHR-053", DiagnosticSeverity.ERROR,
                "@schedule cron is not a valid 5-field cron expression",
                "A @schedule trait requires a standard 5-field cron expression: minute hour day-of-month month day-of-week.",
                List.of("The cron string has wrong number of fields", "Invalid cron field values"),
                List.of("Use a 5-field cron expression, e.g. '0 9 * * 1' for every Monday at 9am"), List.of());

        put(map, "CHR-W001", DiagnosticSeverity.WARNING,
                "Invariant references optional field without null guard",
                "An invariant expression references a field that may be null/absent, but no null check is included. " +
                "This could cause runtime errors if the field is absent.",
                List.of("The field is declared as an optional type but the expression doesn't handle null",
                        "The expression uses the field directly without a null guard"),
                List.of("Wrap the field access with a null guard: '!= null && <condition>'",
                        "Or declare the field as non-optional if it is always present"), List.of());

        REGISTRY = Collections.unmodifiableMap(map);
    }

    private static void put(Map<String, DiagnosticEntry> map, String code,
                             DiagnosticSeverity severity, String title, String description,
                             List<String> causes, List<String> fixes, List<CodeExample> examples) {
        map.put(code, new DiagnosticEntry(code, severity, title, description, causes, fixes, examples));
    }

    private DiagnosticKnowledge() {}
}
