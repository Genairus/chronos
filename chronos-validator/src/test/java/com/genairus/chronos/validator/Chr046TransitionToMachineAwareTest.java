package com.genairus.chronos.validator;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-046 and CHR-047: Machine-aware TransitionTo() validation.
 *
 * <ul>
 *   <li>CHR-046 ERROR:   TransitionTo target state is ambiguous — declared in multiple statemachines.
 *   <li>CHR-047 WARNING: TransitionTo target state has no declared incoming transitions in the
 *                        identified statemachine.
 * </ul>
 *
 * <p>These tests also verify that the variant-level {@code outcome:} field is now validated
 * (a gap that existed in the original CHR-034 check).
 */
class Chr046TransitionToMachineAwareTest {

    // ── Test 1: valid single-machine transition with incoming edge ────────────

    @Test
    void validSingleMachineTransitionWithIncomingEdge() {
        var m = new ChronosCompiler().compile("""
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
                        PENDING -> PAID
                    ]
                }

                actor Customer

                journey Checkout {
                    actor: Customer
                    steps: [
                        step PlaceOrder {
                            action: "Customer places order"
                            expectation: "Order is confirmed"
                            outcome: TransitionTo(PAID)
                        }
                    ]
                    outcomes: {
                        success: "Order placed"
                    }
                }
                """, "test").modelOrNull();

        var result = new ChronosValidator().validate(m);
        assertFalse(result.hasErrors(),
                "Valid single-machine TransitionTo should produce no errors; got: " + result.diagnostics());
        assertFalse(result.diagnostics().stream().anyMatch(d -> "CHR-047".equals(d.code())),
                "No CHR-047 expected; got: " + result.diagnostics());
    }

    // ── Test 2: undeclared state still fires CHR-034 (regression) ────────────

    @Test
    void undeclaredStateStillFiresChr034() {
        var m = new ChronosCompiler().compile("""
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
                        PENDING -> PAID
                    ]
                }

                actor Customer

                journey Checkout {
                    actor: Customer
                    steps: [
                        step PlaceOrder {
                            action: "Place it"
                            expectation: "Done"
                            outcome: TransitionTo(SHIPPED)
                        }
                    ]
                    outcomes: {
                        success: "Order placed"
                    }
                }
                """, "test").modelOrNull();

        var errors034 = new ChronosValidator().validate(m).errors().stream()
                .filter(d -> "CHR-034".equals(d.code()))
                .toList();
        assertEquals(1, errors034.size(),
                "Expected 1 CHR-034 for undeclared state; got: " + errors034);
        assertTrue(errors034.get(0).message().contains("SHIPPED"));
        assertTrue(errors034.get(0).message().contains("PlaceOrder"));
    }

    // ── Test 3: state in multiple machines fires CHR-046 ─────────────────────

    @Test
    void stateInMultipleMachinesFiresChr046() {
        var m = new ChronosCompiler().compile("""
                namespace com.example

                entity Order {
                    status: OrderStatus
                }

                entity Shipment {
                    status: ShipmentStatus
                }

                enum OrderStatus {
                    PENDING
                    ACTIVE
                }

                enum ShipmentStatus {
                    ACTIVE
                    SHIPPED
                }

                statemachine OrderLifecycle {
                    entity: Order
                    field: status
                    states: [PENDING, ACTIVE]
                    initial: PENDING
                    terminal: [ACTIVE]
                    transitions: [
                        PENDING -> ACTIVE
                    ]
                }

                statemachine ShipmentLifecycle {
                    entity: Shipment
                    field: status
                    states: [ACTIVE, SHIPPED]
                    initial: ACTIVE
                    terminal: [SHIPPED]
                    transitions: [
                        ACTIVE -> SHIPPED
                    ]
                }

                actor Customer

                journey ProcessOrder {
                    actor: Customer
                    steps: [
                        step Activate {
                            action: "Activate something"
                            expectation: "Activated"
                            outcome: TransitionTo(ACTIVE)
                        }
                    ]
                    outcomes: {
                        success: "Done"
                    }
                }
                """, "test").modelOrNull();

        var errors046 = new ChronosValidator().validate(m).errors().stream()
                .filter(d -> "CHR-046".equals(d.code()))
                .toList();
        assertEquals(1, errors046.size(),
                "Expected 1 CHR-046 for ambiguous state ACTIVE; got: " + errors046);
        String msg = errors046.get(0).message();
        assertTrue(msg.contains("ACTIVE"),          "message should contain the state name");
        assertTrue(msg.contains("ambiguous"),        "message should say ambiguous");
        assertTrue(msg.contains("OrderLifecycle"),   "message should contain first machine name");
        assertTrue(msg.contains("ShipmentLifecycle"),"message should contain second machine name");
    }

    // ── Test 4: states with declared incoming transitions do NOT fire CHR-047 ─

