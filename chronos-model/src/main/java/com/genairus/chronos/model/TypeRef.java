package com.genairus.chronos.model;

/**
 * Represents a type reference in an entity or shape field declaration.
 *
 * <p>The four concrete forms:
 * <pre>
 *   String, Integer, Float …  → PrimitiveType
 *   List&lt;OrderItem&gt;           → ListType
 *   Map&lt;String, String&gt;       → MapType
 *   OrderStatus                → NamedTypeRef  (resolved shape reference)
 * </pre>
 */
public sealed interface TypeRef
        permits TypeRef.PrimitiveType,
                TypeRef.ListType,
                TypeRef.MapType,
                TypeRef.NamedTypeRef {

    // ── Primitive types ────────────────────────────────────────────────────────

    /**
     * One of the eight Chronos primitive types.
     *
     * @param kind the specific primitive
     */
    record PrimitiveType(PrimitiveKind kind) implements TypeRef {
        @Override public String toString() { return kind.name(); }
    }

    /** Generic list type: {@code List<elementType>}. */
    record ListType(TypeRef elementType) implements TypeRef {
        @Override public String toString() { return "List<" + elementType + ">"; }
    }

    /** Generic map type: {@code Map<keyType, valueType>}. */
    record MapType(TypeRef keyType, TypeRef valueType) implements TypeRef {
        @Override public String toString() { return "Map<" + keyType + ", " + valueType + ">"; }
    }

    /**
     * A reference to a named shape defined elsewhere (entity, shape struct,
     * enum, etc.), identified by its qualified ID.
     *
     * @param qualifiedId dot-separated name, e.g. {@code com.example.OrderItem}
     *                    or just {@code OrderItem} for a same-namespace reference
     */
    record NamedTypeRef(String qualifiedId) implements TypeRef {
        @Override public String toString() { return qualifiedId; }
    }

    // ── PrimitiveKind enum ─────────────────────────────────────────────────────

    /** The eight primitive scalar types defined in the Chronos language spec. */
    enum PrimitiveKind {
        STRING,
        INTEGER,
        LONG,
        FLOAT,
        BOOLEAN,
        TIMESTAMP,
        BLOB,
        DOCUMENT;

        /** Returns the Chronos source spelling of this primitive (e.g. {@code String}). */
        public String chronosName() {
            return switch (this) {
                case STRING    -> "String";
                case INTEGER   -> "Integer";
                case LONG      -> "Long";
                case FLOAT     -> "Float";
                case BOOLEAN   -> "Boolean";
                case TIMESTAMP -> "Timestamp";
                case BLOB      -> "Blob";
                case DOCUMENT  -> "Document";
            };
        }
    }
}
