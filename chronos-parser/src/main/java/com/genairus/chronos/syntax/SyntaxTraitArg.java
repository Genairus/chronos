package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

/**
 * A single argument in a trait application (grammar rule: {@code traitArg}).
 *
 * <p>Named form: {@code metric: "CheckoutConversion"} — {@code keyOrNull} is {@code "metric"}.
 * Positional form: {@code "text"} — {@code keyOrNull} is {@code null}.
 */
public record SyntaxTraitArg(
        String keyOrNull,
        SyntaxTraitValue value,
        Span span
) {}
