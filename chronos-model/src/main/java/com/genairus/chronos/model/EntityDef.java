package com.genairus.chronos.model;

import java.util.List;
import java.util.Optional;

/**
 * A top-level entity definition — a business object with identity.
 *
 * <pre>
 *   @pii
 *   entity Order {
 *       @required
 *       id: String
 *       status: OrderStatus
 *
 *       invariant TotalMatchesItems {
 *           expression: "total.amount == sum(items, i => i.unitPrice.amount * i.quantity)"
 *           severity: error
 *       }
 *   }
 *
 *   entity PremiumUser extends User {
 *       tier: PremiumTier
 *       rewardsBalance: Integer
 *   }
 * </pre>
 *
 * @param name        the entity name (PascalCase)
 * @param traits      trait applications applied to this entity
 * @param docComments lines from preceding {@code ///} doc comments (without the {@code ///} prefix)
 * @param parentType  optional parent entity name (for inheritance)
 * @param fields      the ordered list of field declarations
 * @param invariants  the ordered list of entity-scoped invariants
 * @param location    source location of the {@code entity} keyword
 */
public record EntityDef(
        String name,
        List<TraitApplication> traits,
        List<String> docComments,
        Optional<String> parentType,
        List<FieldDef> fields,
        List<EntityInvariant> invariants,
        SourceLocation location) implements ShapeDefinition {}
