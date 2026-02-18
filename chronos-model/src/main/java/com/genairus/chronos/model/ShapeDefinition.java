package com.genairus.chronos.model;

/**
 * Sealed marker interface for all top-level shape definitions in a Chronos model.
 *
 * <p>Every concrete shape type implements this interface, enabling exhaustive
 * pattern matching in visitors, generators, and selectors:
 *
 * <pre>
 *   switch (shape) {
 *       case EntityDef      e -> ...
 *       case ShapeStructDef s -> ...
 *       case ListDef        l -> ...
 *       case MapDef         m -> ...
 *       case EnumDef        e -> ...
 *       case ActorDef       a -> ...
 *       case PolicyDef      p -> ...
 *       case JourneyDef     j -> ...
 *   }
 * </pre>
 */
public sealed interface ShapeDefinition
        permits ActorDef,
                EntityDef,
                EnumDef,
                JourneyDef,
                ListDef,
                MapDef,
                PolicyDef,
                ShapeStructDef {

    /** The declared name of this shape (PascalCase). */
    String name();

    /** The source location where this shape was declared. */
    SourceLocation location();
}
