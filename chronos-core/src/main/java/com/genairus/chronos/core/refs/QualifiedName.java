package com.genairus.chronos.core.refs;

import java.util.Objects;

/**
 * A possibly-qualified shape name as it appears in source before symbol resolution.
 *
 * <p>Two forms exist:
 * <ul>
 *   <li><b>Local</b>: only a simple name — e.g. {@code Order}. The resolver looks
 *       this up in the current file's namespace first, then in imports.</li>
 *   <li><b>Qualified</b>: a namespace prefix plus name — e.g. {@code com.example#Order}.
 *       The resolver uses the namespace directly, skipping the local scope.</li>
 * </ul>
 *
 * <p>{@code QualifiedName} is the raw pre-resolution form stored in a
 * {@link SymbolRef} before Pass 6 completes. After resolution, the ref holds a
 * {@link ShapeId} and the {@code QualifiedName} is no longer needed.
 *
 * @param namespaceOrNull  dot-separated namespace, or {@code null} for a local name
 * @param name             the simple (unqualified) shape name; never blank
 */
public record QualifiedName(String namespaceOrNull, String name) {

    /** Compact constructor — validates {@code name} is non-null and non-blank. */
    public QualifiedName {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "QualifiedName.name must be non-blank; got: " + name);
        }
    }

    // ── Factories ──────────────────────────────────────────────────────────────

    /**
     * Creates a <em>local</em> name — no namespace prefix.
     * The resolver will search the current file's namespace and its imports.
     *
     * @param name  simple shape name (e.g. {@code "Order"})
     */
    public static QualifiedName local(String name) {
        return new QualifiedName(null, name);
    }

    /**
     * Creates a <em>qualified</em> name with an explicit namespace.
     * The resolver will look in {@code namespace} directly, not in local scope.
     *
     * @param namespace  dot-separated namespace (e.g. {@code "com.example.checkout"})
     * @param name       simple shape name (e.g. {@code "Order"})
     */
    public static QualifiedName qualified(String namespace, String name) {
        Objects.requireNonNull(namespace, "namespace must not be null for a qualified name");
        return new QualifiedName(namespace, name);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Returns {@code true} if this name carries an explicit namespace prefix. */
    public boolean isQualified() {
        return namespaceOrNull != null;
    }

    /**
     * Human-readable form:
     * <ul>
     *   <li>Local: {@code "Order"}</li>
     *   <li>Qualified: {@code "com.example.checkout#Order"}</li>
     * </ul>
     * The {@code #} separator mirrors the {@code use} declaration syntax in the grammar.
     */
    @Override
    public String toString() {
        return isQualified() ? namespaceOrNull + "#" + name : name;
    }
}
