package com.genairus.chronos.validator;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-034: TransitionTo() in journey steps must reference a state declared in a statemachine.
 */
class Chr034TransitionToValidationTest {

    @Test
    void validTransitionToReferencingDeclaredState() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity Order {
                    status: OrderStatus
                }
                
                enum OrderStatus {
                    PENDING
                    CONFIRMED
                    SHIPPED
                }
                
                statemachine OrderLifecycle {
                    entity: Order
                    field: status
                    states: [PENDING, CONFIRMED, SHIPPED]
                    initial: PENDING
                    terminal: [SHIPPED]
                    transitions: [
                        PENDING -> CONFIRMED,
                        CONFIRMED -> SHIPPED
                    ]
                }
                
                actor Customer
                
                journey Checkout {
                    actor: Customer
                    steps: [
                        step PlaceOrder {
                            action: "Customer places order"
                            expectation: "Order is created"
                            outcome: TransitionTo(CONFIRMED)
                        }
                    ]
                    outcomes: {
                        success: "Order confirmed"
                    }
                }
                """, "test").modelOrNull();

        ValidationResult result = new ChronosValidator().validate(model);
        assertTrue(!result.hasErrors(), "Valid TransitionTo should pass validation");
    }

    @Test
    void invalidTransitionToReferencingUndeclaredState() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity Order {
                    status: OrderStatus
                }
                
                enum OrderStatus {
                    PENDING
                    CONFIRMED
                }
                
                statemachine OrderLifecycle {
                    entity: Order
                    field: status
                    states: [PENDING, CONFIRMED]
                    initial: PENDING
                    terminal: [CONFIRMED]
                    transitions: [
                        PENDING -> CONFIRMED
                    ]
                }
                
                actor Customer
                
                journey Checkout {
                    actor: Customer
                    steps: [
                        step PlaceOrder {
                            action: "Customer places order"
                            expectation: "Order is created"
                            outcome: TransitionTo(SHIPPED)
                        }
                    ]
                    outcomes: {
                        success: "Order confirmed"
                    }
                }
                """, "test").modelOrNull();

        ValidationResult result = new ChronosValidator().validate(model);
        assertTrue(result.hasErrors());
        assertEquals(1, result.errors().size());
        assertEquals("CHR-034", result.errors().get(0).code());
        assertTrue(result.errors().get(0).message().contains("SHIPPED"));
        assertTrue(result.errors().get(0).message().contains("PlaceOrder"));
    }

    @Test
    void validTransitionToInVariantStep() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity Order {
                    status: OrderStatus
                }
                
                enum OrderStatus {
                    PENDING
                    FAILED
                }
                
                statemachine OrderLifecycle {
                    entity: Order
                    field: status
                    states: [PENDING, FAILED]
                    initial: PENDING
                    terminal: [FAILED]
                    transitions: [
                        PENDING -> FAILED
                    ]
                }
                
                error PaymentError {
                    code: "PAYMENT_FAILED"
                    severity: high
                }
                
                actor Customer
                
                journey Checkout {
                    actor: Customer
                    steps: [
                        step PlaceOrder {
                            action: "Customer places order"
                            expectation: "Order is created"
                        }
                    ]
                    variants: {
                        PaymentFailed: {
                            trigger: PaymentError
                            steps: [
                                step NotifyFailure {
                                    action: "Notify customer"
                                    expectation: "Error message shown"
                                    outcome: TransitionTo(FAILED)
                                }
                            ]
                        }
                    }
                    outcomes: {
                        success: "Order confirmed"
                    }
                }
                """, "test").modelOrNull();

        ValidationResult result = new ChronosValidator().validate(model);
        assertTrue(!result.hasErrors(), "Valid TransitionTo in variant should pass validation");
    }
}

