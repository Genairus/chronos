package com.genairus.chronos.validator;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-050: @timeout/@ttl duration argument is not a valid duration literal.
 */
class Chr050DurationValidationTest {

    private static boolean hasChr050(com.genairus.chronos.compiler.CompileResult result) {
        return result.diagnostics().stream().anyMatch(d -> "CHR-050".equals(d.code()));
    }

    // ── Valid durations on journey @timeout ────────────────────────────────────

    @Test
    void timeoutWithDurationLiteral_5m_valid() {
        var result = new ChronosCompiler().compile("""
                namespace com.example
                actor Customer
                @timeout(duration: 5m)
                journey PlaceOrder {
                    actor: Customer
                    outcomes: { success: "Done" }
                }
                """, "<test>");
        assertFalse(hasChr050(result), "5m should be valid; got: " + result.diagnostics());
    }

    @Test
    void timeoutWithQuotedDuration_30s_valid() {
        var result = new ChronosCompiler().compile("""
                namespace com.example
                actor Customer
                @timeout(duration: "30s")
                journey PlaceOrder {
                    actor: Customer
                    outcomes: { success: "Done" }
                }
                """, "<test>");
        assertFalse(hasChr050(result), "\"30s\" should be valid; got: " + result.diagnostics());
    }

    @Test
    void timeoutWithPositionalDuration_5m_valid() {
        var result = new ChronosCompiler().compile("""
                namespace com.example
                actor Customer
                @timeout(5m)
                journey PlaceOrder {
                    actor: Customer
                    outcomes: { success: "Done" }
                }
                """, "<test>");
        assertFalse(hasChr050(result), "Positional 5m should be valid; got: " + result.diagnostics());
    }

    // ── Valid durations on step @timeout ──────────────────────────────────────

    @Test
    void stepTimeoutWithDuration_500ms_valid() {
        var result = new ChronosCompiler().compile("""
                namespace com.example
                actor Customer
                journey PlaceOrder {
                    actor: Customer
                    steps: [
                        @timeout(duration: 500ms)
                        step Pay {
                            action: "Pay"
                            expectation: "Accepted"
                        }
                    ]
                    outcomes: { success: "Done" }
                }
                """, "<test>");
        assertFalse(hasChr050(result), "500ms on step should be valid; got: " + result.diagnostics());
    }

    // ── Valid durations on entity @ttl ────────────────────────────────────────

    @Test
    void ttlWithDuration_30d_valid() {
        var result = new ChronosCompiler().compile("""
                namespace com.example
                @ttl(duration: 30d, action: "archive")
                entity Order { id: String }
                """, "<test>");
        assertFalse(hasChr050(result), "30d should be valid; got: " + result.diagnostics());
    }

    // ── Invalid durations → CHR-050 ───────────────────────────────────────────

    @Test
    void timeoutWithBadDuration_5minutes_triggersChr050() {
        var result = new ChronosCompiler().compile("""
                namespace com.example
                actor Customer
                @timeout(duration: "5minutes")
                journey PlaceOrder {
                    actor: Customer
                    outcomes: { success: "Done" }
                }
                """, "<test>");
        assertTrue(hasChr050(result), "\"5minutes\" should trigger CHR-050; got: " + result.diagnostics());
        assertTrue(result.diagnostics().stream()
                .filter(d -> "CHR-050".equals(d.code()))
                .anyMatch(d -> d.message().contains("5minutes")),
                "CHR-050 message should contain '5minutes'");
    }

    @Test
    void ttlWithBadDuration_bad_triggersChr050() {
        var result = new ChronosCompiler().compile("""
                namespace com.example
                @ttl(duration: "bad", action: "delete")
                entity Order { id: String }
                """, "<test>");
        assertTrue(hasChr050(result), "\"bad\" should trigger CHR-050; got: " + result.diagnostics());
    }
}
