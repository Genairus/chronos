package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;

/**
 * Sealed marker interface for all top-level shape definitions in an IR model.
 *
 * <p>Every concrete IR shape type implements this interface, enabling exhaustive
 * pattern matching in compiler phases, generators, and validators:
 *
 * <pre>
 *   switch (shape) {
 *       case EntityDef        e -> ...
 *       case ShapeStructDef   s -> ...
 *       case EnumDef          e -> ...
 *       case ListDef          l -> ...
 *       case MapDef           m -> ...
 *       case ActorDef         a -> ...
 *       case PolicyDef        p -> ...
 *       case JourneyDef       j -> ...
 *       case RelationshipDef  r -> ...
 *       case InvariantDef     i -> ...
 *       case DenyDef          d -> ...
 *       case ErrorDef         e -> ...
 *       case StateMachineDef  s -> ...
 *       case RoleDef          r -> ...
 *       case EventDef         e -> ...
 *   }
 * </pre>
 */
public sealed interface IrShape
        permits ActorDef,
                DenyDef,
                EntityDef,
                EnumDef,
                ErrorDef,
                EventDef,
                InvariantDef,
                JourneyDef,
                ListDef,
                MapDef,
                PolicyDef,
                RelationshipDef,
                RoleDef,
                ShapeStructDef,
                StateMachineDef {

    /** The declared name of this shape (PascalCase). */
    String name();

    /** The source location where this shape was declared. */
    Span span();
}
