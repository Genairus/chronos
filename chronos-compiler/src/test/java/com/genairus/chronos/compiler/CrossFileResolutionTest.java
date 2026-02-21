package com.genairus.chronos.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4 — cross-file type and cross-link resolution via {@link ChronosCompiler#compileAll}.
 *
 * <p>Verifies that the global compilation pipeline resolves named type references
 * and cross-link references (actor, entity) declared in one file and used in another,
 * after proper {@code use} import binding.
 */
class CrossFileResolutionTest {

    // ── Test 1: Cross-file type resolution via use import ─────────────────────

    @Test
    void crossFileTypeRef_resolvesViaUseImport() {
        // file A defines an enum; file B imports and uses it as a field type.
        var unitA = new SourceUnit("a.chronos", """
                namespace shop.domain
                enum OrderStatus { PENDING = 1  SHIPPED = 2 }
                """);
        var unitB = new SourceUnit("b.chronos", """
                namespace shop.ops
                use shop.domain#OrderStatus
                entity Invoice { status: OrderStatus }
                """);

        var result = new ChronosCompiler().compileAll(List.of(unitA, unitB));

        assertTrue(result.parsed(), "Both files should parse");
        assertTrue(result.finalized(),
                "Model should finalize — no CHR-013 expected; diagnostics: " + result.diagnostics());
        assertFalse(result.diagnostics().stream().anyMatch(d -> "CHR-013".equals(d.code())),
                "No CHR-013 expected when type resolves via import; got: " + result.diagnostics());
    }

    // ── Test 2: Cross-file actor reference via use import ─────────────────────

    @Test
    void crossFileActorRef_resolvesViaUseImport() {
        // file A defines an actor; file B imports and uses it in a journey.
        var unitA = new SourceUnit("a.chronos", """
                namespace acme.domain
                actor Customer
                """);
        var unitB = new SourceUnit("b.chronos", """
                namespace acme.ops
                use acme.domain#Customer
                entity Product { id: String }
                journey PlaceOrder {
                    actor: Customer
                    steps: [
                        step DoIt {
                            action: "User places order"
                            expectation: "Order confirmed"
                        }
                    ]
                    outcomes: {
                        success: "Order placed"
                    }
                }
                """);

        var result = new ChronosCompiler().compileAll(List.of(unitA, unitB));

        assertTrue(result.parsed(), "Both files should parse");
        assertFalse(result.diagnostics().stream().anyMatch(d -> "CHR-008".equals(d.code())),
                "No CHR-008 expected when actor resolves via import; got: " + result.diagnostics());
    }

    // ── Test 3: Same-namespace type — no use import needed ────────────────────

    @Test
    void sameNamespace_typeResolvesWithoutImport() {
        // Two files in the same namespace: entity fields referencing types from the other file
        // should resolve via the global same-namespace fallback.
        var unitA = new SourceUnit("a.chronos", """
                namespace com.example
                enum Status { ACTIVE = 1 }
                """);
        var unitB = new SourceUnit("b.chronos", """
                namespace com.example
                entity Item { status: Status }
                """);

        var result = new ChronosCompiler().compileAll(List.of(unitA, unitB));

        assertTrue(result.parsed(), "Both files should parse");
        assertTrue(result.finalized(),
                "Same-namespace types should resolve via global fallback; diagnostics: "
                        + result.diagnostics());
        assertFalse(result.diagnostics().stream().anyMatch(d -> "CHR-013".equals(d.code())),
                "No CHR-013 expected for same-namespace type; got: " + result.diagnostics());
    }

    // ── Test 4: Unknown cross-file type still emits CHR-013 ───────────────────

    @Test
    void crossFileUnknownType_stillEmitsChr013() {
        // file B references a type that does not exist anywhere
        var unitA = new SourceUnit("a.chronos", """
                namespace shop.domain
                entity Product { id: String }
                """);
        var unitB = new SourceUnit("b.chronos", """
                namespace shop.ops
                entity Invoice { product: GhostType }
                """);

        var result = new ChronosCompiler().compileAll(List.of(unitA, unitB));

        assertTrue(result.parsed(), "Both files should parse");
        assertFalse(result.finalized(), "CHR-013 is an ERROR so finalized must be false");
        assertTrue(result.diagnostics().stream().anyMatch(d -> "CHR-013".equals(d.code())),
                "CHR-013 expected for unknown cross-file type; got: " + result.diagnostics());
    }
}
