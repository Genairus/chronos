package com.genairus.chronos.validator;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-036: Step input field must be produced as output by a preceding step.
 */
class Chr036InputAvailabilityTest {

    private static final String PREAMBLE = """
            namespace com.example
            actor Customer
            """;

    /** Input field produced by the immediately preceding step — valid. */
    @Test
    void inputAvailableFromUpstreamStepPassesValidation() {
        var model = new ChronosCompiler().compile(PREAMBLE + """
                journey Checkout {
                    actor: Customer
                    steps: [
                        step CollectPayment {
                            action: "User submits payment"
                            expectation: "Payment captured"
                            output: [transactionId: String]
                        },
                        step SendConfirmation {
                            action: "Send receipt"
                            expectation: "Email delivered"
                            input: [transactionId: String]
                        }
                    ]
                    outcomes: { success: "Confirmed" }
                }
                """, "test").modelOrNull();

        var result = new ChronosValidator().validate(model);
        assertFalse(result.errors().stream().anyMatch(d -> "CHR-036".equals(d.code())),
                "Input available from upstream should not trigger CHR-036; got: " + result.errors());
    }

    /** Input field not produced by any preceding step — CHR-036 error. */
    @Test
    void inputNotProducedByUpstreamTriggersChr036() {
        var model = new ChronosCompiler().compile(PREAMBLE + """
                journey Checkout {
                    actor: Customer
                    steps: [
                        step CollectPayment {
                            action: "User submits payment"
                            expectation: "Payment captured"
                        },
                        step SendConfirmation {
                            action: "Send receipt"
                            expectation: "Email delivered"
                            input: [transactionId: String]
                        }
                    ]
                    outcomes: { success: "Confirmed" }
                }
                """, "test").modelOrNull();

        var result = new ChronosValidator().validate(model);
        assertTrue(result.errors().stream().anyMatch(d -> "CHR-036".equals(d.code())),
                "Missing upstream output should trigger CHR-036; got: " + result.errors());
        assertTrue(result.errors().stream()
                .filter(d -> "CHR-036".equals(d.code()))
                .anyMatch(d -> d.message().contains("transactionId")),
                "CHR-036 message should mention the missing field 'transactionId'");
        assertTrue(result.errors().stream()
                .filter(d -> "CHR-036".equals(d.code()))
                .anyMatch(d -> d.message().contains("SendConfirmation")),
                "CHR-036 message should mention the step 'SendConfirmation'");
    }

    /** A step cannot consume its own outputs in the same step. */
    @Test
    void stepCannotConsumeItsOwnOutputInSameStep() {
        var model = new ChronosCompiler().compile(PREAMBLE + """
                journey Checkout {
                    actor: Customer
                    steps: [
                        step Compute {
                            action: "Compute result"
                            expectation: "Result ready"
                            input: [result: Float]
                            output: [result: Float]
                        }
                    ]
                    outcomes: { success: "Done" }
                }
                """, "test").modelOrNull();

        var result = new ChronosValidator().validate(model);
        assertTrue(result.errors().stream().anyMatch(d -> "CHR-036".equals(d.code())),
                "Step consuming its own output should trigger CHR-036; got: " + result.errors());
    }

    /** Multi-step chain: each step's input is produced by the step before it. */
    @Test
    void multiStepChainWithDataFlowPassesValidation() {
        var model = new ChronosCompiler().compile(PREAMBLE + """
                journey OrderFlow {
                    actor: Customer
                    steps: [
                        step CreateCart {
                            action: "Create shopping cart"
                            expectation: "Cart created"
                            output: [cartId: String]
                        },
                        step AddItem {
                            action: "Add item to cart"
                            expectation: "Item added"
                            input: [cartId: String]
                            output: [cartId: String, itemCount: Integer]
                        },
                        step Checkout {
                            action: "Proceed to checkout"
                            expectation: "Order created"
                            input: [cartId: String, itemCount: Integer]
                            output: [orderId: String]
                        }
                    ]
                    outcomes: { success: "Order placed" }
                }
                """, "test").modelOrNull();

        var result = new ChronosValidator().validate(model);
        assertFalse(result.errors().stream().anyMatch(d -> "CHR-036".equals(d.code())),
                "Valid multi-step data flow should not trigger CHR-036; got: " + result.errors());
    }

    /** Input in a variant step referencing an undeclared output triggers CHR-036. */
    @Test
    void inputUnavailableInVariantTriggersChr036() {
        var model = new ChronosCompiler().compile(PREAMBLE + """
                error PaymentError {
                    code: "PAYMENT_FAILED"
                    severity: high
                }

                journey Checkout {
                    actor: Customer
                    steps: [
                        step Pay {
                            action: "Submit payment"
                            expectation: "Payment accepted"
                        }
                    ]
                    variants: {
                        Declined: {
                            trigger: PaymentError
                            steps: [
                                step Retry {
                                    action: "Retry payment"
                                    expectation: "Payment attempted"
                                    input: [previousAttemptId: String]
                                }
                            ]
                        }
                    }
                    outcomes: { success: "Done" }
                }
                """, "test").modelOrNull();

        var result = new ChronosValidator().validate(model);
        assertTrue(result.errors().stream().anyMatch(d -> "CHR-036".equals(d.code())),
                "Unavailable input in variant should trigger CHR-036; got: " + result.errors());
    }
}
