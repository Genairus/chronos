package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

/**
 * Terminal outcome descriptions for a journey (grammar rule: {@code outcomesDecl}).
 *
 * <p>Either {@code success} or {@code failure} may be absent ({@code null})
 * if only one side is declared in source.
 */
public record SyntaxOutcomes(
        String successOrNull,
        String failureOrNull,
        Span span
) {}
