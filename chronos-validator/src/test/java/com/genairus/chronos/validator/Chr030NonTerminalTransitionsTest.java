package com.genairus.chronos.validator;

import com.genairus.chronos.model.ChronosModel;
import com.genairus.chronos.parser.ChronosModelParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-030: Every non-terminal state must have at least one outbound transition.
 */
class Chr030NonTerminalTransitionsTest {

    @Test
    void validStateMachineWithAllNonTerminalStatesHavingOutbound() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example

                entity Order {
                    status: OrderStatus
                }

                enum OrderStatus {
                    PENDING
                    PAID
                    SHIPPED
                    DELIVERED
                    CANCELLED
                }
                
                statemachine OrderLifecycle {
                    entity: Order
                    field: status
                    states: [PENDING, PAID, SHIPPED]
                    initial: PENDING
                    terminal: [SHIPPED]
                    transitions: [
                        PENDING -> PAID,
                        PAID -> SHIPPED
                    ]
                }
                """);

        ValidationResult result = new ChronosValidator().validate(model);
        assertTrue(!result.hasErrors(), "Valid state machine should pass validation");
    }

    @Test
    void invalidNonTerminalStateWithNoOutbound() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example

                entity Order {
                    status: OrderStatus
                }

                enum OrderStatus {
                    PENDING
                    PAID
                    SHIPPED
                    DELIVERED
                    CANCELLED
                }
                
                statemachine OrderLifecycle {
                    entity: Order
                    field: status
                    states: [PENDING, PAID, SHIPPED]
                    initial: PENDING
                    terminal: [SHIPPED]
                    transitions: [
                        PENDING -> PAID
                    ]
                }
                """);

        ValidationResult result = new ChronosValidator().validate(model);
        assertFalse(!result.hasErrors());
        assertEquals(1, result.errors().size());
        assertEquals("CHR-030", result.errors().get(0).ruleCode());
        assertTrue(result.errors().get(0).message().contains("PAID"));
        assertTrue(result.errors().get(0).message().contains("no outbound transitions"));
    }

    @Test
    void validTerminalStateWithNoOutbound() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example

                entity Order {
                    status: OrderStatus
                }

                enum OrderStatus {
                    PENDING
                    PAID
                    SHIPPED
                    DELIVERED
                    CANCELLED
                }
                
                statemachine OrderLifecycle {
                    entity: Order
                    field: status
                    states: [PENDING, PAID]
                    initial: PENDING
                    terminal: [PAID]
                    transitions: [
                        PENDING -> PAID
                    ]
                }
                """);

        ValidationResult result = new ChronosValidator().validate(model);
        assertTrue(!result.hasErrors(), "Terminal states don't need outbound transitions");
    }

    @Test
    void invalidMultipleNonTerminalStatesWithNoOutbound() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example

                entity Order {
                    status: OrderStatus
                }

                enum OrderStatus {
                    PENDING
                    PAID
                    SHIPPED
                    DELIVERED
                    CANCELLED
                }
                
                statemachine OrderLifecycle {
                    entity: Order
                    field: status
                    states: [PENDING, PAID, SHIPPED, DELIVERED]
                    initial: PENDING
                    terminal: [DELIVERED]
                    transitions: [
                        PENDING -> PAID
                    ]
                }
                """);

        ValidationResult result = new ChronosValidator().validate(model);
        assertFalse(!result.hasErrors());
        assertEquals(2, result.errors().size());
        assertTrue(result.errors().stream().allMatch(e -> e.ruleCode().equals("CHR-030")));
    }

    @Test
    void validStateMachineWithMultipleOutboundFromSameState() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example

                entity Order {
                    status: OrderStatus
                }

                enum OrderStatus {
                    PENDING
                    PAID
                    SHIPPED
                    DELIVERED
                    CANCELLED
                }
                
                statemachine OrderLifecycle {
                    entity: Order
                    field: status
                    states: [PENDING, PAID, CANCELLED]
                    initial: PENDING
                    terminal: [PAID, CANCELLED]
                    transitions: [
                        PENDING -> PAID,
                        PENDING -> CANCELLED
                    ]
                }
                """);

        ValidationResult result = new ChronosValidator().validate(model);
        assertTrue(!result.hasErrors(), "State with multiple outbound transitions should be valid");
    }
}

