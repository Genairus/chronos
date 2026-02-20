package com.genairus.chronos.core.refs;

import com.genairus.chronos.core.diagnostics.DiagnosticCollector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SymbolRefTest {

    private static final Span SPAN = Span.at("model.chronos", 5, 10);

    // ── unresolved() factory ──────────────────────────────────────────────────

    @Test
    void unresolvedFactoryProducesExpectedState() {
        var qn  = QualifiedName.local("Order");
        var ref = SymbolRef.unresolved(SymbolKind.ENTITY, qn, SPAN);

        assertEquals(SymbolKind.ENTITY, ref.kind());
        assertNull(ref.id(),   "id must be null for an unresolved ref");
        assertEquals(qn, ref.name());
        assertEquals(SPAN, ref.span());
        assertFalse(ref.isResolved());
    }

    @Test
    void unresolvedWithQualifiedName() {
        var qn  = QualifiedName.qualified("com.example", "Order");
        var ref = SymbolRef.unresolved(SymbolKind.ENTITY, qn, SPAN);

        assertFalse(ref.isResolved());
        assertEquals("com.example#Order", ref.name().toString());
    }

    @Test
    void unresolvedWithNullNameThrows() {
        assertThrows(NullPointerException.class,
                () -> SymbolRef.unresolved(SymbolKind.ENTITY, null, SPAN));
    }

    // ── resolved() factory ────────────────────────────────────────────────────

    @Test
    void resolvedFactoryProducesExpectedState() {
        var shapeId = ShapeId.of("com.example.checkout", "Order");
        var ref     = SymbolRef.resolved(SymbolKind.ENTITY, shapeId, SPAN);

        assertEquals(SymbolKind.ENTITY, ref.kind());
        assertEquals(shapeId, ref.id());
        assertNull(ref.name(), "name must be null for a resolved ref");
        assertEquals(SPAN, ref.span());
        assertTrue(ref.isResolved());
    }

    @Test
    void resolvedWithNullIdThrows() {
        assertThrows(NullPointerException.class,
                () -> SymbolRef.resolved(SymbolKind.ENTITY, null, SPAN));
    }

    // ── isResolved() ──────────────────────────────────────────────────────────

    @Test
    void isResolvedReturnsFalseForUnresolved() {
        var ref = SymbolRef.unresolved(SymbolKind.ACTOR, QualifiedName.local("Customer"), SPAN);
        assertFalse(ref.isResolved());
    }

    @Test
    void isResolvedReturnsTrueForResolved() {
        var ref = SymbolRef.resolved(SymbolKind.ACTOR, ShapeId.of("com.example", "Customer"), SPAN);
        assertTrue(ref.isResolved());
    }

    // ── requireResolvedOrReport ───────────────────────────────────────────────

    @Test
    void requireResolvedReturnsShapeIdWhenResolved() {
        var shapeId = ShapeId.of("com.example", "Order");
        var ref     = SymbolRef.resolved(SymbolKind.ENTITY, shapeId, SPAN);

        var col = new DiagnosticCollector();

        ShapeId result = ref.requireResolvedOrReport(col, "CHR-008", "Test prefix");
        assertEquals(shapeId, result);
        assertTrue(col.all().isEmpty(), "no diagnostic should be emitted when resolved");
    }

    @Test
    void requireResolvedReportsAndReturnsNullWhenUnresolved() {
        var qn  = QualifiedName.local("UnknownShape");
        var ref = SymbolRef.unresolved(SymbolKind.ENTITY, qn, SPAN);

        var col = new DiagnosticCollector();

        ShapeId result = ref.requireResolvedOrReport(col, "CHR-008", "Journey actor");

        assertNull(result, "must return null when unresolved");
        assertEquals(1, col.all().size(), "exactly one diagnostic expected");
        assertEquals("CHR-008", col.all().get(0).code());
        assertTrue(col.all().get(0).message().contains("UnknownShape"),
                "message should include the unresolved name; got: " + col.all().get(0).message());
        assertTrue(col.all().get(0).message().startsWith("Journey actor"),
                "message should start with prefix; got: " + col.all().get(0).message());
        assertEquals(SPAN, col.all().get(0).span());
    }

    @Test
    void requireResolvedCanUseCollector() {
        var ref = SymbolRef.unresolved(SymbolKind.ACTOR,
                QualifiedName.local("Ghost"), SPAN);

        var col = new DiagnosticCollector();
        ref.requireResolvedOrReport(col, "CHR-001", "actor field");

        assertEquals(List.of("CHR-001"),
                col.all().stream().map(d -> d.code()).toList());
    }

    // ── toString ─────────────────────────────────────────────────────────────

    @Test
    void toStringForResolvedContainsKindAndId() {
        var shapeId = ShapeId.of("com.example", "Order");
        var ref     = SymbolRef.resolved(SymbolKind.ENTITY, shapeId, SPAN);
        String s = ref.toString();

        assertTrue(s.contains("ENTITY"), "expected ENTITY in: " + s);
        assertTrue(s.contains("com.example#Order"), "expected id in: " + s);
        assertFalse(s.contains("?"), "resolved ref should not have '?' marker");
    }

    @Test
    void toStringForUnresolvedContainsQuestionMarkAndName() {
        var ref = SymbolRef.unresolved(SymbolKind.ACTOR,
                QualifiedName.local("Customer"), SPAN);
        String s = ref.toString();

        assertTrue(s.contains("ACTOR"), "expected ACTOR in: " + s);
        assertTrue(s.contains("?Customer"), "expected '?Customer' in: " + s);
    }

    // ── equals / hashCode ─────────────────────────────────────────────────────

    @Test
    void resolvedRefsWithSameStateAreEqual() {
        var id  = ShapeId.of("com.example", "Order");
        var a   = SymbolRef.resolved(SymbolKind.ENTITY, id, SPAN);
        var b   = SymbolRef.resolved(SymbolKind.ENTITY, id, SPAN);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void unresolvedAndResolvedAreNotEqual() {
        var qn  = QualifiedName.local("Order");
        var id  = ShapeId.of("com.example", "Order");
        var unr = SymbolRef.unresolved(SymbolKind.ENTITY, qn, SPAN);
        var res = SymbolRef.resolved(SymbolKind.ENTITY, id, SPAN);
        assertNotEquals(unr, res);
    }

    // ── All SymbolKind values smoke-test ─────────────────────────────────────

    @Test
    void allSymbolKindsCanBeUsedInUnresolvedRef() {
        for (SymbolKind kind : SymbolKind.values()) {
            var ref = SymbolRef.unresolved(kind, QualifiedName.local("Foo"), SPAN);
            assertEquals(kind, ref.kind());
            assertFalse(ref.isResolved());
        }
    }
}
