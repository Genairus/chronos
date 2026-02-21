package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * An IR field definition within an entity or shape struct.
 *
 * @param name   field identifier
 * @param type   the declared type reference
 * @param traits trait applications on this field
 * @param span   source location
 */
public record FieldDef(String name, TypeRef type, List<TraitApplication> traits, Span span) {

    /** Returns {@code true} if this field carries a {@code @required} trait. */
    public boolean isRequired() {
        return traits.stream().anyMatch(t -> "required".equals(t.name()));
    }
}
