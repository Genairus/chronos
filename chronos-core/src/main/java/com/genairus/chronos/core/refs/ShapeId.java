package com.genairus.chronos.core.refs;

import java.util.Objects;

/**
 * The stable, canonical identity of a top-level Chronos declaration.
 *
 * <p>A {@code ShapeId} is assigned to every top-level shape during Pass 2
 * (Collect Symbols) and never changes thereafter.  IR nodes reference each
 * other through {@code ShapeId} values (or through resolved {@link SymbolRef}s
 * that carry a {@code ShapeId}) rather than raw name strings.
 *
 * <h2>String representation</h2>
 * The canonical form is {@code "namespace#name"}, matching the {@code use}
 * import syntax in the Chronos grammar ({@code use com.example.checkout#Order}).
 * This form is:
 * <ul>
 *   <li>Unique across a multi-file compilation (namespace disambiguates).</li>
 *   <li>Human-readable in diagnostic output.</li>
 *   <li>Stable for serialization (e.g. JSON via Jackson).</li>
 * </ul>
 *
 * @param namespace  the owning namespace (e.g. {@code com.example.checkout})
 * @param name       the simple shape name (e.g. {@code Order}); never blank
 */
public record ShapeId(NamespaceId namespace, String name) {

    /** Compact constructor — validates {@code name} is non-null and non-blank. */
    public ShapeId {
        Objects.requireNonNull(namespace, "ShapeId.namespace must not be null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "ShapeId.name must be non-blank; got: " + name);
        }
    }

    // ── Factory ────────────────────────────────────────────────────────────────

    /**
     * Convenience factory to build a {@code ShapeId} from raw strings.
     *
     * @param namespace  dot-separated namespace string (e.g. {@code "com.example"})
     * @param name       simple shape name (e.g. {@code "Order"})
     */
    public static ShapeId of(String namespace, String name) {
        return new ShapeId(new NamespaceId(namespace), name);
    }

    // ── Display ────────────────────────────────────────────────────────────────

    /**
     * Returns the canonical form {@code "namespace#name"}.
     *
     * <p>Example: {@code "com.example.checkout#Order"}.
     * This matches the grammar's {@code use} declaration separator and is the
     * format used in all diagnostic messages and serialized IR.
     */
    @Override
    public String toString() {
        return namespace.value() + "#" + name;
    }
}
