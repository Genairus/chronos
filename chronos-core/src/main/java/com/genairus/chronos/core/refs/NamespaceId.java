package com.genairus.chronos.core.refs;

/**
 * The identity of a Chronos namespace — the dot-separated path declared by
 * {@code namespace com.example.checkout} at the top of every {@code .chronos} file.
 *
 * <p>A {@code NamespaceId} is used as the owning scope of a {@link ShapeId}: every
 * top-level declaration belongs to exactly one namespace.
 *
 * <p>The canonical form is the raw dotted string exactly as written in the source
 * (e.g. {@code "com.example.checkout"}).  No normalization or case-folding is applied.
 *
 * @param value  non-blank, dot-separated namespace path
 */
public record NamespaceId(String value) {

    /**
     * Compact constructor — validates that {@code value} is non-null and non-blank.
     *
     * @throws IllegalArgumentException if {@code value} is null or blank
     */
    public NamespaceId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "NamespaceId value must be non-blank; got: " + value);
        }
    }

    /**
     * Returns the raw dotted namespace string.
     * Equivalent to {@link #value()} but matches the {@link Object#toString()} contract.
     */
    @Override
    public String toString() {
        return value;
    }
}
