package com.genairus.chronos.compiler;

import com.genairus.chronos.compiler.util.IrRefWalker;
import com.genairus.chronos.core.refs.*;
import com.genairus.chronos.ir.model.IrModel;
import com.genairus.chronos.ir.types.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link IrRefWalker}.
 *
 * <p>Verifies that the reflective walker:
 * <ul>
 *   <li>finds every {@link SymbolRef} in all shape types</li>
 *   <li>handles refs inside lists, maps, and optional containers</li>
 *   <li>handles {@code null} actorRef on {@link JourneyDef}</li>
 *   <li>deduplicates by object identity (same instance in two shapes → one entry)</li>
 *   <li>distinguishes resolved vs. unresolved refs correctly</li>
 * </ul>
 */
class IrRefWalkerTest {

    private static final Span SPAN = Span.UNKNOWN;

    // ── Empty / no-ref cases ──────────────────────────────────────────────────

    @Test
    void nullRoot_returnsEmpty() {
        assertTrue(IrRefWalker.findAllRefs(null).isEmpty());
    }

    @Test
    void emptyModel_returnsEmpty() {
        var model = new IrModel("com.test", List.of(), List.of());
        assertTrue(IrRefWalker.findAllRefs(model).isEmpty());
        assertTrue(IrRefWalker.findUnresolvedRefs(model).isEmpty());
    }

    @Test
    void entityWithNoRefs_returnsEmpty() {
        var entity = new EntityDef("Order", List.of(), List.of(),
                Optional.empty(), List.of(), List.of(), SPAN);
        var model = new IrModel("com.test", List.of(), List.of(entity));
        assertTrue(IrRefWalker.findAllRefs(model).isEmpty());
    }

    // ── JourneyDef.actorRef ───────────────────────────────────────────────────

    @Test
    void journey_nullActorRef_noRef() {
        var journey = new JourneyDef("J", List.of(), List.of(),
                /* actorRef */ null,
                List.of(), List.of(), Map.of(), null, SPAN);
        var model = new IrModel("com.test", List.of(), List.of(journey));
        assertTrue(IrRefWalker.findAllRefs(model).isEmpty());
    }

    @Test
    void journey_unresolvedActorRef_foundAndUnresolved() {
        var actorRef = SymbolRef.unresolved(SymbolKind.ACTOR,
                QualifiedName.local("Missing"), SPAN);
        var journey = new JourneyDef("J", List.of(), List.of(), actorRef,
                List.of(), List.of(), Map.of(), null, SPAN);
        var model = new IrModel("com.test", List.of(), List.of(journey));

        var all = IrRefWalker.findAllRefs(model);
        assertEquals(1, all.size());
        assertSame(actorRef, all.get(0));

        var unresolved = IrRefWalker.findUnresolvedRefs(model);
        assertEquals(1, unresolved.size());
        assertSame(actorRef, unresolved.get(0));
    }

    @Test
    void journey_resolvedActorRef_foundButNotUnresolved() {
        var actorRef = SymbolRef.resolved(SymbolKind.ACTOR,
                ShapeId.of("com.test", "Customer"), SPAN);
        var journey = new JourneyDef("J", List.of(), List.of(), actorRef,
                List.of(), List.of(), Map.of(), null, SPAN);
        var model = new IrModel("com.test", List.of(), List.of(journey));

        assertEquals(1, IrRefWalker.findAllRefs(model).size());
        assertTrue(IrRefWalker.findUnresolvedRefs(model).isEmpty());
    }

    // ── RelationshipDef refs ──────────────────────────────────────────────────

    @Test
    void relationship_bothRefsFound() {
        var fromRef = SymbolRef.resolved(SymbolKind.ENTITY,
                ShapeId.of("com.test", "Order"), SPAN);
        var toRef = SymbolRef.unresolved(SymbolKind.ENTITY,
                QualifiedName.local("Missing"), SPAN);
        var rel = new RelationshipDef("R", List.of(), List.of(),
                fromRef, toRef,
                Cardinality.ONE_TO_MANY, Optional.empty(), Optional.empty(), SPAN);
        var model = new IrModel("com.test", List.of(), List.of(rel));

        var all = IrRefWalker.findAllRefs(model);
        assertEquals(2, all.size());
        assertTrue(all.contains(fromRef));
        assertTrue(all.contains(toRef));

        var unresolved = IrRefWalker.findUnresolvedRefs(model);
        assertEquals(1, unresolved.size());
        assertSame(toRef, unresolved.get(0));
    }

    @Test
    void relationship_bothResolved_noUnresolved() {
        var fromRef = SymbolRef.resolved(SymbolKind.ENTITY,
                ShapeId.of("com.test", "Order"), SPAN);
        var toRef = SymbolRef.resolved(SymbolKind.ENTITY,
                ShapeId.of("com.test", "Item"), SPAN);
        var rel = new RelationshipDef("R", List.of(), List.of(),
                fromRef, toRef,
                Cardinality.ONE_TO_MANY, Optional.empty(), Optional.empty(), SPAN);
        var model = new IrModel("com.test", List.of(), List.of(rel));

        assertEquals(2, IrRefWalker.findAllRefs(model).size());
        assertTrue(IrRefWalker.findUnresolvedRefs(model).isEmpty());
    }

    // ── Refs inside a List<IrShape> (tests container traversal) ──────────────

