package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

/**
 * An entity-scoped invariant block (grammar rule: {@code entityInvariant}).
 *
 * <p>Declared inline within an entity body. No {@code scope} field at this level —
 * the enclosing entity is the implicit scope.
 *
 * <p>All string fields default to empty string if not present in source;
 * {@code messageOrNull} is {@code null} when not specified.
 */
public record SyntaxEntityInvariant(
        String name,
        String expression,
        String severity,
        String messageOrNull,
        Span span
) {}
