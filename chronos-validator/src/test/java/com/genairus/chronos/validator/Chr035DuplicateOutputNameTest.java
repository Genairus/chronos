package com.genairus.chronos.validator;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-035: Output field names must be unique across all steps in a journey scope.
 */
class Chr035DuplicateOutputNameTest {

    private static final String PREAMBLE = """
            namespace com.example
            actor Customer
            """;

    /** Two different steps each produce the same output field name — CHR-035 error. */
    @Test
    void duplicateOutputNameAcrossStepsTriggersChr035() {
        var model = new ChronosCompiler().compile(PREAMBLE + """
                journey Checkout {
                    actor: Customer
                    steps: [
                        step CollectPayment {
                            action: "Collect payment details"
                            expectation: "Payment captured"
                            output: [amount: Float]
                        },
                        step RefundPayment {
                            action: "Refund the amount"
                            expectation: "Payment refunded"
                            output: [amount: Float]
                        }
                    ]
                    outcomes: { success: "Done" }
                }
                """, "test").modelOrNull();

        var result = new ChronosValidator().validate(model);
        assertTrue(result.hasErrors(), "Duplicate output names should trigger CHR-035");
        assertTrue(result.errors().stream().anyMatch(d -> "CHR-035".equals(d.code())),
                "Expected CHR-035; got: " + result.errors());
        assertTrue(result.errors().stream()
                .filter(d -> "CHR-035".equals(d.code()))
                .anyMatch(d -> d.message().contains("amount")),
                "CHR-035 message should mention the duplicate field name 'amount'");
    }

    /** Each step uses distinct output field names — no CHR-035 error. */
    @Test
    void uniqueOutputNamesPerStepPassValidation() {
        var model = new ChronosCompiler().compile(PREAMBLE + """
                journey Checkout {
                    actor: Customer
                    steps: [
                        step CollectPayment {
                            action: "Collect payment details"
                            expectation: "Payment captured"
                            output: [paymentId: String]
                        },
                        step SendReceipt {
                            action: "Send receipt"
                            expectation: "Email sent"
                            output: [receiptUrl: String]
                        }
                    ]
                    outcomes: { success: "Done" }
                }
                """, "test").modelOrNull();

        var result = new ChronosValidator().validate(model);
        assertFalse(result.errors().stream().anyMatch(d -> "CHR-035".equals(d.code())),
                "Unique output names should not trigger CHR-035; got: " + result.errors());
    }

    /** Duplicate output names in a variant scope also trigger CHR-035. */
    @Test
    void duplicateOutputNamesInVariantTriggersChr035() {
        var model = new ChronosCompiler().compile(PREAMBLE + """
                error PaymentError {
                    code: "PAYMENT_FAILED"
                    severity: high
                }

                journey Checkout {
                    actor: Customer
                    steps: [
                        step Pay {
                            action: "Pay"
                            expectation: "Receipt issued"
                        }
                    ]
                    variants: {
                        Declined: {
                            trigger: PaymentError
                            steps: [
                                step Notify {
                                    action: "Notify user"
                                    expectation: "Error shown"
                                    output: [errorMsg: String]
                                },
                                step Retry {
                                    action: "Retry payment"
                                    expectation: "Payment attempted"
                                    output: [errorMsg: String]
                                }
                            ]
                        }
                    }
                    outcomes: { success: "Done" }
                }
                """, "test").modelOrNull();

        var result = new ChronosValidator().validate(model);
        assertTrue(result.errors().stream().anyMatch(d -> "CHR-035".equals(d.code())),
                "Duplicate output names in variant should trigger CHR-035; got: " + result.errors());
    }
}
