package com.genairus.chronos.compiler;

import com.genairus.chronos.core.diagnostics.Diagnostic;
import com.genairus.chronos.core.diagnostics.DiagnosticSeverity;

import java.util.List;

/**
 * The result of a {@link ChronosCompiler#compileAll} call over multiple source files.
 *
 * <p>{@link #parsed()} is {@code true} only if every source unit parsed without
 * fatal syntax errors. {@link #finalized()} is {@code true} only if every source
 * unit finalized without errors. {@link #unitOrNull()} is non-null only when
 * {@link #parsed()} is {@code true}.
 *
 * <p>Diagnostics from all source units are aggregated in the order the units were
 * compiled.
 *
 * @param unitOrNull  the compiled unit containing all models, or {@code null} if
 *                    any source unit failed to parse
 * @param diagnostics all diagnostics from every source unit, in compilation order
 * @param parsed      {@code true} if every source unit parsed successfully
 * @param finalized   {@code true} if every source unit finalized without errors
 */
public record CompileAllResult(
        IrCompilationUnit unitOrNull,
        List<Diagnostic> diagnostics,
        boolean parsed,
        boolean finalized) {

    /**
     * Returns {@code true} if all source units compiled with no error diagnostics.
     * Implies both {@link #parsed()} and {@link #finalized()} are {@code true}.
     */
    public boolean success() {
        return parsed && finalized
                && diagnostics.stream()
                        .noneMatch(d -> d.severity() == DiagnosticSeverity.ERROR);
    }

    /** Returns all error-severity diagnostics across all source units. */
    public List<Diagnostic> errors() {
        return diagnostics.stream()
                .filter(d -> d.severity() == DiagnosticSeverity.ERROR)
                .toList();
    }

    /** Returns all warning-severity diagnostics across all source units. */
    public List<Diagnostic> warnings() {
        return diagnostics.stream()
                .filter(d -> d.severity() == DiagnosticSeverity.WARNING)
                .toList();
    }
}
