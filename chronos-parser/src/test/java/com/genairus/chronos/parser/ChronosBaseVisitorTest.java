package com.genairus.chronos.parser;

import com.genairus.chronos.parser.generated.ChronosBaseVisitor;
import com.genairus.chronos.parser.generated.ChronosLexer;
import com.genairus.chronos.parser.generated.ChronosParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests the default {@code visitChildren} delegation of every method in the
 * generated {@link ChronosBaseVisitor}.
 *
 * <p>{@link ChronosModelBuilder} overrides all visitor methods, so the base-class
 * bodies are never executed during normal parse usage.  Each test here walks a
 * parse tree using a plain, non-overriding {@code ChronosBaseVisitor<Object>}
 * to cover every {@code visit*} default implementation.
 *
 * <p>The direct-invocation tests (suffixed {@code _calledDirectly}) additionally
 * extract specific {@code Context} objects from the parse tree and call the
 * corresponding visitor methods explicitly — exercising rules that
 * {@code ChronosModelBuilder} accesses via {@code ctx.xxx()} rather than
 * through {@code visit()}.
 */
class ChronosBaseVisitorTest {

    // ── helpers ─────────────────────────────────────────────────────────────

    /**
     * Parses Chronos source text directly to an ANTLR {@link ChronosParser.ModelContext}
     * without running {@link ChronosModelBuilder}; error listeners are suppressed so
     * test output stays clean.
     */
    private static ChronosParser.ModelContext parseTree(String source) {
        var lexer  = new ChronosLexer(CharStreams.fromString(source));
        var tokens = new CommonTokenStream(lexer);
        var parser = new ChronosParser(tokens);
        parser.removeErrorListeners();
        return parser.model();
    }

    /** Creates a fresh default {@code ChronosBaseVisitor<Object>} with no overrides. */
    private static ChronosBaseVisitor<Object> baseVisitor() {
        return new ChronosBaseVisitor<>() {};
    }

    // ── full-tree traversal: namespaceDecl, useDecl, entity ────────────────

    @Test
    void visitModel_andNamespaceDecl_traverseViaVisitChildren() {
        // visitModel  visitNamespaceDecl  visitQualifiedId
        // visitShapeDefinition  visitEntityDef  visitFieldDef
        // visitTypeRef  visitPrimitiveType
        var tree = parseTree("""
                namespace com.example
                entity Order { id: String }
                """);
        assertDoesNotThrow(() -> baseVisitor().visit(tree));
    }

    @Test
    void visitUseDecl_traversesViaVisitChildren() {
        // visitUseDecl
        var tree = parseTree("""
                namespace com.example
                use com.other#Customer
                entity Order { id: String }
                """);
        assertDoesNotThrow(() -> baseVisitor().visit(tree));
    }

    // ── full-tree traversal: trait forms ─────────────────────────────────

    @Test
    void visitTraitApplication_andAllTraitValueAlternatives_traverseViaVisitChildren() {
        // visitTraitApplication  visitTraitId  visitTraitArgList  visitTraitArg
        // visitTraitValue with STRING, NUMBER, BOOL, qualifiedId
        var tree = parseTree("""
                namespace com.example
                @bare
                @positional("string-value")
                @numeric(42)
                @flag(true)
                @reference(com.example.Base)
                @named(key: "v", count: 1)
                entity Order { id: String }
                """);
        assertDoesNotThrow(() -> baseVisitor().visit(tree));
    }

    // ── full-tree traversal: all remaining shape-def types ────────────────

    @Test
    void visitAllShapeDefTypes_traverseViaVisitChildren() {
        // visitShapeStructDef  visitListDef  visitMapDef
        // visitEnumDef  visitEnumMember  visitActorDef  visitPolicyDef
        var tree = parseTree("""
                namespace com.example
                shape Money { amount: Float currency: String }
                list Tags { member: String }
                map Headers { key: String value: String }
                enum Status { PENDING = 1 PAID = 2 FAILED }
                actor Customer
                policy DataPolicy { description: "7-year retention" }
                """);
        assertDoesNotThrow(() -> baseVisitor().visit(tree));
    }

    // ── full-tree traversal: typeRef forms ────────────────────────────────

    @Test
    void visitTypeRef_allForms_traverseViaVisitChildren() {
        // visitTypeRef via List<>, Map<,>, primitive, and named-type-ref paths
        var tree = parseTree("""
                namespace com.example
                entity Complex {
                    a: List<String>
                    b: Map<String, Integer>
                    c: MyNamedType
                }
                """);
        assertDoesNotThrow(() -> baseVisitor().visit(tree));
    }

    // ── full-tree traversal: complete journey ────────────────────────────

