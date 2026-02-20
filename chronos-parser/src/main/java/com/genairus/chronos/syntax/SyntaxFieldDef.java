package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * A field definition inside an entity, shape, or error payload
 * (grammar rule: {@code fieldDef}).
 *
 * <p>Example: {@code @required id: String}
 */
public record SyntaxFieldDef(
        String name,
        SyntaxTypeRef type,
        List<SyntaxTrait> traits,
        Span span
) {}
