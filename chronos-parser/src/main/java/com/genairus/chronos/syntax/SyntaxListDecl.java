package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * A named list type declaration (grammar rule: {@code listDef}).
 *
 * <p>Example: {@code list OrderItemList { member: OrderItem }}
 */
public record SyntaxListDecl(
        String name,
        List<String> docComments,
        SyntaxTypeRef memberType,
        List<SyntaxTrait> traits,
        Span span
) implements SyntaxDecl {}
