package com.genairus.chronos.parser;

import com.genairus.chronos.core.refs.QualifiedName;
import com.genairus.chronos.syntax.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ChronosParserFacade}: structural assertions on the lowered
 * {@link SyntaxModel}. No semantic checks — only structure and span verification.
 */
class ChronosParserFacadeTest {

    private static final ChronosParserFacade FACADE = new ChronosParserFacade();

    private static SyntaxModel parse(String source) {
        return FACADE.parse(source, "<test>");
    }

    // ── namespace ─────────────────────────────────────────────────────────────

    @Test
    void namespaceIsParsed() {
        var model = parse("namespace com.example.checkout");
        assertEquals("com.example.checkout", model.namespace());
        assertTrue(model.imports().isEmpty());
        assertTrue(model.declarations().isEmpty());
    }

    // ── use declarations ──────────────────────────────────────────────────────

    @Test
    void useDeclarationIsParsed() {
        var model = parse("""
                namespace com.example
                use com.example.actors#Customer
                """);
        assertEquals(1, model.imports().size());
        var use = model.imports().get(0);
        assertEquals(QualifiedName.qualified("com.example.actors", "Customer"), use.name());
        assertFalse(use.span().isUnknown());
        assertEquals("<test>", use.span().sourceName());
    }

    // ── entity ────────────────────────────────────────────────────────────────

    @Test
    void entityDeclarationStructure() {
        var model = parse("""
                namespace com.example
                @pii
                entity Order {
                    id: String
                    total: Float
                }
                """);
        assertEquals(1, model.declarations().size());
        var entity = assertInstanceOf(SyntaxEntityDecl.class, model.declarations().get(0));
        assertEquals("Order", entity.name());
        assertNull(entity.parentOrNull());
        assertEquals(1, entity.traits().size());
        assertEquals("pii", entity.traits().get(0).name());
        assertEquals(2, entity.fields().size());

        var idField = entity.fields().get(0);
        assertEquals("id", idField.name());
        var prim = assertInstanceOf(SyntaxTypeRef.Primitive.class, idField.type());
        assertEquals(SyntaxTypeRef.PrimitiveKind.STRING, prim.kind());
    }

    @Test
    void entityWithInheritanceParsed() {
        var model = parse("""
                namespace com.example
                entity SpecialOrder extends Order {
                    priority: Integer
                }
                """);
        var entity = assertInstanceOf(SyntaxEntityDecl.class, model.declarations().get(0));
        assertEquals("SpecialOrder", entity.name());
        assertEquals("Order", entity.parentOrNull());
    }

    // ── span capture ──────────────────────────────────────────────────────────

    @Test
    void spansAreCaptured() {
        var model = parse("""
                namespace com.example
                entity Order {
                    id: String
                }
                """);
        var entity = (SyntaxEntityDecl) model.declarations().get(0);
        assertFalse(entity.span().isUnknown());
        assertEquals("<test>", entity.span().sourceName());
        // entity keyword is on line 2
        assertEquals(2, entity.span().startLine());
        // "entity" starts at column 1
        assertEquals(1, entity.span().startCol());
    }

    // ── all primitive type refs ───────────────────────────────────────────────

    @Test
    void allPrimitiveTypeRefsAreLowered() {
        var model = parse("""
                namespace com.example
                entity AllTypes {
                    a: String
                    b: Integer
                    c: Long
                    d: Float
                    e: Boolean
                    f: Timestamp
                    g: Blob
                    h: Document
                }
                """);
        var entity = (SyntaxEntityDecl) model.declarations().get(0);
        assertEquals(8, entity.fields().size());
        var kinds = entity.fields().stream()
                .map(f -> ((SyntaxTypeRef.Primitive) f.type()).kind())
                .toList();
        assertEquals(List.of(
                SyntaxTypeRef.PrimitiveKind.STRING,
                SyntaxTypeRef.PrimitiveKind.INTEGER,
                SyntaxTypeRef.PrimitiveKind.LONG,
                SyntaxTypeRef.PrimitiveKind.FLOAT,
                SyntaxTypeRef.PrimitiveKind.BOOLEAN,
                SyntaxTypeRef.PrimitiveKind.TIMESTAMP,
                SyntaxTypeRef.PrimitiveKind.BLOB,
                SyntaxTypeRef.PrimitiveKind.DOCUMENT
        ), kinds);
    }

    @Test
    void genericTypeRefsAreLowered() {
        var model = parse("""
                namespace com.example
                entity Cart {
                    items: List<OrderItem>
                    meta: Map<String, String>
                }
                """);
        var entity = (SyntaxEntityDecl) model.declarations().get(0);

        var listType = assertInstanceOf(SyntaxTypeRef.ListType.class, entity.fields().get(0).type());
        var named = assertInstanceOf(SyntaxTypeRef.Named.class, listType.element());
        assertEquals("OrderItem", named.name());

        var mapType = assertInstanceOf(SyntaxTypeRef.MapType.class, entity.fields().get(1).type());
        assertInstanceOf(SyntaxTypeRef.Primitive.class, mapType.key());
        assertInstanceOf(SyntaxTypeRef.Primitive.class, mapType.value());
    }

