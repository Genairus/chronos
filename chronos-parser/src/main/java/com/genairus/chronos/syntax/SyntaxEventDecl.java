package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * Syntax DTO for a top-level {@code event} declaration.
 *
 * <pre>
 *   event CartReviewed {
 *       cartId: String
 *       itemCount: Integer
 *   }
 * </pre>
 *
 * <p>The payload fields use the same {@link SyntaxFieldDef} structure as
 * {@code entity} and {@code shape} declarations.
 */
public record SyntaxEventDecl(
        String name,
        List<String> docComments,
        List<SyntaxFieldDef> fields,
        List<SyntaxTrait> traits,
        Span span
) implements SyntaxDecl {}
