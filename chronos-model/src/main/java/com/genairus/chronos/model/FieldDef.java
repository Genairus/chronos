package com.genairus.chronos.model;

import java.util.List;

/**
 * A single field declaration inside an {@link EntityDef} or {@link ShapeStructDef}.
 *
 * <pre>
 *   @required
 *   id: String
 * </pre>
 *
 * @param name     the field name (camelCase by convention)
 * @param type     the field's type reference
 * @param traits   trait applications on this field (e.g. {@code @required}, {@code @pattern})
 * @param location source location of the field name token
 */
public record FieldDef(
        String name,
        TypeRef type,
        List<TraitApplication> traits,
        SourceLocation location) {

    /** Returns {@code true} if this field carries a {@code @required} trait. */
    public boolean isRequired() {
        return traits.stream().anyMatch(t -> "required".equals(t.name()));
    }
}
