package com.genairus.chronos.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class VariantTest {

    private static final SourceLocation LOC = SourceLocation.of("test.chronos", 1, 1);

    @Test
    void variantWithOutcome() {
        var outcome = new OutcomeExpr.ReturnToStep("ChoosePayment", LOC);
        var variant = new Variant("PaymentDeclined", "Gateway returned declined", List.of(),
                Optional.of(outcome), LOC);

        assertEquals("PaymentDeclined", variant.name());
        assertEquals("Gateway returned declined", variant.trigger());
        assertTrue(variant.steps().isEmpty());
        assertTrue(variant.outcome().isPresent());
        assertInstanceOf(OutcomeExpr.ReturnToStep.class, variant.outcome().get());
    }

    @Test
    void variantWithoutOutcome() {
        var variant = new Variant("InventoryUnavailable", "Item out of stock", List.of(),
                Optional.empty(), LOC);
        assertTrue(variant.outcome().isEmpty());
    }

    @Test
    void variantWithSteps() {
        var step = new Step("NotifyUser", List.of(),
                List.of(new StepField.ExpectationField("Message shown", LOC)), LOC);
        var variant = new Variant("NetworkError", "Connection timed out", List.of(step),
                Optional.empty(), LOC);
        assertEquals(1, variant.steps().size());
        assertEquals("NotifyUser", variant.steps().get(0).name());
    }
}