    @Test
    void multipleShapesInList_allRefsFound() {
        var ref1 = SymbolRef.unresolved(SymbolKind.ACTOR,
                QualifiedName.local("A1"), SPAN);
        var ref2 = SymbolRef.unresolved(SymbolKind.ACTOR,
                QualifiedName.local("A2"), SPAN);
        var j1 = new JourneyDef("J1", List.of(), List.of(), ref1,
                List.of(), List.of(), Map.of(), null, SPAN);
        var j2 = new JourneyDef("J2", List.of(), List.of(), ref2,
                List.of(), List.of(), Map.of(), null, SPAN);
        var model = new IrModel("com.test", List.of(), List.of(j1, j2));

        var unresolved = IrRefWalker.findUnresolvedRefs(model);
        assertEquals(2, unresolved.size());
        assertTrue(unresolved.contains(ref1));
        assertTrue(unresolved.contains(ref2));
    }

    // ── Refs inside a Map<String, Variant> (tests map-value traversal) ────────

    @Test
    void journeyWithVariants_mapValuesTraversed() {
        // JourneyDef.variants() is Map<String, Variant>; Variant contains steps.
        // Variants don't directly carry SymbolRefs today, but the map itself
        // must be traversed.  Use the actorRef to confirm the journey is reached.
        var actorRef = SymbolRef.resolved(SymbolKind.ACTOR,
                ShapeId.of("com.test", "Customer"), SPAN);

        var variant = new Variant("AltFlow", "SomeError",
                List.of(), null, SPAN);
        var journey = new JourneyDef("J", List.of(), List.of(), actorRef,
                List.of(), List.of(),
                Map.of("AltFlow", variant),   // <-- map with a variant value
                null, SPAN);
        var model = new IrModel("com.test", List.of(), List.of(journey));

        // Walker must enter the map values, reach the variant, and not crash.
        var all = IrRefWalker.findAllRefs(model);
        assertEquals(1, all.size()); // only actorRef; Variant has no SymbolRef
        assertSame(actorRef, all.get(0));
    }

    // ── Deduplication by identity ─────────────────────────────────────────────

    @Test
    void sameRefInstanceInTwoShapes_reportedOnce() {
        var sharedRef = SymbolRef.unresolved(SymbolKind.ACTOR,
                QualifiedName.local("Ghost"), SPAN);
        var j1 = new JourneyDef("J1", List.of(), List.of(), sharedRef,
                List.of(), List.of(), Map.of(), null, SPAN);
        var j2 = new JourneyDef("J2", List.of(), List.of(), sharedRef,
                List.of(), List.of(), Map.of(), null, SPAN);
        var model = new IrModel("com.test", List.of(), List.of(j1, j2));

        // Object identity dedup: same instance → appears once in result.
        assertEquals(1, IrRefWalker.findUnresolvedRefs(model).size());
    }

    @Test
    void twoDistinctButEqualRefs_reportedSeparately() {
        // Two *different* SymbolRef objects with the same logical value.
        var ref1 = SymbolRef.unresolved(SymbolKind.ACTOR,
                QualifiedName.local("Ghost"), SPAN);
        var ref2 = SymbolRef.unresolved(SymbolKind.ACTOR,
                QualifiedName.local("Ghost"), SPAN);
        assertEquals(ref1, ref2, "precondition: equal by value");
        assertNotSame(ref1, ref2, "precondition: distinct instances");

        var j1 = new JourneyDef("J1", List.of(), List.of(), ref1,
                List.of(), List.of(), Map.of(), null, SPAN);
        var j2 = new JourneyDef("J2", List.of(), List.of(), ref2,
                List.of(), List.of(), Map.of(), null, SPAN);
        var model = new IrModel("com.test", List.of(), List.of(j1, j2));

        // Distinct instances → both reported (identity-based visited set).
        assertEquals(2, IrRefWalker.findUnresolvedRefs(model).size());
    }

    // ── Mixed model ───────────────────────────────────────────────────────────

    @Test
    void mixedModel_correctSplit() {
        var resolvedActor = SymbolRef.resolved(SymbolKind.ACTOR,
                ShapeId.of("com.test", "Customer"), SPAN);
        var resolvedFrom = SymbolRef.resolved(SymbolKind.ENTITY,
                ShapeId.of("com.test", "Order"), SPAN);
        var unresolvedTo = SymbolRef.unresolved(SymbolKind.ENTITY,
                QualifiedName.local("Ghost"), SPAN);

        var journey = new JourneyDef("J", List.of(), List.of(), resolvedActor,
                List.of(), List.of(), Map.of(), null, SPAN);
        var rel = new RelationshipDef("R", List.of(), List.of(),
                resolvedFrom, unresolvedTo,
                Cardinality.ONE_TO_MANY, Optional.empty(), Optional.empty(), SPAN);
        var entity = new EntityDef("Order", List.of(), List.of(),
                Optional.empty(), List.of(), List.of(), SPAN);

        var model = new IrModel("com.test", List.of(), List.of(journey, rel, entity));

        // 3 refs total: resolvedActor, resolvedFrom, unresolvedTo
        assertEquals(3, IrRefWalker.findAllRefs(model).size());

        // 1 unresolved: unresolvedTo
        var unresolved = IrRefWalker.findUnresolvedRefs(model);
        assertEquals(1, unresolved.size());
        assertSame(unresolvedTo, unresolved.get(0));
    }

    // ── Non-IR roots ──────────────────────────────────────────────────────────

    @Test
    void plainStringRoot_returnsEmpty() {
        assertTrue(IrRefWalker.findAllRefs("not an IR object").isEmpty());
    }

    @Test
    void isolatedSymbolRef_collectedDirectly() {
        var ref = SymbolRef.unresolved(SymbolKind.ENTITY,
                QualifiedName.local("Foo"), SPAN);
        var all = IrRefWalker.findAllRefs(ref);
        assertEquals(1, all.size());
        assertSame(ref, all.get(0));
    }
}
