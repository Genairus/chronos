package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

/**
 * Sealed type for a type reference (grammar rule: {@code typeRef}).
 *
 * <p>Four alternatives mirror the grammar: generic {@code List<T>},
 * generic {@code Map<K,V>}, a primitive keyword, or a named shape reference.
 * All named references are raw strings — no resolution has occurred.
 */
public sealed interface SyntaxTypeRef {

    /** Primitive type keywords supported by the grammar. */
    enum PrimitiveKind {
        STRING, INTEGER, LONG, FLOAT, BOOLEAN, TIMESTAMP, BLOB, DOCUMENT
    }

    /** A primitive type keyword (e.g. {@code String}, {@code Integer}). */
    record Primitive(PrimitiveKind kind, Span span) implements SyntaxTypeRef {}

    /**
     * A named type reference (e.g. {@code Order} or {@code com.example.Order}).
     * The name is the raw dot-separated qualified-id string as written in source.
     */
    record Named(String name, Span span) implements SyntaxTypeRef {}

    /** {@code List<elementType>} — a generic list type. */
    record ListType(SyntaxTypeRef element, Span span) implements SyntaxTypeRef {}

    /** {@code Map<keyType, valueType>} — a generic map type. */
    record MapType(SyntaxTypeRef key, SyntaxTypeRef value, Span span) implements SyntaxTypeRef {}
}
