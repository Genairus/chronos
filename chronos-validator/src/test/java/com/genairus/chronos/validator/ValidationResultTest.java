package com.genairus.chronos.validator;

import com.genairus.chronos.core.diagnostics.Diagnostic;
import com.genairus.chronos.core.refs.Span;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValidationResultTest {

    private static Diagnostic error(String code) {
        return Diagnostic.error(code, "msg", Span.UNKNOWN);
    }

    private static Diagnostic warning(String code) {
        return Diagnostic.warning(code, "msg", Span.UNKNOWN);
    }

    @Test
    void emptyResultReportsEmpty() {
        var result = new ValidationResult(List.of());
        assertTrue(result.isEmpty());
        assertFalse(result.hasErrors());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void hasErrorsWhenErrorPresent() {
        var result = new ValidationResult(List.of(error("CHR-001"), warning("CHR-009")));
        assertFalse(result.isEmpty());
        assertTrue(result.hasErrors());
        assertEquals(1, result.errors().size());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void noErrorsWhenOnlyWarnings() {
        var result = new ValidationResult(List.of(warning("CHR-004"), warning("CHR-009")));
        assertFalse(result.hasErrors());
        assertFalse(result.isEmpty());
        assertTrue(result.errors().isEmpty());
        assertEquals(2, result.warnings().size());
    }

    @Test
    void diagnosticsPreservedInOrder() {
        var e1 = error("CHR-001");
        var w1 = warning("CHR-009");
        var e2 = error("CHR-003");
        var result = new ValidationResult(List.of(e1, w1, e2));
        assertEquals(List.of(e1, w1, e2), result.diagnostics());
        assertEquals(List.of(e1, e2), result.errors());
        assertEquals(List.of(w1), result.warnings());
    }
}
