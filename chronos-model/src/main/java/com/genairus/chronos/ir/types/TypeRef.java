package com.genairus.chronos.ir.types;

/**
 * Represents a type reference in an IR field declaration.
 *
 * <p>Mirrors {@code com.genairus.chronos.model.TypeRef} but lives in the IR
 * type system so that the compiler never imports the legacy model package.
 */
public sealed interface TypeRef
        permits TypeRef.PrimitiveType,
                TypeRef.ListType,
                TypeRef.MapType,
                TypeRef.NamedTypeRef {

    record PrimitiveType(PrimitiveKind kind) implements TypeRef {
        @Override public String toString() { return kind.name(); }
    }

    record ListType(TypeRef elementType) implements TypeRef {
        @Override public String toString() { return "List<" + elementType + ">"; }
    }

    record MapType(TypeRef keyType, TypeRef valueType) implements TypeRef {
        @Override public String toString() { return "Map<" + keyType + ", " + valueType + ">"; }
    }

    record NamedTypeRef(String qualifiedId) implements TypeRef {
        @Override public String toString() { return qualifiedId; }
    }

    enum PrimitiveKind {
        STRING, INTEGER, LONG, FLOAT, BOOLEAN, TIMESTAMP, BLOB, DOCUMENT;

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