    @Test
    void visitJourney_allSections_traverseViaVisitChildren() {
        // visitJourneyDef  visitJourneyBody  visitActorDecl
        // visitPreconditionsDecl  visitStepsDecl  visitStep
        // visitStepField (action / expectation / outcome / telemetry / risk)
        // visitOutcomeExpr (TransitionTo and ReturnToStep)
        // visitVariantsDecl  visitVariantEntry  visitVariantBody
        // visitOutcomesDecl  visitOutcomeEntry
        var tree = parseTree("""
                namespace com.example
                error CardDeclinedError {
                    code: "CARD_DECLINED"
                    severity: high
                    recoverable: true
                    message: "Card declined"
                }
                journey Checkout {
                    actor: Customer
                    preconditions: ["Cart not empty", "User ready"]
                    steps: [
                        step PlaceOrder {
                            action: "user clicks confirm"
                            expectation: "order is saved"
                            outcome: TransitionTo(Confirmed)
                            telemetry: [order_placed]
                            risk: "gateway may be slow"
                        },
                        step Fallback {
                            action: "retry"
                            expectation: "retried"
                            outcome: ReturnToStep(PlaceOrder)
                        }
                    ]
                    variants: {
                        Declined: {
                            trigger: CardDeclinedError
                            steps: [
                                step Notify {
                                    action: "show error"
                                    expectation: "error visible"
                                }
                            ]
                            outcome: ReturnToStep(PlaceOrder)
                        }
                    }
                    outcomes: {
                        success: "Order confirmed",
                        failure: "Cart intact"
                    }
                }
                """);
        assertDoesNotThrow(() -> baseVisitor().visit(tree));
    }

    // ── direct method invocations ─────────────────────────────────────────
    // ChronosModelBuilder accesses these rules via ctx.xxx() rather than
    // visit(), so their ChronosBaseVisitor bodies are otherwise unreachable.
    // Calling them directly proves the default visitChildren delegation is safe.

    @Test
    void visitTraitId_calledDirectly_delegatesToVisitChildren() {
        var tree = parseTree("""
                namespace com.example
                @description("hello")
                entity Order { id: String }
                """);
        var traitCtx = tree.shapeDefinition(0).traitApplication(0);
        Object result = baseVisitor().visitTraitId(traitCtx.traitId());
        assertNull(result);
    }

    @Test
    void visitTraitArgList_calledDirectly_delegatesToVisitChildren() {
        var tree = parseTree("""
                namespace com.example
                @slo(p99: "200ms")
                entity Order { id: String }
                """);
        var traitCtx = tree.shapeDefinition(0).traitApplication(0);
        Object result = baseVisitor().visitTraitArgList(traitCtx.traitArgList());
        assertNull(result);
    }

    @Test
    void visitJourneyBody_calledDirectly_delegatesToVisitChildren() {
        var tree = parseTree("""
                namespace com.example
                journey Checkout {
                    actor: Customer
                }
                """);
        var bodyCtx = tree.shapeDefinition(0).shapeDef().journeyDef().journeyBody();
        Object result = baseVisitor().visitJourneyBody(bodyCtx);
        assertNull(result);
    }

    @Test
    void visitActorDecl_calledDirectly_delegatesToVisitChildren() {
        var tree = parseTree("""
                namespace com.example
                journey Checkout {
                    actor: Customer
                }
                """);
        var bodyCtx = tree.shapeDefinition(0).shapeDef().journeyDef().journeyBody();
        Object result = baseVisitor().visitActorDecl(bodyCtx.actorDecl());
        assertNull(result);
    }

    @Test
    void visitPreconditionsDecl_calledDirectly_delegatesToVisitChildren() {
        var tree = parseTree("""
                namespace com.example
                journey Checkout {
                    actor: Customer
                    preconditions: ["Cart not empty"]
                }
                """);
        var bodyCtx = tree.shapeDefinition(0).shapeDef().journeyDef().journeyBody();
        Object result = baseVisitor().visitPreconditionsDecl(bodyCtx.preconditionsDecl());
        assertNull(result);
    }

    @Test
    void visitStepsDecl_calledDirectly_delegatesToVisitChildren() {
        var tree = parseTree("""
                namespace com.example
                journey Checkout {
                    actor: Customer
                    steps: [
                        step DoIt { action: "do" expectation: "done" }
                    ]
                }
                """);
        var bodyCtx = tree.shapeDefinition(0).shapeDef().journeyDef().journeyBody();
        Object result = baseVisitor().visitStepsDecl(bodyCtx.stepsDecl());
        assertNull(result);
    }

    @Test
    void visitOutcomeEntry_calledDirectly_delegatesToVisitChildren() {
        var tree = parseTree("""
                namespace com.example
                journey Checkout {
                    actor: Customer
                    outcomes: {
                        success: "done",
                        failure: "undone"
                    }
                }
                """);
        var outcomesCtx = tree.shapeDefinition(0).shapeDef().journeyDef()
                .journeyBody().outcomesDecl();
        Object result = baseVisitor().visitOutcomeEntry(outcomesCtx.outcomeEntry(0));
        assertNull(result);
    }
}
