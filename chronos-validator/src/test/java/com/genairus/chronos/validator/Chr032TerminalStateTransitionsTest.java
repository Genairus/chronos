package com.genairus.chronos.validator;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-032: Terminal states must not have outbound transitions.
 */
class Chr032TerminalStateTransitionsTest {

    @Test
    void validTerminalStateWithNoOutbound() {
        var model = new ChronosCompiler().compile("""
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
                """, "test").modelOrNull();

        ValidationResult result = new ChronosValidator().validate(model);
        assertTrue(!result.hasErrors(), "Terminal state with no outbound transitions should be valid");
    }

    @Test
    void invalidTerminalStateWithOutbound() {
        var model = new ChronosCompiler().compile("""
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
                    terminal: [PAID, SHIPPED]
                    transitions: [
                        PENDING -> PAID,
                        PAID -> SHIPPED
                    ]
                }
                """, "test").modelOrNull();

        ValidationResult result = new ChronosValidator().validate(model);
        assertTrue(result.hasErrors());
        assertEquals(1, result.errors().size());
        assertEquals("CHR-032", result.errors().get(0).code());
        assertTrue(result.errors().get(0).message().contains("PAID"));
        assertTrue(result.errors().get(0).message().contains("must not have outbound transitions"));
    }

    @Test
    void invalidMultipleTerminalStatesWithOutbound() {
        var model = new ChronosCompiler().compile("""
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
                    terminal: [PAID, SHIPPED, DELIVERED]
                    transitions: [
                        PENDING -> PAID,
                        PAID -> SHIPPED,
                        SHIPPED -> DELIVERED
                    ]
                }
                """, "test").modelOrNull();

        ValidationResult result = new ChronosValidator().validate(model);
        assertTrue(result.hasErrors());
        assertEquals(2, result.errors().size());
        assertTrue(result.errors().stream().allMatch(e -> e.code().equals("CHR-032")));
    }

    @Test
    void validMultipleTerminalStatesWithNoOutbound() {
        var model = new ChronosCompiler().compile("""
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
                """, "test").modelOrNull();

        ValidationResult result = new ChronosValidator().validate(model);
        assertTrue(!result.hasErrors(), "Multiple terminal states with no outbound transitions should be valid");
    }
}

