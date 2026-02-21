package com.genairus.chronos.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Phase 3 import binding: {@link com.genairus.chronos.compiler.imports.ImportResolver}
 * wired through {@link ChronosCompiler#compileAll}.
 *
 * <p>Verifies CHR-016 (unknown import target) and CHR-017 (ambiguous simple-name
 * binding) are reported, and that valid imports produce no binding errors.
 */
class ImportBindingTest {

    // ── Test 1: Unknown import target → CHR-016 ───────────────────────────────

    @Test
    void unknownImportTarget_emitsChr016() {
        // File B imports "nonexistent#Order" — nothing defines Order in namespace "nonexistent".
        var unitA = new SourceUnit("a.chronos", """
                namespace a
                entity Foo { id: String }
                """);
        var unitB = new SourceUnit("b.chronos", """
                namespace b
                use nonexistent#Order
                entity Bar { id: String }
                """);

        var result = new ChronosCompiler().compileAll(List.of(unitA, unitB));

        assertTrue(result.parsed(), "both files should parse");
        assertFalse(result.finalized(), "CHR-016 is an ERROR so finalized must be false");

        long chr016Count = result.diagnostics().stream()
                .filter(d -> "CHR-016".equals(d.code()))
                .count();
        assertEquals(1, chr016Count,
                "expected exactly one CHR-016; got: " + result.diagnostics());

        String msg = result.diagnostics().stream()
                .filter(d -> "CHR-016".equals(d.code()))
                .findFirst().orElseThrow()
                .message();
        assertTrue(msg.contains("nonexistent#Order"),
                "CHR-016 message should name the unknown import; got: " + msg);
    }

    @Test
    void unknownImportTarget_unitStillPresent() {
        // parsed=true, so unitOrNull is non-null even when imports fail
        var unitA = new SourceUnit("a.chronos", "namespace a\nentity Foo { id: String }");
        var unitB = new SourceUnit("b.chronos", """
                namespace b
                use nonexistent#Order
                entity Bar { id: String }
                """);

        var result = new ChronosCompiler().compileAll(List.of(unitA, unitB));

        assertTrue(result.parsed());
        assertNotNull(result.unitOrNull(),
                "unitOrNull must be non-null when all files parse");
    }

    // ── Test 2: Ambiguous import (two uses bind same simpleName) → CHR-017 ────

    @Test
    void ambiguousImport_emitsChr017() {
        // file A: x#Order   file B: y#Order   file C: imports both
        var unitA = new SourceUnit("a.chronos", "namespace x\nentity Order { id: String }");
        var unitB = new SourceUnit("b.chronos", "namespace y\nentity Order { id: String }");
        var unitC = new SourceUnit("c.chronos", """
                namespace z
                use x#Order
                use y#Order
                entity Foo { id: String }
                """);

        var result = new ChronosCompiler().compileAll(List.of(unitA, unitB, unitC));

        assertTrue(result.parsed(), "all three files should parse");
        assertFalse(result.finalized(), "CHR-017 is an ERROR so finalized must be false");

        long chr017Count = result.diagnostics().stream()
                .filter(d -> "CHR-017".equals(d.code()))
                .count();
        assertEquals(1, chr017Count,
                "expected exactly one CHR-017; got: " + result.diagnostics());

        String msg = result.diagnostics().stream()
                .filter(d -> "CHR-017".equals(d.code()))
                .findFirst().orElseThrow()
                .message();
        assertTrue(msg.contains("Order"),
                "CHR-017 message should name the conflicting simple name; got: " + msg);
        assertTrue(msg.contains("x#Order") && msg.contains("y#Order"),
                "CHR-017 message should name both competing targets; got: " + msg);
    }

    // ── Test 3: Valid import binds cleanly — no CHR-016 / CHR-017 ─────────────

    @Test
    void validImport_noBindingErrors() {
        // file A: x#Status (enum)   file B: imports x#Status
        var unitA = new SourceUnit("a.chronos", """
                namespace x
                enum Status { ACTIVE = 1 }
                """);
        var unitB = new SourceUnit("b.chronos", """
                namespace y
                use x#Status
                entity Order { id: String }
                """);

        var result = new ChronosCompiler().compileAll(List.of(unitA, unitB));

        assertTrue(result.parsed(), "both files should parse");

        boolean hasChr016 = result.diagnostics().stream()
                .anyMatch(d -> "CHR-016".equals(d.code()));
        boolean hasChr017 = result.diagnostics().stream()
                .anyMatch(d -> "CHR-017".equals(d.code()));

        assertFalse(hasChr016, "no CHR-016 expected for valid import; got: " + result.diagnostics());
        assertFalse(hasChr017, "no CHR-017 expected for valid import; got: " + result.diagnostics());
    }

    @Test
    void identicalDuplicateImport_notAmbiguous() {
        // Two identical `use x#Order` in one file — same target, not ambiguous.
        var unitA = new SourceUnit("a.chronos", "namespace x\nentity Order { id: String }");
        var unitB = new SourceUnit("b.chronos", """
                namespace y
                use x#Order
                use x#Order
                entity Foo { id: String }
                """);

        var result = new ChronosCompiler().compileAll(List.of(unitA, unitB));

        assertTrue(result.parsed());
        assertFalse(result.diagnostics().stream().anyMatch(d -> "CHR-017".equals(d.code())),
                "identical re-import must not produce CHR-017; got: " + result.diagnostics());
        assertFalse(result.diagnostics().stream().anyMatch(d -> "CHR-016".equals(d.code())),
                "identical re-import must not produce CHR-016; got: " + result.diagnostics());
    }

    // ── Test 4: No import declarations → no binding diagnostics ─────────────

    @Test
    void noImports_noBindingDiagnostics() {
        var unitA = new SourceUnit("a.chronos", "namespace a\nentity Foo { id: String }");
        var unitB = new SourceUnit("b.chronos", "namespace b\nentity Bar { id: String }");

        var result = new ChronosCompiler().compileAll(List.of(unitA, unitB));

        assertTrue(result.parsed());
        assertFalse(result.diagnostics().stream()
                        .anyMatch(d -> "CHR-016".equals(d.code()) || "CHR-017".equals(d.code())),
                "no import diagnostics expected when no use declarations present; got: "
                        + result.diagnostics());
    }
}
