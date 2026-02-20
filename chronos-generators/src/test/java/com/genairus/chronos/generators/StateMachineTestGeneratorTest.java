package com.genairus.chronos.generators;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StateMachineTestGeneratorTest {

    @Test
    void generateTestsForSimpleStateMachine() {
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

        GeneratorOutput output = new StateMachineTestGenerator().generate(model);

        assertEquals(1, output.files().size());
        String testCode = output.content();

        // Check package and imports
        assertTrue(testCode.contains("package com.example.tests;"));
        assertTrue(testCode.contains("import org.junit.jupiter.api.Test;"));
        assertTrue(testCode.contains("import static org.junit.jupiter.api.Assertions.*;"));

        // Check class name
        assertTrue(testCode.contains("public class ComExampleStateMachineTests {"));

        // Check initial state test
        assertTrue(testCode.contains("void testOrderLifecycle_InitialState()"));
        assertTrue(testCode.contains("new Order instances start in PENDING state"));

        // Check transition tests
        assertTrue(testCode.contains("void testOrderLifecycle_PENDINGToPAID()"));
        assertTrue(testCode.contains("Verify transition from PENDING to PAID"));

        assertTrue(testCode.contains("void testOrderLifecycle_PAIDToSHIPPED()"));
        assertTrue(testCode.contains("Verify transition from PAID to SHIPPED"));

        // Check terminal state test
        assertTrue(testCode.contains("void testOrderLifecycle_SHIPPEDIsTerminal()"));
        assertTrue(testCode.contains("SHIPPED is a terminal state"));
    }

    @Test
    void generateTestsWithGuardsAndActions() {
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
                        PENDING -> PAID {
                            guard: "payment received"
                            action: "Emit PaymentReceivedEvent"
                        },
                        PAID -> SHIPPED {
                            guard: "fulfillment dispatched"
                        }
                    ]
                }
                """, "test").modelOrNull();

        GeneratorOutput output = new StateMachineTestGenerator().generate(model);
        String testCode = output.content();

        // Check guard comments
        assertTrue(testCode.contains("// Guard: payment received"));
        assertTrue(testCode.contains("// Guard: fulfillment dispatched"));

        // Check action comments
        assertTrue(testCode.contains("// Action: Emit PaymentReceivedEvent"));

        // Check guard setup code
        assertTrue(testCode.contains("// Ensure guard condition is met: payment received"));
        assertTrue(testCode.contains("setupGuardCondition(order)"));

        // Check action verification code
        assertTrue(testCode.contains("// Verify action was executed: Emit PaymentReceivedEvent"));
        assertTrue(testCode.contains("verifyActionExecuted()"));
    }

    @Test
    void generateTestsForMultipleStateMachines() {
        var model = new ChronosCompiler().compile("""
                namespace com.example

                entity Order {
                    status: OrderStatus
                }

                entity Payment {
                    status: PaymentStatus
                }

                enum OrderStatus {
                    PENDING
                    COMPLETED
                }

                enum PaymentStatus {
                    UNPAID
                    PAID
                }

                statemachine OrderLifecycle {
                    entity: Order
                    field: status
                    states: [PENDING, COMPLETED]
                    initial: PENDING
                    terminal: [COMPLETED]
                    transitions: [
                        PENDING -> COMPLETED
                    ]
                }

                statemachine PaymentLifecycle {
                    entity: Payment
                    field: status
                    states: [UNPAID, PAID]
                    initial: UNPAID
                    terminal: [PAID]
                    transitions: [
                        UNPAID -> PAID
                    ]
                }
                """, "test").modelOrNull();

        GeneratorOutput output = new StateMachineTestGenerator().generate(model);
        String testCode = output.content();

        // Check both state machines are included
        assertTrue(testCode.contains("OrderLifecycle"));
        assertTrue(testCode.contains("PaymentLifecycle"));

        // Check tests for both
        assertTrue(testCode.contains("testOrderLifecycle_InitialState"));
        assertTrue(testCode.contains("testPaymentLifecycle_InitialState"));

        assertTrue(testCode.contains("testOrderLifecycle_PENDINGToCOMPLETED"));
        assertTrue(testCode.contains("testPaymentLifecycle_UNPAIDToPAID"));
    }
}
