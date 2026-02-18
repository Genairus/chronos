package com.genairus.chronos.validator;

import com.genairus.chronos.model.SourceLocation;

/**
 * A single diagnostic produced by the {@link ChronosValidator}.
 *
 * <p>Rendered as:
 * <pre>
 *   ERROR   [CHR-001] checkout.chronos:12  Journey 'GuestCheckout' must declare an actor
 *   WARNING [CHR-009] checkout.chronos:12  Journey 'GuestCheckout' is missing a @kpi trait
 * </pre>
 *
 * @param ruleCode the rule identifier (e.g. {@code "CHR-001"})
 * @param severity whether this is an error or a warning
 * @param message  human-readable description of the problem
 * @param location the source location of the offending construct
 */
public record ValidationIssue(
        String ruleCode,
        ValidationSeverity severity,
        String message,
        SourceLocation location) {

    @Override
    public String toString() {
        // Pad severity to 7 chars so ERROR and WARNING columns align
        String sev = String.format("%-7s", severity.name());
        return sev + " [" + ruleCode + "] " + location + "  " + message;
    }
}
