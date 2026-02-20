package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

/**
 * A state machine transition (grammar rule: {@code transition}).
 *
 * <p>Example: {@code PENDING -> PAID { guard: "payment.status == APPROVED" }}
 * <p>Both {@code guardOrNull} and {@code actionOrNull} are absent when not specified.
 */
public record SyntaxTransition(
        String fromState,
        String toState,
        String guardOrNull,
        String actionOrNull,
        Span span
) {}