    // ── journey ───────────────────────────────────────────────────────────────

    @Test
    void journeyDeclarationStructure() {
        var model = parse("""
                namespace com.example
                journey Checkout {
                    actor: Customer
                    preconditions: ["Cart not empty"]
                    steps: [
                        step Pay {
                            action: "Click pay"
                            expectation: "Order placed"
                            outcome: TransitionTo(Confirmed)
                            telemetry: [order_placed]
                            risk: "Gateway may be down"
                        }
                    ]
                    outcomes: {
                        success: "Order confirmed",
                        failure: "Cart intact"
                    }
                }
                """);
        var journey = assertInstanceOf(SyntaxJourneyDecl.class, model.declarations().get(0));
        assertEquals("Checkout", journey.name());
        assertEquals("Customer", journey.actorOrNull());
        assertEquals(List.of("Cart not empty"), journey.preconditions());
        assertNotNull(journey.outcomesOrNull());
        assertEquals("Order confirmed", journey.outcomesOrNull().successOrNull());
        assertEquals("Cart intact", journey.outcomesOrNull().failureOrNull());

        assertEquals(1, journey.steps().size());
        var step = journey.steps().get(0);
        assertEquals("Pay", step.name());
        assertEquals(5, step.fields().size());
        assertInstanceOf(SyntaxStepField.Action.class,      step.fields().get(0));
        assertInstanceOf(SyntaxStepField.Expectation.class, step.fields().get(1));
        assertInstanceOf(SyntaxStepField.Outcome.class,     step.fields().get(2));
        assertInstanceOf(SyntaxStepField.Telemetry.class,   step.fields().get(3));
        assertInstanceOf(SyntaxStepField.Risk.class,        step.fields().get(4));

        var outcome = (SyntaxStepField.Outcome) step.fields().get(2);
        var transitionTo = assertInstanceOf(SyntaxOutcomeExpr.TransitionTo.class, outcome.expr());
        assertEquals("Confirmed", transitionTo.stateId());
    }

    @Test
    void journeyVariantsAreLowered() {
        var model = parse("""
                namespace com.example
                journey Pay {
                    variants: {
                        Declined: {
                            trigger: PaymentError
                            steps: [
                                step Notify { expectation: "Show error" }
                            ]
                            outcome: ReturnToStep(EnterCard)
                        }
                    }
                }
                """);
        var journey = assertInstanceOf(SyntaxJourneyDecl.class, model.declarations().get(0));
        assertEquals(1, journey.variants().size());
        var variant = journey.variants().get(0);
        assertEquals("Declined", variant.name());
        assertEquals("PaymentError", variant.triggerName());
        assertEquals(1, variant.steps().size());
        var returnTo = assertInstanceOf(SyntaxOutcomeExpr.ReturnToStep.class, variant.outcomeOrNull());
        assertEquals("EnterCard", returnTo.stepId());
    }

    @Test
    void minimalJourneyHasNullsForAbsentFields() {
        var model = parse("""
                namespace com.example
                journey Empty {}
                """);
        var journey = assertInstanceOf(SyntaxJourneyDecl.class, model.declarations().get(0));
        assertNull(journey.actorOrNull());
        assertTrue(journey.preconditions().isEmpty());
        assertTrue(journey.steps().isEmpty());
        assertTrue(journey.variants().isEmpty());
        assertNull(journey.outcomesOrNull());
    }

    @Test
    void stepInputOutputFieldsAreLowered() {
        var model = parse("""
                namespace com.example
                journey Checkout {
                    steps: [
                        step CollectPayment {
                            action: "User submits payment"
                            expectation: "Payment is processed"
                            output: [amount: Float, currency: String]
                        },
                        step SendConfirmation {
                            action: "System sends receipt"
                            expectation: "Email delivered"
                            input: [amount: Float, currency: String]
                        }
                    ]
                }
                """);
        var journey = assertInstanceOf(SyntaxJourneyDecl.class, model.declarations().get(0));
        assertEquals(2, journey.steps().size());

        // Step 1: CollectPayment has output block
        var step1 = journey.steps().get(0);
        assertEquals("CollectPayment", step1.name());
        var outputField = assertInstanceOf(SyntaxStepField.Output.class,
                step1.fields().stream()
                     .filter(f -> f instanceof SyntaxStepField.Output)
                     .findFirst().orElseThrow());
        assertEquals(2, outputField.fields().size());
        assertEquals("amount", outputField.fields().get(0).name());
        assertInstanceOf(SyntaxTypeRef.Primitive.class, outputField.fields().get(0).type());
        assertEquals("currency", outputField.fields().get(1).name());
        assertFalse(outputField.span().isUnknown());

        // Step 2: SendConfirmation has input block
        var step2 = journey.steps().get(1);
        assertEquals("SendConfirmation", step2.name());
        var inputField = assertInstanceOf(SyntaxStepField.Input.class,
                step2.fields().stream()
                     .filter(f -> f instanceof SyntaxStepField.Input)
                     .findFirst().orElseThrow());
        assertEquals(2, inputField.fields().size());
        assertEquals("amount",   inputField.fields().get(0).name());
        assertEquals("currency", inputField.fields().get(1).name());
        assertFalse(inputField.span().isUnknown());
    }

