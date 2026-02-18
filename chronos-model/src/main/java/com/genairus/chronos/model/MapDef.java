package com.genairus.chronos.model;

import java.util.List;

/**
 * A named map collection shape.
 *
 * <pre>
 *   map MetadataMap {
 *       key: String
 *       value: String
 *   }
 * </pre>
 *
 * @param name        the map type name (PascalCase)
 * @param traits      trait applications applied to this map shape
 * @param docComments lines from preceding {@code ///} doc comments
 * @param keyType     the key type
 * @param valueType   the value type
 * @param location    source location of the {@code map} keyword
 */
public record MapDef(
        String name,
        List<TraitApplication> traits,
        List<String> docComments,
        TypeRef keyType,
        TypeRef valueType,
        SourceLocation location) implements ShapeDefinition {}
