package com.genairus.chronos.validator;

import com.genairus.chronos.model.ChronosModel;
import com.genairus.chronos.parser.ChronosModelParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-033: The referenced entity and field must be defined; field type should be an enum.
 */
class Chr033EntityFieldValidationTest {

    @Test
    void validStateMachineWithEnumField() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity Order {
                    status: OrderStatus
                }
                
                enum OrderStatus {
                    PENDING
                    PAID
                    SHIPPED
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
        assertFalse(result.hasErrors(), "Valid statemachine with enum field should pass validation");
    }

    @Test
    void invalidEntityNotDefined() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                enum OrderStatus {
                    PENDING
                    PAID
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
        assertTrue(result.hasErrors());
        assertEquals(1, result.errors().size());
        assertEquals("CHR-033", result.errors().get(0).ruleCode());
        assertTrue(result.errors().get(0).message().contains("Order"));
        assertTrue(result.errors().get(0).message().contains("not defined"));
    }

    @Test
    void invalidFieldNotDefined() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity Order {
                    orderId: String
                }
                
                enum OrderStatus {
                    PENDING
                    PAID
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
        assertTrue(result.hasErrors());
        assertEquals(1, result.errors().size());
        assertEquals("CHR-033", result.errors().get(0).ruleCode());
        assertTrue(result.errors().get(0).message().contains("status"));
        assertTrue(result.errors().get(0).message().contains("not defined on entity"));
    }

    @Test
    void invalidFieldTypeNotEnum() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity Order {
                    status: String
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
        assertTrue(result.hasErrors());
        assertEquals(1, result.errors().size());
        assertEquals("CHR-033", result.errors().get(0).ruleCode());
        assertTrue(result.errors().get(0).message().contains("status"));
        assertTrue(result.errors().get(0).message().contains("must have an enum type"));
    }
}

