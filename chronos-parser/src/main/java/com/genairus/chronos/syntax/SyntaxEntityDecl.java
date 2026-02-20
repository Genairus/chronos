package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * An entity declaration (grammar rule: {@code entityDef}).
 *
 * <p>Example:
 * <pre>{@code
 * @pii
 * entity Order extends BaseEntity {
 *     id: String
 *     invariant TotalPositive { expression: "total > 0" severity: error }
 * }
 * }</pre>
 *
 * {@code parentOrNull} is {@code null} when no {@code extends} clause is present.
 */
public record SyntaxEntityDecl(
        String name,
        List<String> docComments,
        String parentOrNull,
        List<SyntaxFieldDef> fields,
        List<SyntaxEntityInvariant> invariants,
        List<SyntaxTrait> traits,
        Span span
) implements SyntaxDecl {}
