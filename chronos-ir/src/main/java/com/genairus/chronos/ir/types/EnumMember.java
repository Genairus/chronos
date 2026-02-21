package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;

/**
 * A member of an IR {@link EnumDef}.
 *
 * @param name          the member identifier
 * @param ordinalOrNull explicit numeric ordinal, or {@code null} if auto-assigned
 * @param span          source location
 */
public record EnumMember(String name, Integer ordinalOrNull, Span span) {

    public static EnumMember of(String name, Integer ordinalOrNull, Span span) {
        return new EnumMember(name, ordinalOrNull, span);
    }

    public static EnumMember of(String name, Span span) {
        return new EnumMember(name, null, span);
    }
}
