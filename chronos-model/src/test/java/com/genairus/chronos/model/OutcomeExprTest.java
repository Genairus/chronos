package com.genairus.chronos.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OutcomeExprTest {

    private static final SourceLocation LOC = SourceLocation.of("test.chronos", 1, 1);

    @Test
    void transitionToCarriesTargetName() {
        OutcomeExpr expr = new OutcomeExpr.TransitionTo("OrderConfirmed", LOC);
        assertEquals("OrderConfirmed", expr.target());
        assertInstanceOf(OutcomeExpr.TransitionTo.class, expr);
    }

    @Test
    void returnToStepCarriesTargetName() {
        OutcomeExpr expr = new OutcomeExpr.ReturnToStep("ChoosePayment", LOC);
        assertEquals("ChoosePayment", expr.target());
        assertInstanceOf(OutcomeExpr.ReturnToStep.class, expr);
    }

    @Test
    void sealedExhaustivePatternMatch() {
        OutcomeExpr expr = new OutcomeExpr.TransitionTo("Done", LOC);
        String result = switch (expr) {
            case OutcomeExpr.TransitionTo t -> "transition:" + t.target();
            case OutcomeExpr.ReturnToStep r -> "return:" + r.target();
        };
        assertEquals("transition:Done", result);
    }
}
