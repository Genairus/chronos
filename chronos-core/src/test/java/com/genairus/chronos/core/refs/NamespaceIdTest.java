package com.genairus.chronos.core.refs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NamespaceIdTest {

    // ── Valid construction ─────────────────────────────────────────────────────

    @Test
    void storesValueVerbatim() {
        var ns = new NamespaceId("com.example.checkout");
        assertEquals("com.example.checkout", ns.value());
    }

    @Test
    void singleSegmentNamespaceIsValid() {
        var ns = new NamespaceId("root");
        assertEquals("root", ns.value());
    }

    // ── toString ──────────────────────────────────────────────────────────────

    @Test
    void toStringReturnsDottedPath() {
        var ns = new NamespaceId("com.genairus.chronos");
        assertEquals("com.genairus.chronos", ns.toString());
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    void nullValueThrows() {
        assertThrows(IllegalArgumentException.class, () -> new NamespaceId(null));
    }

    @Test
    void emptyStringThrows() {
        assertThrows(IllegalArgumentException.class, () -> new NamespaceId(""));
    }

    @Test
    void blankStringThrows() {
        assertThrows(IllegalArgumentException.class, () -> new NamespaceId("   "));
    }

    // ── Record equality ────────────────────────────────────────────────────────

    @Test
    void equalityByValue() {
        var a = new NamespaceId("com.example");
        var b = new NamespaceId("com.example");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentValuesAreNotEqual() {
        assertNotEquals(new NamespaceId("com.a"), new NamespaceId("com.b"));
    }
}
