package com.genairus.chronos.compiler;

import com.genairus.chronos.compiler.util.IrRefWalker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 5 — compilation-unit finalization invariant tests.
 *
 * <p>Verifies that {@code compileAll.finalized() == true} implies
 * {@link IrRefWalker#findUnresolvedRefs} is empty for every model in the unit,
 * and that any unresolved reference in any file prevents finalization.
 *
 * <p>Also verifies that {@code CHR-012} diagnostics are emitted with
 * the correct source path and in stable (kind → name → span) order.
 */
class MultiFileFinalizeInvariantTest {

    // ── Test 1: finalized => no unresolved refs across all files ──────────────

    @Test
    void finalizedImpliesNoUnresolvedRefsAcrossAllFiles() {
        // unitA defines types; unitB imports and uses them as field types.
        var unitA = new SourceUnit("a.chronos", """
                namespace shop.domain
                enum OrderStatus { PENDING = 1  SHIPPED = 2 }
                entity Product { id: String }
                """);
        var unitB = new SourceUnit("b.chronos", """
                namespace shop.ops
                use shop.domain#OrderStatus
                use shop.domain#Product
                entity Order {
                    status: OrderStatus
                    product: Product
                }
                """);

        var result = new ChronosCompiler().compileAll(List.of(unitA, unitB));

        assertTrue(result.parsed(), "all files should parse");
        assertTrue(result.finalized(),
                "all refs resolve → finalized must be true; diagnostics: " + result.diagnostics());
        assertNotNull(result.unitOrNull());

        for (var model : result.unitOrNull().models()) {
            var unresolved = IrRefWalker.findUnresolvedRefs(model);
            assertTrue(unresolved.isEmpty(),
                    "finalized == true implies no unresolved refs in model '"
                            + model.namespace() + "'; found: " + unresolved);
        }
    }

    // ── Test 2: unresolved ref in any file prevents unit finalization ──────────

    @Test
    void unresolvedRefInAnyFilePreventsUnitFinalization() {
        var unitA = new SourceUnit("a.chronos", """
                namespace a
                entity Order { status: MissingType }
                """);
        var unitB = new SourceUnit("b.chronos", """
                namespace b
                entity Foo { id: String }
                """);

        var result = new ChronosCompiler().compileAll(List.of(unitA, unitB));

        assertTrue(result.parsed(), "both files should parse");
        assertFalse(result.finalized(),
                "unresolved ref in any file must prevent unit finalization");

        // CHR-012 must be emitted for the unresolved type in unitA
        boolean hasChr012 = result.diagnostics().stream()
                .anyMatch(d -> "CHR-012".equals(d.code()));
        assertTrue(hasChr012,
                "CHR-012 expected for unresolved ref; diagnostics: " + result.diagnostics());

        // The CHR-012 diagnostic must carry the source file path
        result.diagnostics().stream()
                .filter(d -> "CHR-012".equals(d.code()))
                .findFirst()
                .ifPresent(d -> assertEquals("a.chronos", d.pathOrNull(),
                        "CHR-012 must carry the source file path"));
    }

    // ── Test 3: stable ordering of CHR-012 diagnostics ────────────────────────

    @Test
    void chr012Diagnostics_areStablyOrdered() {
        // Three unresolved type refs in two entities: TypeZ, TypeA, TypeB (traversal order).
        // After sort by (kind, name): TypeA < TypeB < TypeZ.
        var unit = new SourceUnit("mixed.chronos", """
                namespace test
                entity Alpha {
                    x: TypeZ
                    y: TypeA
                }
                entity Beta {
                    z: TypeB
                }
                """);

        var result = new ChronosCompiler().compileAll(List.of(unit));

        assertFalse(result.finalized(), "unresolved refs must prevent finalization");

        List<String> chr012Names = result.diagnostics().stream()
                .filter(d -> "CHR-012".equals(d.code()))
                .map(d -> {
                    // extract name from: "Unresolved reference '<name>' (expected kind: ...)"
                    String msg = d.message();
                    int start = msg.indexOf('\'') + 1;
                    int end   = msg.indexOf('\'', start);
                    return msg.substring(start, end);
                })
                .toList();

        // All three refs have kind TYPE; sorted alphabetically: TypeA, TypeB, TypeZ.
        assertEquals(List.of("TypeA", "TypeB", "TypeZ"), chr012Names,
                "CHR-012 diagnostics must be in stable (kind, name) order; got: " + chr012Names);
    }

    // ── Test 4: unit finalized only when ALL files are clean ──────────────────

    @Test
    void unitFinalizedOnlyWhenAllFilesClean() {
        var unitA = new SourceUnit("a.chronos", """
                namespace good
                entity Clean { id: String }
                """);
        var unitB = new SourceUnit("b.chronos", """
                namespace bad
                entity Broken { x: GhostType }
                """);

        var result = new ChronosCompiler().compileAll(List.of(unitA, unitB));

        assertTrue(result.parsed(), "both files should parse");
        assertFalse(result.finalized(),
                "unit must not be finalized when any file has unresolved refs");
        assertNotNull(result.unitOrNull(), "unit is present even when not finalized");

        var goodModel = result.unitOrNull().models().stream()
                .filter(m -> "good".equals(m.namespace()))
                .findFirst().orElseThrow();
        assertTrue(IrRefWalker.findUnresolvedRefs(goodModel).isEmpty(),
                "unitA model (namespace 'good') must have no unresolved refs");

        var badModel = result.unitOrNull().models().stream()
                .filter(m -> "bad".equals(m.namespace()))
                .findFirst().orElseThrow();
        assertFalse(IrRefWalker.findUnresolvedRefs(badModel).isEmpty(),
                "unitB model (namespace 'bad') must have unresolved refs");
    }
}
