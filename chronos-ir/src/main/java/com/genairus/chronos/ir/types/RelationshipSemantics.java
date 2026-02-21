package com.genairus.chronos.ir.types;

/** Semantic classification for an IR {@link RelationshipDef}. */
public enum RelationshipSemantics {
    ASSOCIATION("association"),
    AGGREGATION("aggregation"),
    COMPOSITION("composition");

    private final String chronosName;

    RelationshipSemantics(String chronosName) { this.chronosName = chronosName; }

    public String chronosName() { return chronosName; }

    public static RelationshipSemantics fromChronosName(String name) {
        return switch (name) {
            case "association" -> ASSOCIATION;
            case "aggregation" -> AGGREGATION;
            case "composition" -> COMPOSITION;
            default -> throw new IllegalArgumentException("Unknown relationship semantics: " + name);
        };
    }
}
