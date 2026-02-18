package com.genairus.chronos.model;

import java.util.OptionalInt;

/**
 * A single member of an {@link EnumDef}.
 *
 * <pre>
 *   PENDING = 1
 *   PAID    = 2
 *   SHIPPED           ← no ordinal
 * </pre>
 *
 * @param name     the member name (UPPER_SNAKE_CASE by convention)
 * @param ordinal  the explicit integer ordinal, or empty if omitted
 * @param location source location of the member name token
 */
public record EnumMember(String name, OptionalInt ordinal, SourceLocation location) {

    /** Convenience constructor for members without an explicit ordinal. */
    public static EnumMember of(String name, SourceLocation location) {
        return new EnumMember(name, OptionalInt.empty(), location);
    }

    /** Convenience constructor for members with an explicit ordinal. */
    public static EnumMember of(String name, int ordinal, SourceLocation location) {
        return new EnumMember(name, OptionalInt.of(ordinal), location);
    }
}
