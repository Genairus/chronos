package com.genairus.chronos.validator;

import com.genairus.chronos.model.ChronosModel;
import com.genairus.chronos.parser.ChronosModelParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-031: The initial state must be in the states list.
 */
class Chr031InitialStateValidationTest {

    @Test
    void validInitialStateInStatesList() {
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
        assertTrue(!result.hasErrors(), "Valid initial state should pass validation");
    }

    @Test
    void invalidInitialStateNotInStatesList() {
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
                    initial: UNKNOWN
                    terminal: [PAID]
                    transitions: [
                        PENDING -> PAID
                    ]
                }
                """);

        ValidationResult result = new ChronosValidator().validate(model);
        assertFalse(!result.hasErrors());
        assertEquals(1, result.errors().size());
        assertEquals("CHR-031", result.errors().get(0).ruleCode());
        assertTrue(result.errors().get(0).message().contains("UNKNOWN"));
        assertTrue(result.errors().get(0).message().contains("not declared in states list"));
    }

    @Test
    void validInitialStateCanBeTerminal() {
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

                statemachine SingleState {
                    entity: Order
                    field: status
                    states: [PENDING, COMPLETE]
                    initial: PENDING
                    terminal: [COMPLETE]
                    transitions: [
                        PENDING -> COMPLETE
                    ]
                }
                """);

        ValidationResult result = new ChronosValidator().validate(model);
        assertFalse(result.hasErrors(), "Initial state can also be a terminal state");
    }
}

