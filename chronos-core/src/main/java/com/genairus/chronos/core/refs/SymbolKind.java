package com.genairus.chronos.core.refs;

/**
 * The kind of top-level Chronos declaration a {@link ShapeId} or {@link SymbolRef}
 * points to.
 *
 * <p>This enum drives kind-compatibility checks during resolution:
 * <ul>
 *   <li>{@code entity extends ID} — the target must be {@link #ENTITY}.</li>
 *   <li>{@code actor extends ID} — the target must be {@link #ACTOR}.</li>
 *   <li>{@code journey actor: ID} — the target must be {@link #ACTOR}.</li>
 *   <li>{@code relationship from/to: ID} — the target must be {@link #ENTITY}.</li>
 *   <li>{@code variant trigger: ID} — the target must be {@link #ERROR}.</li>
 *   <li>{@code statemachine entity: ID} — the target must be {@link #ENTITY}.</li>
 *   <li>{@code invariant/deny scope: [ID...]} — target may be any shape kind.</li>
 * </ul>
 *
 * <p>{@link #EVENT} is reserved for future telemetry event declarations; it does
 * not correspond to a grammar construct in the current version of {@code Chronos.g4}
 * but is included so that {@code step telemetry: [ID, ...]} references can be
 * typed correctly once events are introduced.
 */
public enum SymbolKind {

    /** {@code entity} declaration — business object with identity. */
    ENTITY,

    /** {@code shape} declaration — value object with no identity. */
    STRUCT,

    /** {@code enum} declaration — closed set of named values. */
    ENUM,

    /** {@code list} declaration — named list collection type. */
    LIST,

    /** {@code map} declaration — named map collection type. */
    MAP,

    /** {@code actor} declaration — user or system that participates in journeys. */
    ACTOR,

    /** {@code policy} declaration — global business or regulatory constraint. */
    POLICY,

    /** {@code journey} declaration — cohesive unit of user/system value. */
    JOURNEY,

    /** {@code relationship} declaration — first-class association between entities. */
    RELATIONSHIP,

    /** {@code invariant} declaration (global) — cross-cutting business rule. */
    INVARIANT,

    /** {@code deny} declaration — negative requirement (what the system must never do). */
    DENY,

    /** {@code error} declaration — typed error condition with code and payload. */
    ERROR,

    /** {@code statemachine} declaration — lifecycle of an entity's field. */
    STATEMACHINE,

    /**
     * Reserved for future {@code event} declarations.
     * Currently only referenced by {@code step telemetry: [ID, ...]} lists.
     */
    EVENT
}
