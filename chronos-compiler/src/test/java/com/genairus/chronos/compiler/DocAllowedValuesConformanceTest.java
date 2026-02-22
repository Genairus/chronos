package com.genairus.chronos.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Conformance tests that pin the canonical allowed values for every enum-like
 * field documented in the Chronos quick-reference and language guides.
 *
 * <h2>Purpose</h2>
 * <p>These tests act as "failing fixtures" — they confirm that <em>invalid</em> values
 * documented in error produce the expected diagnostic, and that <em>valid</em> values
 * do not.  Any deviation between what the docs say is legal and what the compiler
 * actually accepts will surface here in CI.
 *
 * <h2>Covered fields</h2>
 * <ul>
 *   <li>{@code invariant.severity} — valid: {@code error}, {@code warning}, {@code info}
 *       (CHR-020 rejects anything else, including {@code critical})</li>
 *   <li>{@code error.severity} — valid: {@code critical}, {@code high}, {@code medium},
 *       {@code low} (CHR-028 rejects anything else)</li>
 *   <li>{@code deny.severity} — valid: {@code critical}, {@code high}, {@code medium},
 *       {@code low} (CHR-025 rejects anything else)</li>
 * </ul>
 */
class DocAllowedValuesConformanceTest {

    // ── invariant.severity ────────────────────────────────────────────────────

    @Test
    void invariantSeverity_error_isValid() {
        var result = compile("""
                namespace doc.test
                entity Foo { id: String }
                invariant NoFoo {
                    scope: [Foo]
                    expression: "id != null"
                    severity: error
                    message: "id must not be null"
                }
                """);
        assertNoChr020(result);
    }

    @Test
    void invariantSeverity_warning_isValid() {
        var result = compile("""
                namespace doc.test
                entity Foo { id: String }
                invariant NoFoo {
                    scope: [Foo]
                    expression: "id != null"
                    severity: warning
                    message: "id must not be null"
                }
                """);
        assertNoChr020(result);
    }

    @Test
    void invariantSeverity_info_isValid() {
        var result = compile("""
                namespace doc.test
                entity Foo { id: String }
                invariant NoFoo {
                    scope: [Foo]
                    expression: "id != null"
                    severity: info
                    message: "id must not be null"
                }
                """);
        assertNoChr020(result);
    }

    /**
     * Failing fixture: {@code critical} is NOT a valid invariant severity.
     * <p>The quick-reference.md previously listed {@code critical} here in error —
     * this test ensures that regression never silently re-enters.
     * {@code CHR-020} must fire.
     */
    @Test
    void invariantSeverity_critical_isRejected_chr020() {
        var result = compile("""
                namespace doc.test
                entity Foo { id: String }
                invariant NoFoo {
                    scope: [Foo]
                    expression: "id != null"
                    severity: critical
                    message: "id must not be null"
                }
                """);
        assertTrue(hasDiagnostic(result, "CHR-020"),
                "severity: critical in invariant must be rejected with CHR-020; got: "
                        + result.diagnostics());
    }

    // ── error.severity ────────────────────────────────────────────────────────

    @Test
    void errorSeverity_critical_isValid() {
        var result = compile("""
                namespace doc.test
                error PaymentFailed {
                    code: "PAYMENT_FAILED"
                    severity: critical
                    recoverable: false
                    message: "Payment gateway rejected the request"
                }
                """);
        assertNoChr028(result);
    }

    @Test
    void errorSeverity_high_isValid() {
        var result = compile("""
                namespace doc.test
                error PaymentFailed {
                    code: "PAYMENT_FAILED"
                    severity: high
                    recoverable: false
                    message: "Payment gateway rejected the request"
                }
                """);
        assertNoChr028(result);
    }

    @Test
    void errorSeverity_medium_isValid() {
        var result = compile("""
                namespace doc.test
                error PaymentFailed {
                    code: "PAYMENT_FAILED"
                    severity: medium
                    recoverable: true
                    message: "Payment gateway rejected the request"
                }
                """);
        assertNoChr028(result);
    }

    @Test
    void errorSeverity_low_isValid() {
        var result = compile("""
                namespace doc.test
                error PaymentFailed {
                    code: "PAYMENT_FAILED"
                    severity: low
                    recoverable: true
                    message: "Payment gateway rejected the request"
                }
                """);
        assertNoChr028(result);
    }

    @Test
    void errorSeverity_warning_isRejected_chr028() {
        var result = compile("""
                namespace doc.test
                error PaymentFailed {
                    code: "PAYMENT_FAILED"
                    severity: warning
                    recoverable: true
                    message: "Payment gateway rejected the request"
                }
                """);
        assertTrue(hasDiagnostic(result, "CHR-028"),
                "severity: warning in error block must be rejected with CHR-028; got: "
                        + result.diagnostics());
    }

    // ── deny.severity ─────────────────────────────────────────────────────────

    @Test
    void denySeverity_critical_isValid() {
        var result = compile("""
                namespace doc.test
                entity UserCredential { id: String }
                deny NoPlaintext {
                    description: "Must not store plaintext passwords"
                    scope: [UserCredential]
                    severity: critical
                }
                """);
        assertNoChr025(result);
    }

    @Test
    void denySeverity_high_isValid() {
        var result = compile("""
                namespace doc.test
                entity UserCredential { id: String }
                deny NoPlaintext {
                    description: "Must not store plaintext passwords"
                    scope: [UserCredential]
                    severity: high
                }
                """);
        assertNoChr025(result);
    }

    @Test
    void denySeverity_medium_isValid() {
        var result = compile("""
                namespace doc.test
                entity UserCredential { id: String }
                deny NoPlaintext {
                    description: "Must not store plaintext passwords"
                    scope: [UserCredential]
                    severity: medium
                }
                """);
        assertNoChr025(result);
    }

    @Test
    void denySeverity_low_isValid() {
        var result = compile("""
                namespace doc.test
                entity UserCredential { id: String }
                deny NoPlaintext {
                    description: "Must not store plaintext passwords"
                    scope: [UserCredential]
                    severity: low
                }
                """);
        assertNoChr025(result);
    }

    @Test
    void denySeverity_info_isRejected_chr025() {
        // 'info' is a valid invariant severity but NOT a valid deny severity.
        // CHR-025 must fire, distinguishing the two severity enumerations.
        var result = compile("""
                namespace doc.test
                entity UserCredential { id: String }
                deny NoPlaintext {
                    description: "Must not store plaintext passwords"
                    scope: [UserCredential]
                    severity: info
                }
                """);
        assertTrue(hasDiagnostic(result, "CHR-025"),
                "severity: info in deny block must be rejected with CHR-025; got: "
                        + result.diagnostics());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static CompileResult compile(String src) {
        return new ChronosCompiler().compile(src, "doc-conformance-test.chronos");
    }

    private static boolean hasDiagnostic(CompileResult result, String code) {
        return result.diagnostics().stream().anyMatch(d -> code.equals(d.code()));
    }

    private static void assertNoChr020(CompileResult result) {
        assertFalse(hasDiagnostic(result, "CHR-020"),
                "Valid invariant severity must not produce CHR-020; got: " + result.diagnostics());
    }

    private static void assertNoChr028(CompileResult result) {
        assertFalse(hasDiagnostic(result, "CHR-028"),
                "Valid error severity must not produce CHR-028; got: " + result.diagnostics());
    }

    private static void assertNoChr025(CompileResult result) {
        assertFalse(hasDiagnostic(result, "CHR-025"),
                "Valid deny severity must not produce CHR-025; got: " + result.diagnostics());
    }
}
