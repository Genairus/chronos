package com.genairus.chronos.compiler;

import com.genairus.chronos.core.diagnostics.Diagnostic;
import com.genairus.chronos.core.diagnostics.DiagnosticSeverity;
import com.genairus.chronos.ir.model.IrModel;

import java.util.List;

/**
 * The result of a {@link ChronosCompiler#compile} call.
 *
 * @param modelOrNull   the fully compiled IR model, or {@code null} if the source
 *                      could not be parsed (i.e. {@link #parsed()} is {@code false})
 * @param diagnostics   all accumulated diagnostics from every phase, in order
 * @param parsed        {@code true} if Pass 0+1 succeeded (syntax was valid)
 * @param finalized     {@code true} if the IR is complete and all cross-references
 *                      were verified without errors
 */
public record CompileResult(
        IrModel modelOrNull,
        List<Diagnostic> diagnostics,
        boolean parsed,
        boolean finalized) {

    /**
     * Returns {@code true} if compilation succeeded with no error diagnostics.
     * A successful compilation implies both {@link #parsed()} and
     * {@link #finalized()} are {@code true}.
     */
    public boolean success() {
        return parsed && finalized
                && diagnostics.stream()
                        .noneMatch(d -> d.severity() == DiagnosticSeverity.ERROR);
    }

    /** Returns all error-severity diagnostics. */
    public List<Diagnostic> errors() {
        return diagnostics.stream()
                .filter(d -> d.severity() == DiagnosticSeverity.ERROR)
                .toList();
    }

    /** Returns all warning-severity diagnostics. */
    public List<Diagnostic> warnings() {
        return diagnostics.stream()
                .filter(d -> d.severity() == DiagnosticSeverity.WARNING)
                .toList();
    }
}
