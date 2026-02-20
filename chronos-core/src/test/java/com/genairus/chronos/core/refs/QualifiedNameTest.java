package com.genairus.chronos.core.refs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QualifiedNameTest {

    // ── local() factory ────────────────────────────────────────────────────────

    @Test
    void localFactoryHasNullNamespace() {
        var qn = QualifiedName.local("Order");
        assertNull(qn.namespaceOrNull());
        assertEquals("Order", qn.name());
    }

    @Test
    void localIsNotQualified() {
        assertFalse(QualifiedName.local("Order").isQualified());
    }

    @Test
    void localToStringIsJustTheName() {
        assertEquals("Order", QualifiedName.local("Order").toString());
    }

    // ── qualified() factory ───────────────────────────────────────────────────

    @Test
    void qualifiedFactoryStoresNamespaceAndName() {
        var qn = QualifiedName.qualified("com.example.checkout", "Order");
        assertEquals("com.example.checkout", qn.namespaceOrNull());
        assertEquals("Order", qn.name());
    }

    @Test
    void qualifiedIsQualified() {
        assertTrue(QualifiedName.qualified("com.example", "Order").isQualified());
    }

    @Test
    void qualifiedToStringUsesHashSeparator() {
        var qn = QualifiedName.qualified("com.example.checkout", "Order");
        assertEquals("com.example.checkout#Order", qn.toString());
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    void blankNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> QualifiedName.local(""));
    }

    @Test
    void nullNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> QualifiedName.local(null));
    }

    @Test
    void qualifiedWithNullNamespaceThrows() {
        assertThrows(NullPointerException.class,
                () -> QualifiedName.qualified(null, "Order"));
    }

    // ── Record equality ────────────────────────────────────────────────────────

    @Test
    void localEqualityByValue() {
        var a = QualifiedName.local("Foo");
        var b = QualifiedName.local("Foo");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void qualifiedEqualityByValue() {
        var a = QualifiedName.qualified("ns", "Foo");
        var b = QualifiedName.qualified("ns", "Foo");
        assertEquals(a, b);
    }

    @Test
    void localAndQualifiedAreNotEqual() {
        assertNotEquals(
                QualifiedName.local("Foo"),
                QualifiedName.qualified("ns", "Foo"));
    }
}
