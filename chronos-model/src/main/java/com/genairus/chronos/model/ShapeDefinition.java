package com.genairus.chronos.model;

/**
 * Sealed marker interface for all top-level shape definitions in a Chronos model.
 *
 * <p>Every concrete shape type implements this interface, enabling exhaustive
 * pattern matching in visitors, generators, and selectors:
 *
 * <pre>
 *   switch (shape) {
 *       case EntityDef        e -> ...
 *       case ShapeStructDef   s -> ...
 *       case ListDef          l -> ...
 *       case MapDef           m -> ...
 *       case EnumDef          e -> ...
 *       case ActorDef         a -> ...
 *       case PolicyDef        p -> ...
 *       case JourneyDef       j -> ...
 *       case RelationshipDef  r -> ...
 *       case InvariantDef     i -> ...
 *       case DenyDef          d -> ...
 *       case ErrorDef         e -> ...
 *       case StateMachineDef  s -> ...
 *   }
 * </pre>
 */
public sealed interface ShapeDefinition
        permits ActorDef,
                DenyDef,
                EntityDef,
                EnumDef,
                ErrorDef,
                InvariantDef,
                JourneyDef,
                ListDef,
                MapDef,
                PolicyDef,
                RelationshipDef,
                ShapeStructDef,
                StateMachineDef {

    /** The declared name of this shape (PascalCase). */
    String name();

    /** The source location where this shape was declared. */
    SourceLocation location();
}
