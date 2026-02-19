package com.genairus.chronos.model;

/**
 * Semantic classification for a {@link RelationshipDef}.
 *
 * <p>Defines the lifecycle and ownership semantics of the relationship.
 */
public enum RelationshipSemantics {
    /**
     * Association: independent lifecycle. Deleting the source does not affect
     * the target.
     */
    ASSOCIATION("association"),

    /**
     * Aggregation: shared ownership. The target can exist independently but is
     * logically part of the source.
     */
    AGGREGATION("aggregation"),

    /**
     * Composition: exclusive ownership. The target's lifecycle is bound to the
     * source; deleting the source cascades to the target.
     */
    COMPOSITION("composition");

    private final String chronosName;

    RelationshipSemantics(String chronosName) {
        this.chronosName = chronosName;
    }

    /** Returns the Chronos syntax name for this semantics. */
    public String chronosName() {
        return chronosName;
    }

    /** Parses a Chronos semantics name into the enum value. */
    public static RelationshipSemantics fromChronosName(String name) {
        return switch (name) {
            case "association" -> ASSOCIATION;
            case "aggregation" -> AGGREGATION;
            case "composition" -> COMPOSITION;
            default -> throw new IllegalArgumentException("Unknown relationship semantics: " + name);
        };
    }
}

