package com.genairus.chronos.ir.types;

/** Cardinality constraint for an IR {@link RelationshipDef}. */
public enum Cardinality {
    ONE_TO_ONE("one_to_one"),
    ONE_TO_MANY("one_to_many"),
    MANY_TO_MANY("many_to_many");

    private final String chronosName;

    Cardinality(String chronosName) { this.chronosName = chronosName; }

    public String chronosName() { return chronosName; }

    public static Cardinality fromChronosName(String name) {
        return switch (name) {
            case "one_to_one"   -> ONE_TO_ONE;
            case "one_to_many"  -> ONE_TO_MANY;
            case "many_to_many" -> MANY_TO_MANY;
            default -> throw new IllegalArgumentException("Unknown cardinality: " + name);
        };
    }
}