    // ── other shape types ─────────────────────────────────────────────────────

    @Test
    void enumDeclarationStructure() {
        var model = parse("""
                namespace com.example
                enum Status {
                    PENDING = 1
                    PAID    = 2
                    FAILED
                }
                """);
        var enumDecl = assertInstanceOf(SyntaxEnumDecl.class, model.declarations().get(0));
        assertEquals("Status", enumDecl.name());
        assertEquals(3, enumDecl.members().size());
        assertEquals(Integer.valueOf(1), enumDecl.members().get(0).ordinalOrNull());
        assertEquals(Integer.valueOf(2), enumDecl.members().get(1).ordinalOrNull());
        assertNull(enumDecl.members().get(2).ordinalOrNull());
    }

    @Test
    void actorDeclarationStructure() {
        var model = parse("""
                namespace com.example
                @description("A guest user")
                actor GuestUser
                """);
        var actor = assertInstanceOf(SyntaxActorDecl.class, model.declarations().get(0));
        assertEquals("GuestUser", actor.name());
        assertNull(actor.parentOrNull());
        assertEquals(1, actor.traits().size());
        assertEquals("description", actor.traits().get(0).name());
    }

    @Test
    void stateMachineDeclarationStructure() {
        var model = parse("""
                namespace com.example
                statemachine OrderLifecycle {
                    entity: Order
                    field: status
                    states: [PENDING, PAID, CANCELLED]
                    initial: PENDING
                    terminal: [PAID, CANCELLED]
                    transitions: [
                        PENDING -> PAID { guard: "payment ok" action: "emit event" }
                    ]
                }
                """);
        var sm = assertInstanceOf(SyntaxStateMachineDecl.class, model.declarations().get(0));
        assertEquals("OrderLifecycle", sm.name());
        assertEquals("Order", sm.entityName());
        assertEquals("status", sm.fieldName());
        assertEquals(List.of("PENDING", "PAID", "CANCELLED"), sm.states());
        assertEquals("PENDING", sm.initialState());
        assertEquals(List.of("PAID", "CANCELLED"), sm.terminalStates());
        assertEquals(1, sm.transitions().size());
        var t = sm.transitions().get(0);
        assertEquals("PENDING", t.fromState());
        assertEquals("PAID", t.toState());
        assertEquals("payment ok", t.guardOrNull());
        assertEquals("emit event", t.actionOrNull());
    }

    // ── trait forms ───────────────────────────────────────────────────────────

    @Test
    void namedTraitArgsAreLowered() {
        var model = parse("""
                namespace com.example
                @kpi(metric: "Conv", target: ">80%")
                journey MyJourney {}
                """);
        var journey = assertInstanceOf(SyntaxJourneyDecl.class, model.declarations().get(0));
        assertEquals(1, journey.traits().size());
        var trait = journey.traits().get(0);
        assertEquals("kpi", trait.name());
        assertEquals(2, trait.args().size());
        assertEquals("metric", trait.args().get(0).keyOrNull());
        var strVal = assertInstanceOf(SyntaxTraitValue.StringVal.class, trait.args().get(0).value());
        assertEquals("Conv", strVal.value());
    }

    // ── multiple declarations ─────────────────────────────────────────────────

    @Test
    void multipleDeclarationsAreCollected() {
        var model = parse("""
                namespace com.example
                entity Order { id: String }
                actor Customer
                enum Status { PENDING PAID }
                shape Money { amount: Float }
                """);
        assertEquals(4, model.declarations().size());
        assertInstanceOf(SyntaxEntityDecl.class,  model.declarations().get(0));
        assertInstanceOf(SyntaxActorDecl.class,   model.declarations().get(1));
        assertInstanceOf(SyntaxEnumDecl.class,    model.declarations().get(2));
        assertInstanceOf(SyntaxShapeDecl.class,   model.declarations().get(3));
    }

    // ── no IR types in syntax layer ───────────────────────────────────────────

    @Test
    void syntaxModelContainsNoIrTypes() {
        // Verifies at the type-system level that SyntaxModel and its nested DTOs
        // live in com.genairus.chronos.syntax, not in the IR package.
        var model = parse("namespace com.example entity Foo { id: String }");
        assertEquals("com.genairus.chronos.syntax", model.getClass().getPackageName(),
                "SyntaxModel must be in the syntax package");
        var decl = model.declarations().get(0);
        assertEquals("com.genairus.chronos.syntax", decl.getClass().getPackageName(),
                "SyntaxDecl impl must be in the syntax package");
    }

    // ── syntax error handling ─────────────────────────────────────────────────

    @Test
    void syntaxErrorThrowsChronosParseException() {
        var ex = assertThrows(ChronosParseException.class,
                () -> parse("namespace com.example entity { broken }"));
        assertFalse(ex.errors().isEmpty());
    }
}
