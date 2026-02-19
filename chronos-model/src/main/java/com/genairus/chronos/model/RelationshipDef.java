package com.genairus.chronos.model;

import java.util.List;
import java.util.Optional;

/**
 * A first-class relationship declaration between two entities.
 *
 * <pre>
 *   @description("Order contains line items")
 *   relationship OrderItems {
 *       from: Order
 *       to: OrderItem
 *       cardinality: one_to_many
 *       semantics: composition
 *       inverse: order
 *   }
 * </pre>
 *
 * @param name         the relationship name (PascalCase)
 * @param traits       trait applications on this relationship
 * @param docComments  documentation comment lines
 * @param fromEntity   the source entity name
 * @param toEntity     the target entity name
 * @param cardinality  the cardinality constraint
 * @param semantics    the relationship semantics (optional, defaults to association)
 * @param inverseField the name of the inverse field on the target entity (optional)
 * @param location     source location of the relationship name token
 */
public record RelationshipDef(
        String name,
        List<TraitApplication> traits,
        List<String> docComments,
        String fromEntity,
        String toEntity,
        Cardinality cardinality,
        Optional<RelationshipSemantics> semantics,
        Optional<String> inverseField,
        SourceLocation location) implements ShapeDefinition {

    /** Returns the effective semantics, defaulting to ASSOCIATION if not specified. */
    public RelationshipSemantics effectiveSemantics() {
        return semantics.orElse(RelationshipSemantics.ASSOCIATION);
    }
}

