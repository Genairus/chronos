package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

/**
 * A member of an enum declaration (grammar rule: {@code enumMember}).
 *
 * <p>Example: {@code PENDING = 1} — name {@code "PENDING"}, ordinal {@code 1}.
 * Example: {@code PAID} — name {@code "PAID"}, ordinal {@code null}.
 */
public record SyntaxEnumMember(
        String name,
        Integer ordinalOrNull,
        Span span
) {}
