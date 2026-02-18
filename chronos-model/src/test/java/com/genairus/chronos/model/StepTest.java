package com.genairus.chronos.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StepTest {

    private static final SourceLocation LOC = SourceLocation.of("test.chronos", 1, 1);

    private Step buildStep(StepField... fields) {
        return new Step("PlaceOrder", List.of(), List.of(fields), LOC);
    }

    @Test
    void actionAccessor() {
        var step = buildStep(new StepField.ActionField("Clicks Place Order", LOC));
        assertTrue(step.action().isPresent());
        assertEquals("Clicks Place Order", step.action().get());
    }

    @Test
    void actionEmptyWhenAbsent() {
        var step = buildStep(new StepField.ExpectationField("something", LOC));
        assertTrue(step.action().isEmpty());
    }

    @Test
    void expectationAccessor() {
        var step = buildStep(new StepField.ExpectationField("Order created with status PAID", LOC));
        assertTrue(step.expectation().isPresent());
        assertEquals("Order created with status PAID", step.expectation().get());
    }

    @Test
    void outcomeAccessor() {
        var expr = new OutcomeExpr.TransitionTo("OrderConfirmed", LOC);
        var step = buildStep(new StepField.OutcomeField(expr, LOC));
        assertTrue(step.outcome().isPresent());
        assertInstanceOf(OutcomeExpr.TransitionTo.class, step.outcome().get());
    }

    @Test
    void outcomeEmptyWhenAbsent() {
        var step = buildStep(new StepField.ActionField("action", LOC));
        assertTrue(step.outcome().isEmpty());
    }

    @Test
    void telemetryEventsCollected() {
        var telemetry = new StepField.TelemetryField(
                List.of("OrderPlacedEvent", "PaymentProcessedEvent"), LOC);
        var step = buildStep(telemetry);
        assertEquals(List.of("OrderPlacedEvent", "PaymentProcessedEvent"), step.telemetryEvents());
    }

    @Test
    void telemetryEventsEmptyWhenAbsent() {
        var step = buildStep(new StepField.ActionField("action", LOC));
        assertTrue(step.telemetryEvents().isEmpty());
    }

    @Test
    void riskAccessor() {
        var step = buildStep(new StepField.RiskField("Gateway timeout", LOC));
        assertTrue(step.risk().isPresent());
        assertEquals("Gateway timeout", step.risk().get());
    }

    @Test
    void allFieldTypesInOneStep() {
        var step = buildStep(
                new StepField.ActionField("action text", LOC),
                new StepField.ExpectationField("expectation text", LOC),
                new StepField.OutcomeField(new OutcomeExpr.TransitionTo("Done", LOC), LOC),
                new StepField.TelemetryField(List.of("EventA"), LOC),
                new StepField.RiskField("some risk", LOC)
        );
        assertTrue(step.action().isPresent());
        assertTrue(step.expectation().isPresent());
        assertTrue(step.outcome().isPresent());
        assertEquals(1, step.telemetryEvents().size());
        assertTrue(step.risk().isPresent());
    }
}
