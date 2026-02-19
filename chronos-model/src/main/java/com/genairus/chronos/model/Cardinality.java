package com.genairus.chronos.model;

/**
 * Cardinality constraint for a {@link RelationshipDef}.
 *
 * <p>Defines how many instances of the target entity can be associated with
 * each instance of the source entity.
 */
public enum Cardinality {
    /** One source instance relates to exactly one target instance. */
    ONE_TO_ONE("one_to_one"),

    /** One source instance relates to zero or more target instances. */
    ONE_TO_MANY("one_to_many"),

    /** Many source instances can relate to many target instances. */
    MANY_TO_MANY("many_to_many");

    private final String chronosName;

    Cardinality(String chronosName) {
        this.chronosName = chronosName;
    }

    /** Returns the Chronos syntax name for this cardinality. */
    public String chronosName() {
        return chronosName;
    }

    /** Parses a Chronos cardinality name into the enum value. */
    public static Cardinality fromChronosName(String name) {
        return switch (name) {
            case "one_to_one" -> ONE_TO_ONE;
            case "one_to_many" -> ONE_TO_MANY;
            case "many_to_many" -> MANY_TO_MANY;
            default -> throw new IllegalArgumentException("Unknown cardinality: " + name);
        };
    }
}

