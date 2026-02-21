package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * An IR named map collection shape.
 *
 * @param name        the map name (PascalCase)
 * @param traits      trait applications applied to this map
 * @param docComments lines from preceding {@code ///} doc comments
 * @param keyType     the key type reference
 * @param valueType   the value type reference
 * @param span        source location of the {@code map} keyword
 */
public record MapDef(
        String name,
        List<TraitApplication> traits,
        List<String> docComments,
        TypeRef keyType,
        TypeRef valueType,
        Span span) implements IrShape {}
