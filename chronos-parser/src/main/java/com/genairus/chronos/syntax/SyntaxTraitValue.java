package com.genairus.chronos.syntax;

/**
 * Sealed type for the value part of a trait argument (grammar rule: {@code traitValue}).
 *
 * <p>Four forms correspond to the four grammar alternatives:
 * string literal, number literal, boolean literal, and a qualified-id reference.
 */
public sealed interface SyntaxTraitValue {

    /** A double-quoted string value (e.g. {@code "text"}), with escape sequences resolved. */
    record StringVal(String value) implements SyntaxTraitValue {}

    /** A numeric literal (integer or decimal). */
    record NumberVal(double value) implements SyntaxTraitValue {}

    /** A boolean literal ({@code true} or {@code false}). */
    record BoolVal(boolean value) implements SyntaxTraitValue {}

    /**
     * A qualified-id reference (e.g. {@code SomeShape} or {@code com.example.Shape}).
     * The value is the raw dot-separated string as written in source — not yet resolved.
     */
    record RefVal(String ref) implements SyntaxTraitValue {}
}
