package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * A named variant entry within a journey's {@code variants} block
 * (grammar rules: {@code variantEntry} / {@code variantBody}).
 *
 * <p>{@code triggerName} is the raw identifier referencing an error type — not yet resolved.
 * {@code outcomeOrNull} is absent when the variant body omits an {@code outcome} clause.
 */
public record SyntaxVariant(
        String name,
        String triggerName,
        List<SyntaxStep> steps,
        SyntaxOutcomeExpr outcomeOrNull,
        Span span
) {}
