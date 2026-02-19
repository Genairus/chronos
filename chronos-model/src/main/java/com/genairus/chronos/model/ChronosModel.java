package com.genairus.chronos.model;

import java.util.List;

/**
 * The root of a parsed Chronos model — the in-memory representation of one
 * {@code .chronos} source file after parsing and import resolution.
 *
 * <p>All validators and generators receive a {@code ChronosModel} and read from
 * it. It is immutable once constructed by the {@code ChronosModelBuilder}.
 *
 * @param namespace the declared namespace (e.g. {@code com.example.checkout})
 * @param imports   the {@code use} import declarations in source order
 * @param shapes    all top-level shape definitions in source order
 */
public record ChronosModel(
        String namespace,
        List<UseDecl> imports,
        List<ShapeDefinition> shapes) {

    // ── Typed accessors ────────────────────────────────────────────────────────

    /** All journey definitions in source order. */
    public List<JourneyDef> journeys() {
        return shapes.stream()
                .filter(s -> s instanceof JourneyDef)
                .map(s -> (JourneyDef) s)
                .toList();
    }

    /** All entity definitions in source order. */
    public List<EntityDef> entities() {
        return shapes.stream()
                .filter(s -> s instanceof EntityDef)
                .map(s -> (EntityDef) s)
                .toList();
    }

    /** All shape struct definitions (value objects) in source order. */
    public List<ShapeStructDef> shapeStructs() {
        return shapes.stream()
                .filter(s -> s instanceof ShapeStructDef)
                .map(s -> (ShapeStructDef) s)
                .toList();
    }

    /** All enum definitions in source order. */
    public List<EnumDef> enums() {
        return shapes.stream()
                .filter(s -> s instanceof EnumDef)
                .map(s -> (EnumDef) s)
                .toList();
    }

    /** All actor definitions in source order. */
    public List<ActorDef> actors() {
        return shapes.stream()
                .filter(s -> s instanceof ActorDef)
                .map(s -> (ActorDef) s)
                .toList();
    }

    /** All policy definitions in source order. */
    public List<PolicyDef> policies() {
        return shapes.stream()
                .filter(s -> s instanceof PolicyDef)
                .map(s -> (PolicyDef) s)
                .toList();
    }

    /** All named list definitions in source order. */
    public List<ListDef> lists() {
        return shapes.stream()
                .filter(s -> s instanceof ListDef)
                .map(s -> (ListDef) s)
                .toList();
    }

    /** All named map definitions in source order. */
    public List<MapDef> maps() {
        return shapes.stream()
                .filter(s -> s instanceof MapDef)
                .map(s -> (MapDef) s)
                .toList();
    }

    /** All relationship definitions in source order. */
    public List<RelationshipDef> relationships() {
        return shapes.stream()
                .filter(s -> s instanceof RelationshipDef)
                .map(s -> (RelationshipDef) s)
                .toList();
    }

    // ── Lookup ─────────────────────────────────────────────────────────────────

    /**
     * Finds the first shape with the given name, regardless of type.
     * Used by the validator for CHR-008 (unresolved shape references).
     */
    public java.util.Optional<ShapeDefinition> findShape(String name) {
        return shapes.stream()
                .filter(s -> s.name().equals(name))
                .findFirst();
    }
}
