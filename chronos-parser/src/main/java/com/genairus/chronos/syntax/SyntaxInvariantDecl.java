package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * A top-level (global) invariant declaration (grammar rule: {@code invariantDef}).
 *
 * <p>Example:
 * <pre>{@code
 * invariant ActiveOrderLimit {
 *     scope: [Customer, Order]
 *     expression: "count(customer.orders, o => o.status == PENDING) <= 10"
 *     severity: warning
 *     message: "Customer should not exceed 10 pending orders"
 * }
 * }</pre>
 *
 * <p>All string fields default to empty string when absent in source.
 * {@code messageOrNull} is {@code null} when the {@code message} field is not present.
 */
public record SyntaxInvariantDecl(
        String name,
        List<String> docComments,
        List<String> scope,
        String expression,
        String severity,
        String messageOrNull,
        List<SyntaxTrait> traits,
        Span span
) implements SyntaxDecl {}
