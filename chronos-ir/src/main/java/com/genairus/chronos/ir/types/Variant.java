package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;

import java.util.List;
import java.util.Optional;

/**
 * A named alternative or error-flow branch within an IR journey.
 *
 * @param name          the variant name (PascalCase)
 * @param triggerName   the raw error-type name (not yet cross-linked)
 * @param steps         the ordered steps in this variant branch
 * @param outcomeOrNull the terminal outcome expression, or {@code null} if absent
 * @param span          source location of the variant name token
 */
public record Variant(
        String name,
        String triggerName,
        List<Step> steps,
        OutcomeExpr outcomeOrNull,
        Span span) {

    /** Returns the outcome expression, or empty if absent. */
    public Optional<OutcomeExpr> outcome() {
        return Optional.ofNullable(outcomeOrNull);
    }
}
