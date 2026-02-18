package com.genairus.chronos.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JourneyOutcomesTest {

    private static final SourceLocation LOC = SourceLocation.of("test.chronos", 1, 1);

    @Test
    void successAndFailurePresentWhenSet() {
        var outcomes = new JourneyOutcomes("Order created with status PAID", "Cart intact", LOC);
        assertTrue(outcomes.successOutcome().isPresent());
        assertTrue(outcomes.failureOutcome().isPresent());
        assertEquals("Order created with status PAID", outcomes.successOutcome().get());
        assertEquals("Cart intact", outcomes.failureOutcome().get());
    }

    @Test
    void successEmptyWhenNull() {
        var outcomes = new JourneyOutcomes(null, "Cart intact", LOC);
        assertTrue(outcomes.successOutcome().isEmpty());
        assertTrue(outcomes.failureOutcome().isPresent());
    }

    @Test
    void failureEmptyWhenNull() {
        var outcomes = new JourneyOutcomes("Order created", null, LOC);
        assertTrue(outcomes.successOutcome().isPresent());
        assertTrue(outcomes.failureOutcome().isEmpty());
    }
}
