package com.genairus.chronos.validator;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-029: All states referenced in transitions must be declared in the states list.
 */
class Chr029StateReferencesValidationTest {

    @Test
    void validStateMachineWithAllStatesDeclared() {
        var model = new ChronosCompiler().compile("""
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
                """, "test").modelOrNull();

        ValidationResult result = new ChronosValidator().validate(model);
        assertFalse(result.hasErrors(), "Valid state machine should pass validation");
    }

    @Test
    void invalidFromStateNotDeclared() {
        var model = new ChronosCompiler().compile("""
                namespace com.example

                entity Order {
                    status: OrderStatus
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
                        PENDING -> PAID,
                        UNKNOWN -> PAID
                    ]
                }
                """, "test").modelOrNull();

        ValidationResult result = new ChronosValidator().validate(model);
        assertTrue(result.hasErrors());
        assertEquals(1, result.errors().size());
        assertEquals("CHR-029", result.errors().get(0).code());
        assertTrue(result.errors().get(0).message().contains("UNKNOWN"));
        assertTrue(result.errors().get(0).message().contains("not declared in states list"));
    }

    @Test
    void invalidToStateNotDeclared() {
        var model = new ChronosCompiler().compile("""
                namespace com.example

                entity Order {
                    status: OrderStatus
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
                        PENDING -> UNKNOWN
                    ]
                }
                """, "test").modelOrNull();

        ValidationResult result = new ChronosValidator().validate(model);
        assertTrue(result.hasErrors());
        assertEquals(1, result.errors().size());
        assertEquals("CHR-029", result.errors().get(0).code());
        assertTrue(result.errors().get(0).message().contains("UNKNOWN"));
        assertTrue(result.errors().get(0).message().contains("not declared in states list"));
    }

    @Test
    void invalidBothStatesNotDeclared() {
        var model = new ChronosCompiler().compile("""
                namespace com.example

                entity Order {
                    status: OrderStatus
                }

                enum OrderStatus {
                    PENDING
                }

                statemachine OrderLifecycle {
                    entity: Order
                    field: status
                    states: [PENDING]
                    initial: PENDING
                    terminal: [PENDING]
                    transitions: [
                        UNKNOWN1 -> UNKNOWN2
                    ]
                }
                """, "test").modelOrNull();

        ValidationResult result = new ChronosValidator().validate(model);
        assertTrue(result.hasErrors());
        assertEquals(2, result.errors().size());
        assertTrue(result.errors().stream().allMatch(e -> e.code().equals("CHR-029")));
    }
}

