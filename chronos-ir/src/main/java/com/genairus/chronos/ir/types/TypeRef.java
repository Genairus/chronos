package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.SymbolRef;

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

    /**
     * A named (user-defined) type reference backed by a {@link SymbolRef}.
     *
     * <p>Initially created with an {@link SymbolRef#unresolved unresolved} ref of kind
     * {@link com.genairus.chronos.core.refs.SymbolKind#TYPE} by {@code BuildIrSkeletonPhase}.
     * {@code TypeResolutionPhase} replaces the ref with a resolved one carrying the actual
     * shape kind (ENTITY, STRUCT, ENUM, LIST, or MAP).
     */
    record NamedTypeRef(SymbolRef ref) implements TypeRef {

        /**
         * Convenience accessor for the simple type name — works for both resolved and
         * unresolved refs. Used by validators and generators to obtain the readable name
         * without inspecting resolution state.
         */
        public String qualifiedId() {
            return ref.isResolved() ? ref.id().name() : ref.name().name();
        }

        @Override public String toString() { return qualifiedId(); }
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
