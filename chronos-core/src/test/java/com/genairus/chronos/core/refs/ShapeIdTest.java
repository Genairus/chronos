package com.genairus.chronos.core.refs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShapeIdTest {

    // ── of() factory ──────────────────────────────────────────────────────────

    @Test
    void ofFactoryCreatesExpectedState() {
        var id = ShapeId.of("com.example.checkout", "Order");
        assertEquals("com.example.checkout", id.namespace().value());
        assertEquals("Order", id.name());
    }

    // ── Full constructor ───────────────────────────────────────────────────────

    @Test
    void constructorStoresNamespaceAndName() {
        var ns = new NamespaceId("com.example");
        var id = new ShapeId(ns, "Customer");
        assertEquals(ns, id.namespace());
        assertEquals("Customer", id.name());
    }

    // ── toString ──────────────────────────────────────────────────────────────

    @Test
    void toStringUsesHashSeparator() {
        var id = ShapeId.of("com.example.checkout", "Order");
        assertEquals("com.example.checkout#Order", id.toString());
    }

    @Test
    void toStringMatchesUseDeclarationSyntax() {
        // "use com.example.checkout#Order" — the '#' mirrors grammar §1.2
        var id = ShapeId.of("com.example.checkout", "Order");
        String s = id.toString();
        assertTrue(s.startsWith("com.example.checkout#"));
        assertTrue(s.endsWith("#Order"));
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    void nullNamespaceThrows() {
        assertThrows(NullPointerException.class, () -> new ShapeId(null, "Foo"));
    }

    @Test
    void blankNameThrows() {
        var ns = new NamespaceId("com.example");
        assertThrows(IllegalArgumentException.class, () -> new ShapeId(ns, ""));
    }

    @Test
    void nullNameThrows() {
        var ns = new NamespaceId("com.example");
        assertThrows(IllegalArgumentException.class, () -> new ShapeId(ns, null));
    }

    // ── Record equality ────────────────────────────────────────────────────────

    @Test
    void equalityByValue() {
        var a = ShapeId.of("com.example", "Order");
        var b = ShapeId.of("com.example", "Order");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentNamesAreNotEqual() {
        assertNotEquals(
                ShapeId.of("com.example", "Order"),
                ShapeId.of("com.example", "Customer"));
    }

    @Test
    void differentNamespacesAreNotEqual() {
        assertNotEquals(
                ShapeId.of("com.a", "Order"),
                ShapeId.of("com.b", "Order"));
    }
}
