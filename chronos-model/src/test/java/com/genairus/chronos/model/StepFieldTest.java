package com.genairus.chronos.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StepFieldTest {

    private static final SourceLocation LOC = SourceLocation.of("test.chronos", 1, 1);

    @Test
    void actionField() {
        StepField f = new StepField.ActionField("Clicks Place Order", LOC);
        assertInstanceOf(StepField.ActionField.class, f);
        assertEquals("Clicks Place Order", ((StepField.ActionField) f).text());
    }

    @Test
    void expectationField() {
        StepField f = new StepField.ExpectationField("Order created with status PAID", LOC);
        assertInstanceOf(StepField.ExpectationField.class, f);
        assertEquals("Order created with status PAID", ((StepField.ExpectationField) f).text());
    }

    @Test
    void outcomeField() {
        var expr = new OutcomeExpr.TransitionTo("OrderConfirmed", LOC);
        StepField f = new StepField.OutcomeField(expr, LOC);
        assertInstanceOf(StepField.OutcomeField.class, f);
        assertEquals(expr, ((StepField.OutcomeField) f).expr());
    }

    @Test
    void telemetryField() {
        StepField f = new StepField.TelemetryField(List.of("OrderPlacedEvent", "PaymentProcessedEvent"), LOC);
        assertInstanceOf(StepField.TelemetryField.class, f);
        assertEquals(2, ((StepField.TelemetryField) f).events().size());
    }

    @Test
    void riskField() {
        StepField f = new StepField.RiskField("Payment gateway timeout risk", LOC);
        assertInstanceOf(StepField.RiskField.class, f);
        assertEquals("Payment gateway timeout risk", ((StepField.RiskField) f).text());
    }

    @Test
    void sealedExhaustivePatternMatch() {
        StepField f = new StepField.RiskField("some risk", LOC);
        String result = switch (f) {
            case StepField.ActionField      a -> "action";
            case StepField.ExpectationField e -> "expectation";
            case StepField.OutcomeField     o -> "outcome";
            case StepField.TelemetryField   t -> "telemetry";
            case StepField.RiskField        r -> "risk";
        };
        assertEquals("risk", result);
    }
}
