package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * A relationship declaration (grammar rule: {@code relationshipDef}).
 *
 * <p>Example:
 * <pre>{@code
 * relationship OrderItems {
 *     from: Order
 *     to: OrderItem
 *     cardinality: one_to_many
 *     semantics: composition
 *     inverse: order
 * }
 * }</pre>
 *
 * <p>{@code cardinality} is the raw token text ({@code one_to_one}, {@code one_to_many},
 * {@code many_to_many}). {@code semanticsOrNull} and {@code inverseOrNull} may be absent.
 */
public record SyntaxRelationshipDecl(
        String name,
        List<String> docComments,
        String fromEntity,
        String toEntity,
        String cardinality,
        String semanticsOrNull,
        String inverseOrNull,
        List<SyntaxTrait> traits,
        Span span
) implements SyntaxDecl {}
