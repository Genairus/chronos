package com.genairus.chronos.core.diagnostics;

import com.genairus.chronos.core.refs.Span;

/**
 * A single compiler or validator diagnostic: a code, severity, message,
 * source location, and optional file path.
 *
 * <p>Use the factory methods {@link #error}, {@link #warning}, and
 * {@link #info} to construct diagnostics without a file path override.
 *
 * @param code        CHR-xxx diagnostic code (e.g. {@code "CHR-008"})
 * @param severity    whether this is an error, warning, or informational message
 * @param message     human-readable explanation
 * @param span        source location; {@link Span#UNKNOWN} when not available
 * @param pathOrNull  optional file path that supplements or overrides the span's path;
 *                    {@code null} when no override is needed
 */
public record Diagnostic(
        String code,
        DiagnosticSeverity severity,
        String message,
        Span span,
        String pathOrNull) {

    // ── Factory methods ───────────────────────────────────────────────────────

    public static Diagnostic error(String code, String message, Span span) {
        return new Diagnostic(code, DiagnosticSeverity.ERROR, message, span, null);
    }

    public static Diagnostic warning(String code, String message, Span span) {
        return new Diagnostic(code, DiagnosticSeverity.WARNING, message, span, null);
    }

    public static Diagnostic info(String code, String message, Span span) {
        return new Diagnostic(code, DiagnosticSeverity.INFO, message, span, null);
    }

    // ── Display ───────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        String path = (pathOrNull != null) ? pathOrNull : span.toString();
        return severity + " [" + code + "] " + path + "  " + message;
    }
}
