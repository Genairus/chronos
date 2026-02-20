package com.genairus.chronos.core.refs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpanTest {

    // ── UNKNOWN constant / factory ─────────────────────────────────────────────

    @Test
    void unknownConstantHasUnknownSourceAndZeroPositions() {
        Span u = Span.UNKNOWN;
        assertEquals("<unknown>", u.sourceName());
        assertEquals(0, u.startLine());
        assertEquals(0, u.startCol());
        assertEquals(0, u.endLine());
        assertEquals(0, u.endCol());
    }

    @Test
    void unknownFactoryUsesSuppliedSourceName() {
        Span u = Span.unknown("checkout.chronos");
        assertEquals("checkout.chronos", u.sourceName());
        assertTrue(u.isUnknown());
    }

    @Test
    void unknownConstantIsUnknown() {
        assertTrue(Span.UNKNOWN.isUnknown());
    }

    // ── at() factory ───────────────────────────────────────────────────────────

    @Test
    void atFactoryProducesSinglePointSpan() {
        Span s = Span.at("model.chronos", 7, 3);
        assertEquals("model.chronos", s.sourceName());
        assertEquals(7, s.startLine());
        assertEquals(3, s.startCol());
        assertEquals(7, s.endLine());   // same as start for a point span
        assertEquals(3, s.endCol());
        assertFalse(s.isUnknown());
    }

    // ── Full constructor ───────────────────────────────────────────────────────

    @Test
    void fullConstructorStoresAllFields() {
        Span s = new Span("order.chronos", 10, 1, 10, 25);
        assertEquals("order.chronos", s.sourceName());
        assertEquals(10, s.startLine());
        assertEquals(1,  s.startCol());
        assertEquals(10, s.endLine());
        assertEquals(25, s.endCol());
    }

    // ── toString ──────────────────────────────────────────────────────────────

    @Test
    void toStringForKnownSpanIsFileColonLineColonCol() {
        Span s = new Span("checkout.chronos", 12, 4, 12, 10);
        assertEquals("checkout.chronos:12:4", s.toString());
    }

    @Test
    void toStringForUnknownSpanContainsUnknownMarker() {
        String str = Span.UNKNOWN.toString();
        assertTrue(str.contains("<unknown>"), "expected '<unknown>' in: " + str);
    }

    @Test
    void toStringForUnknownWithSourceNameContainsSourceName() {
        Span u = Span.unknown("src/model.chronos");
        assertTrue(u.toString().contains("src/model.chronos"));
    }

    // ── Record equality ────────────────────────────────────────────────────────

    @Test
    void recordEqualityByValue() {
        Span a = new Span("f.chronos", 1, 1, 1, 5);
        Span b = new Span("f.chronos", 1, 1, 1, 5);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentSourceNamesAreNotEqual() {
        Span a = new Span("a.chronos", 1, 1, 1, 1);
        Span b = new Span("b.chronos", 1, 1, 1, 1);
        assertNotEquals(a, b);
    }
}
