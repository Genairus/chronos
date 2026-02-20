package com.genairus.chronos.core.diagnostics;

import com.genairus.chronos.core.refs.Span;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Accumulator for compiler and validator diagnostics (CHR-xxx codes).
 *
 * <p>Diagnostics are appended in the order they are reported and can be
 * retrieved as an immutable snapshot via {@link #all()}.  Convenience methods
 * {@link #error}, {@link #warn}, and {@link #info} cover the three common
 * severity levels; {@link #add} accepts a pre-built {@link Diagnostic} for
 * cases where full control is needed.
 *
 * <p>Usage:
 * <pre>{@code
 *   var collector = new DiagnosticCollector();
 *   collector.error("CHR-008", "Unresolved type 'Foo'", span);
 *   if (collector.hasErrors()) { ... }
 *   List<Diagnostic> all = collector.all();
 * }</pre>
 */
public final class DiagnosticCollector {

    private final List<Diagnostic> diagnostics = new ArrayList<>();

    // ── Mutation ─────────────────────────────────────────────────────────────

    /** Appends a pre-built diagnostic. */
    public void add(Diagnostic diagnostic) {
        diagnostics.add(diagnostic);
    }

    /** Reports an {@link DiagnosticSeverity#ERROR} diagnostic. */
    public void error(String code, String message, Span span) {
        diagnostics.add(Diagnostic.error(code, message, span));
    }

    /** Reports a {@link DiagnosticSeverity#WARNING} diagnostic. */
    public void warn(String code, String message, Span span) {
        diagnostics.add(Diagnostic.warning(code, message, span));
    }

    /** Reports an {@link DiagnosticSeverity#INFO} diagnostic. */
    public void info(String code, String message, Span span) {
        diagnostics.add(Diagnostic.info(code, message, span));
    }

    // ── Inspection ────────────────────────────────────────────────────────────

    /** Returns all accumulated diagnostics as an unmodifiable snapshot. */
    public List<Diagnostic> all() {
        return Collections.unmodifiableList(new ArrayList<>(diagnostics));
    }

    /** Returns {@code true} if any accumulated diagnostic has severity {@link DiagnosticSeverity#ERROR}. */
    public boolean hasErrors() {
        return diagnostics.stream()
                .anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR);
    }
}
