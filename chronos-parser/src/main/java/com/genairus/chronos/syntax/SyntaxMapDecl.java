package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * A named map type declaration (grammar rule: {@code mapDef}).
 *
 * <p>Example: {@code map MetadataMap { key: String value: String }}
 */
public record SyntaxMapDecl(
        String name,
        List<String> docComments,
        SyntaxTypeRef keyType,
        SyntaxTypeRef valueType,
        List<SyntaxTrait> traits,
        Span span
) implements SyntaxDecl {}
