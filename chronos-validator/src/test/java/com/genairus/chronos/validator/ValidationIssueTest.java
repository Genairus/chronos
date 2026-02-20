package com.genairus.chronos.validator;

import com.genairus.chronos.core.diagnostics.Diagnostic;
import com.genairus.chronos.core.diagnostics.DiagnosticSeverity;
import com.genairus.chronos.core.refs.Span;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Diagnostic} formatting and accessors (formerly ValidationIssueTest).
 */
class ValidationIssueTest {

    private static final Span SPAN = Span.at("checkout.chronos", 12, 1);

    @Test
    void errorDiagnosticFormatted() {
        var d = Diagnostic.error("CHR-001",
                "Journey 'GuestCheckout' must declare an actor", SPAN);
        assertEquals("ERROR [CHR-001] checkout.chronos:12:1  Journey 'GuestCheckout' must declare an actor",
                d.toString());
    }

    @Test
    void warningDiagnosticFormatted() {
        var d = Diagnostic.warning("CHR-009",
                "Journey 'GuestCheckout' is missing a @kpi trait", SPAN);
        assertEquals("WARNING [CHR-009] checkout.chronos:12:1  Journey 'GuestCheckout' is missing a @kpi trait",
                d.toString());
    }

    @Test
    void accessorsReturnConstructedValues() {
        var d = Diagnostic.error("CHR-005", "Duplicate shape name 'Order'", SPAN);
        assertEquals("CHR-005", d.code());
        assertEquals(DiagnosticSeverity.ERROR, d.severity());
        assertEquals("Duplicate shape name 'Order'", d.message());
        assertEquals(SPAN, d.span());
        assertNull(d.pathOrNull());
    }
}
