package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * An enum declaration (grammar rule: {@code enumDef}).
 *
 * <p>Example:
 * <pre>{@code
 * enum OrderStatus {
 *     PENDING = 1
 *     PAID    = 2
 * }
 * }</pre>
 */
public record SyntaxEnumDecl(
        String name,
        List<String> docComments,
        List<SyntaxEnumMember> members,
        List<SyntaxTrait> traits,
        Span span
) implements SyntaxDecl {}
