package com.genairus.chronos.ir.model;

import com.genairus.chronos.ir.types.*;

import java.util.List;

/**
 * The canonical IR model produced by the Chronos compiler pipeline.
 *
 * <p>An {@code IrModel} is a fully-parsed (but not yet fully cross-linked) representation
 * of a single {@code .chronos} source file. It is produced by {@code BuildIrSkeletonPhase}
 * and progressively enriched by subsequent compiler phases.
 *
 * @param namespace the namespace declaration from the source file
 * @param imports   the {@code use} declarations
 * @param shapes    all top-level shape definitions
 */
public record IrModel(
        String namespace,
        List<UseDecl> imports,
        List<IrShape> shapes) {

    /** Returns all entity definitions. */
    public List<EntityDef> entities() {
        return shapes.stream()
                .filter(s -> s instanceof EntityDef)
                .map(s -> (EntityDef) s)
                .toList();
    }

    /** Returns all journey definitions. */
    public List<JourneyDef> journeys() {
        return shapes.stream()
                .filter(s -> s instanceof JourneyDef)
                .map(s -> (JourneyDef) s)
                .toList();
    }

    /** Returns all relationship definitions. */
    public List<RelationshipDef> relationships() {
        return shapes.stream()
                .filter(s -> s instanceof RelationshipDef)
                .map(s -> (RelationshipDef) s)
                .toList();
    }

    /** Returns all actor definitions. */
    public List<ActorDef> actors() {
        return shapes.stream()
                .filter(s -> s instanceof ActorDef)
                .map(s -> (ActorDef) s)
                .toList();
    }

    /** Returns all enum definitions. */
    public List<EnumDef> enums() {
        return shapes.stream()
                .filter(s -> s instanceof EnumDef)
                .map(s -> (EnumDef) s)
                .toList();
    }

    /** Returns all shape struct (value object) definitions. */
    public List<ShapeStructDef> shapeStructs() {
        return shapes.stream()
                .filter(s -> s instanceof ShapeStructDef)
                .map(s -> (ShapeStructDef) s)
                .toList();
    }

    /** Returns all named list definitions. */
    public List<ListDef> lists() {
        return shapes.stream()
                .filter(s -> s instanceof ListDef)
                .map(s -> (ListDef) s)
                .toList();
    }

    /** Returns all named map definitions. */
    public List<MapDef> maps() {
        return shapes.stream()
                .filter(s -> s instanceof MapDef)
                .map(s -> (MapDef) s)
                .toList();
    }

    /** Returns all policy definitions. */
    public List<PolicyDef> policies() {
        return shapes.stream()
                .filter(s -> s instanceof PolicyDef)
                .map(s -> (PolicyDef) s)
                .toList();
    }

    /** Returns all global invariant definitions. */
    public List<InvariantDef> invariants() {
        return shapes.stream()
                .filter(s -> s instanceof InvariantDef)
                .map(s -> (InvariantDef) s)
                .toList();
    }

    /** Returns all deny definitions (negative requirements). */
    public List<DenyDef> denies() {
        return shapes.stream()
                .filter(s -> s instanceof DenyDef)
                .map(s -> (DenyDef) s)
                .toList();
    }

    /** Returns all error definitions. */
    public List<ErrorDef> errors() {
        return shapes.stream()
                .filter(s -> s instanceof ErrorDef)
                .map(s -> (ErrorDef) s)
                .toList();
    }

    /** Returns all state machine definitions. */
    public List<StateMachineDef> stateMachines() {
        return shapes.stream()
                .filter(s -> s instanceof StateMachineDef)
                .map(s -> (StateMachineDef) s)
                .toList();
    }

    /** Finds the first shape with the given name, regardless of type. */
    public java.util.Optional<IrShape> findShape(String name) {
        return shapes.stream()
                .filter(s -> s.name().equals(name))
                .findFirst();
    }
}
