package com.genairus.chronos.validator;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-051: @timeout onExpiry references a variant not declared in the journey.
 */
class Chr051TimeoutOnExpiryTest {

    private static boolean hasChr051(com.genairus.chronos.compiler.CompileResult result) {
        return result.diagnostics().stream().anyMatch(d -> "CHR-051".equals(d.code()));
    }

    // ── Valid: onExpiry matches declared variant ────────────────────────────────

    @Test
    void onExpiry_matchesDeclaredVariant_valid() {
        var result = new ChronosCompiler().compile("""
                namespace com.example
                actor Customer
                error TimeoutError { code: "TO-001" severity: high recoverable: false message: "Timed out" }
                @timeout(duration: 5m, onExpiry: Timeout)
                journey PlaceOrder {
                    actor: Customer
                    variants: {
                        Timeout: {
                            trigger: TimeoutError
                        }
                    }
                    outcomes: { success: "Done" }
                }
                """, "<test>");
        assertFalse(hasChr051(result),
                "onExpiry matching declared variant should not trigger CHR-051; got: " + result.diagnostics());
    }

    @Test
    void timeout_withoutOnExpiry_valid() {
        var result = new ChronosCompiler().compile("""
                namespace com.example
                actor Customer
                @timeout(duration: 5m)
                journey PlaceOrder {
                    actor: Customer
                    outcomes: { success: "Done" }
                }
                """, "<test>");
        assertFalse(hasChr051(result),
                "@timeout without onExpiry should not trigger CHR-051; got: " + result.diagnostics());
    }

    @Test
    void timeout_onStep_noVariants_valid() {
        var result = new ChronosCompiler().compile("""
                namespace com.example
                actor Customer
                journey PlaceOrder {
                    actor: Customer
                    steps: [
                        @timeout(duration: 5m)
                        step Pay {
                            action: "Pay"
                            expectation: "Accepted"
                        }
                    ]
                    outcomes: { success: "Done" }
                }
                """, "<test>");
        assertFalse(hasChr051(result),
                "@timeout on step should not trigger CHR-051; got: " + result.diagnostics());
    }

    // ── Invalid: onExpiry references non-existent variant ─────────────────────

    @Test
    void onExpiry_nonExistentVariant_triggersChr051() {
        var result = new ChronosCompiler().compile("""
                namespace com.example
                actor Customer
                @timeout(duration: 5m, onExpiry: Missing)
                journey PlaceOrder {
                    actor: Customer
                    outcomes: { success: "Done" }
                }
                """, "<test>");
        assertTrue(hasChr051(result),
                "onExpiry referencing non-existent variant should trigger CHR-051; got: " + result.diagnostics());
        assertTrue(result.diagnostics().stream()
                .filter(d -> "CHR-051".equals(d.code()))
                .anyMatch(d -> d.message().contains("Missing")),
                "CHR-051 message should contain the bad variant name");
    }

    @Test
    void onExpiry_wrongVariantName_withTwoVariants_triggersChr051() {
        var result = new ChronosCompiler().compile("""
                namespace com.example
                actor Customer
                error PayError { code: "P-001" severity: high recoverable: false message: "Pay failed" }
                error NetError { code: "N-001" severity: high recoverable: false message: "Network" }
                @timeout(duration: 5m, onExpiry: WrongName)
                journey PlaceOrder {
                    actor: Customer
                    variants: {
                        PaymentFailed: { trigger: PayError },
                        NetworkError:  { trigger: NetError }
                    }
                    outcomes: { success: "Done" }
                }
                """, "<test>");
        assertTrue(hasChr051(result),
                "onExpiry with wrong name should trigger CHR-051; got: " + result.diagnostics());
        assertTrue(result.diagnostics().stream()
                .filter(d -> "CHR-051".equals(d.code()))
                .anyMatch(d -> d.message().contains("WrongName")),
                "CHR-051 message should contain 'WrongName'");
    }
}
