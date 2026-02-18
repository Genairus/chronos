package com.genairus.chronos.model;

import java.util.List;

/**
 * A named list collection shape.
 *
 * <pre>
 *   list OrderItemList {
 *       member: OrderItem
 *   }
 * </pre>
 *
 * @param name        the list type name (PascalCase)
 * @param traits      trait applications applied to this list shape
 * @param docComments lines from preceding {@code ///} doc comments
 * @param memberType  the element type
 * @param location    source location of the {@code list} keyword
 */
public record ListDef(
        String name,
        List<TraitApplication> traits,
        List<String> docComments,
        TypeRef memberType,
        SourceLocation location) implements ShapeDefinition {}
