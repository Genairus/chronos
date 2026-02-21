package com.genairus.chronos.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Phase 2 global symbol indexing: {@link IndexCompilationUnitPhase} and
 * cross-file duplicate detection via {@code CHR-014}.
 *
 * <p>All assertions are through the public {@link ChronosCompiler#compileAll} API.
 */
class GlobalIndexTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Minimal valid source with a single entity in a given namespace. */
    private static SourceUnit entityUnit(String ns, String entityName, String filename) {
        return new SourceUnit(filename, """
                namespace %s

                entity %s { id: String }
                """.formatted(ns, entityName));
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void sameNameDifferentNamespaces_noDuplicateError() {
        // file A: x#Order   file B: y#Order  — different fqNames, no conflict
        var unitA = entityUnit("x", "Order", "a.chronos");
        var unitB = entityUnit("y", "Order", "b.chronos");

        var result = new ChronosCompiler().compileAll(List.of(unitA, unitB));

        assertTrue(result.parsed(),   "both files should parse");
        assertTrue(result.finalized(), "no errors expected");
        assertTrue(result.diagnostics().stream().noneMatch(d -> "CHR-014".equals(d.code())),
                "CHR-014 must not fire for different namespaces; got: " + result.diagnostics());
    }

    @Test
    void duplicateFqNameAcrossFiles_emitsChr014() {
        // file A: x#Order   file B: x#Order  — same fqName, duplicate
        var unitA = entityUnit("x", "Order", "a.chronos");
        var unitB = entityUnit("x", "Order", "b.chronos");

        var result = new ChronosCompiler().compileAll(List.of(unitA, unitB));

        assertTrue(result.parsed(), "both files should parse despite duplicate");
        assertFalse(result.finalized(), "CHR-014 is an ERROR so finalized must be false");

        long chr014Count = result.diagnostics().stream()
                .filter(d -> "CHR-014".equals(d.code()))
                .count();
        assertEquals(1, chr014Count,
                "expected exactly one CHR-014; got: " + result.diagnostics());

        // The CHR-014 message must name the conflicting fqName
        String msg = result.diagnostics().stream()
                .filter(d -> "CHR-014".equals(d.code()))
                .findFirst().orElseThrow()
                .message();
        assertTrue(msg.contains("x#Order"),
                "CHR-014 message should contain the fqName 'x#Order'; got: " + msg);
    }

    @Test
    void duplicateFqName_unitStillPresent() {
        // Even with CHR-014, unitOrNull should be non-null (all files parsed)
        var unitA = entityUnit("x", "Order", "a.chronos");
        var unitB = entityUnit("x", "Order", "b.chronos");

        var result = new ChronosCompiler().compileAll(List.of(unitA, unitB));

        assertTrue(result.parsed());
        assertNotNull(result.unitOrNull(),
                "unitOrNull must be non-null when all files parse (even with CHR-014)");
        assertEquals(2, result.unitOrNull().models().size(),
                "both models should be present even with a duplicate-definition error");
    }

    @Test
    void multipleDuplicatesAcrossFiles_eachEmitsChr014() {
        // file A defines x#Alpha, x#Beta; file B also defines x#Alpha, x#Beta
        var unitA = new SourceUnit("a.chronos", """
                namespace x
                entity Alpha { id: String }
                entity Beta  { id: String }
                """);
        var unitB = new SourceUnit("b.chronos", """
                namespace x
                entity Alpha { id: String }
                entity Beta  { id: String }
                """);

        var result = new ChronosCompiler().compileAll(List.of(unitA, unitB));

        assertTrue(result.parsed());
        long chr014Count = result.diagnostics().stream()
                .filter(d -> "CHR-014".equals(d.code()))
                .count();
        assertEquals(2, chr014Count,
                "expected one CHR-014 per duplicate fqName; got: " + result.diagnostics());
    }

    @Test
    void parseFailureInOneFile_returnsNotParsedBeforeIndexing() {
        var valid   = entityUnit("x", "Order", "good.chronos");
        var invalid = new SourceUnit("bad.chronos", """
                namespace x
                entity { id: String }
                """);

        var result = new ChronosCompiler().compileAll(List.of(valid, invalid));

        assertFalse(result.parsed(),    "parsed must be false when any file has a syntax error");
        assertFalse(result.finalized(), "finalized must be false when parsed is false");
        assertNull(result.unitOrNull(), "unitOrNull must be null when parsed is false");
    }
}