    @Test
    void statesWithIncomingTransitionDoNotFireChr047() {
        var m = new ChronosCompiler().compile("""
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

                actor Customer

                journey Checkout {
                    actor: Customer
                    steps: [
                        step PayOrder {
                            action: "Pay"
                            expectation: "Paid"
                            outcome: TransitionTo(PAID)
                        },
                        step ShipOrder {
                            action: "Ship"
                            expectation: "Shipped"
                            outcome: TransitionTo(SHIPPED)
                        }
                    ]
                    outcomes: {
                        success: "All done"
                    }
                }
                """, "test").modelOrNull();

        var result = new ChronosValidator().validate(m);
        assertFalse(result.diagnostics().stream().anyMatch(d -> "CHR-047".equals(d.code())),
                "States with declared incoming edges must not trigger CHR-047; got: " + result.diagnostics());
    }

    // ── Test 5: state with no incoming transition fires CHR-047 ──────────────

    @Test
    void stateWithNoIncomingTransitionFiresChr047() {
        var m = new ChronosCompiler().compile("""
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
                        PENDING -> PAID
                    ]
                }

                actor Customer

                journey ResetOrder {
                    actor: Customer
                    steps: [
                        step Reset {
                            action: "Reset order"
                            expectation: "Order reset"
                            outcome: TransitionTo(PENDING)
                        }
                    ]
                    outcomes: {
                        success: "Reset done"
                    }
                }
                """, "test").modelOrNull();

        var result = new ChronosValidator().validate(m);
        var warnings047 = result.warnings().stream()
                .filter(d -> "CHR-047".equals(d.code()))
                .toList();
        assertEquals(1, warnings047.size(),
                "Expected 1 CHR-047 for PENDING (no incoming edge); got: " + warnings047);
        String msg = warnings047.get(0).message();
        assertTrue(msg.contains("PENDING"),                      "message should contain state name");
        assertTrue(msg.contains("OrderLifecycle"),               "message should contain machine name");
        assertTrue(msg.contains("no declared incoming transitions"), "message should explain the problem");
    }

    // ── Test 6: variant-level outcome is validated (gap fix) ──────────────────

    @Test
    void variantLevelOutcomeIsValidated() {
        var m = new ChronosCompiler().compile("""
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
                            action: "Place"
                            expectation: "Placed"
                        }
                    ]
                    variants: {
                        PaymentFailed: {
                            trigger: PaymentError
                            steps: [
                                step NotifyFailure {
                                    action: "Notify"
                                    expectation: "Notified"
                                }
                            ]
                            outcome: TransitionTo(FAILED)
                        }
                    }
                    outcomes: {
                        success: "Completed"
                    }
                }
                """, "test").modelOrNull();

        var result = new ChronosValidator().validate(m);
        assertFalse(result.hasErrors(),
                "Variant-level TransitionTo(FAILED) with incoming edge should be valid; got: "
                + result.diagnostics());
        assertFalse(result.diagnostics().stream().anyMatch(d -> "CHR-047".equals(d.code())),
                "No CHR-047 expected; got: " + result.diagnostics());
    }

    // ── Test 7: CHR-046 message quality ──────────────────────────────────────

    @Test
    void chr046MessageQuality() {
        var m = new ChronosCompiler().compile("""
                namespace com.example

                entity Order {
                    status: OrderStatus
                }

                entity Shipment {
                    status: ShipmentStatus
                }

                enum OrderStatus {
                    PENDING
                    ACTIVE
                }

                enum ShipmentStatus {
                    ACTIVE
                    SHIPPED
                }

                statemachine OrderLifecycle {
                    entity: Order
                    field: status
                    states: [PENDING, ACTIVE]
                    initial: PENDING
                    terminal: [ACTIVE]
                    transitions: [
                        PENDING -> ACTIVE
                    ]
                }

                statemachine ShipmentLifecycle {
                    entity: Shipment
                    field: status
                    states: [ACTIVE, SHIPPED]
                    initial: ACTIVE
                    terminal: [SHIPPED]
                    transitions: [
                        ACTIVE -> SHIPPED
                    ]
                }

                actor Customer

                journey ProcessOrder {
                    actor: Customer
                    steps: [
                        step Activate {
                            action: "Activate"
                            expectation: "Activated"
                            outcome: TransitionTo(ACTIVE)
                        }
                    ]
                    outcomes: {
                        success: "Done"
                    }
                }
                """, "test").modelOrNull();

        var chr046 = new ChronosValidator().validate(m).errors().stream()
                .filter(d -> "CHR-046".equals(d.code()))
                .findFirst();

        assertTrue(chr046.isPresent(), "Expected CHR-046 to fire");
        String msg = chr046.get().message();
        assertTrue(msg.contains("ACTIVE"),           "message should contain the ambiguous state name");
        assertTrue(msg.contains("ambiguous"),         "message should say ambiguous");
        assertTrue(msg.contains("OrderLifecycle"),    "message should contain OrderLifecycle");
        assertTrue(msg.contains("ShipmentLifecycle"), "message should contain ShipmentLifecycle");
    }
}
