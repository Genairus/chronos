package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * A shape (value object) declaration (grammar rule: {@code shapeStructDef}).
 *
 * <p>Example:
 * <pre>{@code
 * shape Money {
 *     amount: Float
 *     currency: String
 * }
 * }</pre>
 */
public record SyntaxShapeDecl(
        String name,
        List<String> docComments,
        List<SyntaxFieldDef> fields,
        List<SyntaxTrait> traits,
        Span span
) implements SyntaxDecl {}
