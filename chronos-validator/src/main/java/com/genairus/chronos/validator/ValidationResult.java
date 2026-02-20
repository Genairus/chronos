package com.genairus.chronos.validator;

import com.genairus.chronos.core.diagnostics.Diagnostic;
import com.genairus.chronos.core.diagnostics.DiagnosticSeverity;

import java.util.List;

/**
 * The outcome of a {@link ChronosValidator#validate} call — an ordered list of
 * all {@link Diagnostic}s produced, in the order the rules ran.
 *
 * @param diagnostics all diagnostics, errors before warnings within each rule
 */
public record ValidationResult(List<Diagnostic> diagnostics) {

    /** Returns {@code true} when there are no diagnostics at all. */
    public boolean isEmpty() {
        return diagnostics.isEmpty();
    }

    /** Returns {@code true} when at least one diagnostic has severity {@code ERROR}. */
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR);
    }

    /** All diagnostics with severity {@code ERROR}, in encounter order. */
    public List<Diagnostic> errors() {
        return diagnostics.stream()
                .filter(d -> d.severity() == DiagnosticSeverity.ERROR)
                .toList();
    }

    /** All diagnostics with severity {@code WARNING}, in encounter order. */
    public List<Diagnostic> warnings() {
        return diagnostics.stream()
                .filter(d -> d.severity() == DiagnosticSeverity.WARNING)
                .toList();
    }
}
