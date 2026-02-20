package com.genairus.chronos.cli;

import com.genairus.chronos.core.refs.QualifiedName;
import com.genairus.chronos.core.refs.Span;
import com.genairus.chronos.core.refs.SymbolKind;
import com.genairus.chronos.core.refs.SymbolRef;
import com.genairus.chronos.ir.model.IrModel;
import com.genairus.chronos.ir.types.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ModelProjectionTest {

    // ── Shape factories ────────────────────────────────────────────────────────

    private static ActorDef actor(String name) {
        return new ActorDef(name, List.of(), List.of(), Optional.empty(), Span.UNKNOWN);
    }

    private static EntityDef entity(String name) {
        return new EntityDef(name, List.of(), List.of(), Optional.empty(), List.of(), List.of(), Span.UNKNOWN);
    }

    private static JourneyDef journey(String name) {
        return new JourneyDef(name, List.of(), List.of(),
                SymbolRef.unresolved(SymbolKind.ACTOR, new QualifiedName(null, "Customer"), Span.UNKNOWN),
                List.of(), List.of(), Map.of(),
                new JourneyOutcomes("done", null, Span.UNKNOWN), Span.UNKNOWN);
    }

    private static IrModel model(IrShape... shapes) {
        return new IrModel("com.example", List.of(), List.of(shapes));
    }

    private static BuildTarget target(List<String> include, List<String> exclude) {
        return new BuildTarget("t", "markdown", "out", include, exclude);
    }

    // ── Include behaviour ──────────────────────────────────────────────────────

    @Test
    void emptyInclude_includesAll() {
        var model = model(actor("Alice"), entity("Order"), journey("Checkout"));
        var result = ModelProjection.apply(model, target(List.of(), List.of()));
        assertEquals(3, result.shapes().size());
    }

    @Test
    void include_exactTypeAndName_filtersToOne() {
        var model = model(actor("Alice"), entity("Order"), journey("Checkout"));
        var result = ModelProjection.apply(model, target(List.of("entity:Order"), List.of()));
        assertEquals(1, result.shapes().size());
        assertEquals("Order", result.shapes().get(0).name());
    }

    @Test
    void include_typeWildcard_includesAllOfType() {
        var model = model(actor("Alice"), actor("Bob"), entity("Order"));
        var result = ModelProjection.apply(model, target(List.of("actor:*"), List.of()));
        assertEquals(2, result.shapes().size());
        assertTrue(result.shapes().stream().allMatch(s -> s instanceof ActorDef));
    }

    @Test
    void include_nameWildcard_includesAnyTypeMatchingName() {
        var model = model(actor("Checkout"), entity("Checkout"), journey("Login"));
        var result = ModelProjection.apply(model, target(List.of("*:Checkout"), List.of()));
        assertEquals(2, result.shapes().size());
        assertTrue(result.shapes().stream().allMatch(s -> "Checkout".equals(s.name())));
    }

    @Test
    void include_starPattern_includesAll() {
        var model = model(actor("A"), entity("B"), journey("C"));
        var result = ModelProjection.apply(model, target(List.of("*"), List.of()));
        assertEquals(3, result.shapes().size());
    }

    // ── Exclude behaviour ──────────────────────────────────────────────────────

    @Test
    void exclude_removesMatchingShapes() {
        var model = model(actor("Alice"), entity("Order"), journey("Checkout"));
        var result = ModelProjection.apply(model, target(List.of(), List.of("entity:Order")));
        assertEquals(2, result.shapes().size());
        assertTrue(result.shapes().stream().noneMatch(s -> "Order".equals(s.name())));
    }

    @Test
    void exclude_typeWildcard_removesAllOfType() {
        var model = model(actor("Alice"), actor("Bob"), entity("Order"));
        var result = ModelProjection.apply(model, target(List.of(), List.of("actor:*")));
        assertEquals(1, result.shapes().size());
        assertInstanceOf(EntityDef.class, result.shapes().get(0));
    }

    // ── Include + exclude (exclude wins for intersection) ─────────────────────

    @Test
    void includeAndExclude_excludeWinsForIntersection() {
        // Include all actors, but exclude actor Alice specifically
        var model = model(actor("Alice"), actor("Bob"), entity("Order"));
        var result = ModelProjection.apply(model,
                target(List.of("actor:*"), List.of("actor:Alice")));
        assertEquals(1, result.shapes().size());
        assertEquals("Bob", result.shapes().get(0).name());
    }

    @Test
    void include_noMatch_producesEmptyModel() {
        var model = model(actor("Alice"), entity("Order"));
        var result = ModelProjection.apply(model, target(List.of("journey:*"), List.of()));
        assertTrue(result.shapes().isEmpty());
    }

    // ── Metadata preservation ─────────────────────────────────────────────────

    @Test
    void apply_preservesNamespaceAndImports() {
        var original = new IrModel("com.test",
                List.of(new UseDecl("com.other", "Foo", Span.UNKNOWN)),
                List.of(actor("A")));
        var result = ModelProjection.apply(original, target(List.of(), List.of()));
        assertEquals("com.test", result.namespace());
        assertEquals(1, result.imports().size());
    }

    // ── typeName helper ────────────────────────────────────────────────────────

    @Test
    void typeName_returnsCorrectStrings() {
        assertEquals("actor",   ModelProjection.typeName(actor("X")));
        assertEquals("entity",  ModelProjection.typeName(entity("X")));
        assertEquals("journey", ModelProjection.typeName(journey("X")));
    }
}
