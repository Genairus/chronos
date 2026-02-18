package com.genairus.chronos.model;

import java.util.List;

/**
 * A top-level entity definition — a business object with identity.
 *
 * <pre>
 *   @pii
 *   entity Order {
 *       @required
 *       id: String
 *       status: OrderStatus
 *   }
 * </pre>
 *
 * @param name        the entity name (PascalCase)
 * @param traits      trait applications applied to this entity
 * @param docComments lines from preceding {@code ///} doc comments (without the {@code ///} prefix)
 * @param fields      the ordered list of field declarations
 * @param location    source location of the {@code entity} keyword
 */
public record EntityDef(
        String name,
        List<TraitApplication> traits,
        List<String> docComments,
        List<FieldDef> fields,
        SourceLocation location) implements ShapeDefinition {}
