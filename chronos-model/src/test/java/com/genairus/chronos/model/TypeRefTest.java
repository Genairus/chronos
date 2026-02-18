package com.genairus.chronos.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class TypeRefTest {

    @Test
    void primitiveTypeToString() {
        var t = new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.STRING);
        assertEquals("STRING", t.toString());
    }

    @Test
    void listTypeToString() {
        var inner = new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.INTEGER);
        var list = new TypeRef.ListType(inner);
        assertEquals("List<INTEGER>", list.toString());
    }

    @Test
    void mapTypeToString() {
        var key = new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.STRING);
        var val = new TypeRef.NamedTypeRef("com.example.Order");
        var map = new TypeRef.MapType(key, val);
        assertEquals("Map<STRING, com.example.Order>", map.toString());
    }

    @Test
    void namedTypeRefToString() {
        var ref = new TypeRef.NamedTypeRef("OrderStatus");
        assertEquals("OrderStatus", ref.toString());
    }

    @Test
    void nestedGenericTypes() {
        // List<Map<String, String>>
        var str = new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.STRING);
        var map = new TypeRef.MapType(str, str);
        var list = new TypeRef.ListType(map);
        assertEquals("List<Map<STRING, STRING>>", list.toString());
    }

    @ParameterizedTest
    @EnumSource(TypeRef.PrimitiveKind.class)
    void allPrimitiveKindsHaveChronosName(TypeRef.PrimitiveKind kind) {
        assertNotNull(kind.chronosName());
        assertFalse(kind.chronosName().isBlank());
    }

    @Test
    void chronosNamesMatchSpec() {
        assertEquals("String",    TypeRef.PrimitiveKind.STRING.chronosName());
        assertEquals("Integer",   TypeRef.PrimitiveKind.INTEGER.chronosName());
        assertEquals("Long",      TypeRef.PrimitiveKind.LONG.chronosName());
        assertEquals("Float",     TypeRef.PrimitiveKind.FLOAT.chronosName());
        assertEquals("Boolean",   TypeRef.PrimitiveKind.BOOLEAN.chronosName());
        assertEquals("Timestamp", TypeRef.PrimitiveKind.TIMESTAMP.chronosName());
        assertEquals("Blob",      TypeRef.PrimitiveKind.BLOB.chronosName());
        assertEquals("Document",  TypeRef.PrimitiveKind.DOCUMENT.chronosName());
    }

    @Test
    void sealedExhaustivePatternMatch() {
        TypeRef ref = new TypeRef.NamedTypeRef("Foo");
        String result = switch (ref) {
            case TypeRef.PrimitiveType p -> "primitive";
            case TypeRef.ListType      l -> "list";
            case TypeRef.MapType       m -> "map";
            case TypeRef.NamedTypeRef  n -> "named:" + n.qualifiedId();
        };
        assertEquals("named:Foo", result);
    }
}
