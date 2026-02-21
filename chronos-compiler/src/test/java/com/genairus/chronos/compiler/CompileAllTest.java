package com.genairus.chronos.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ChronosCompiler#compileAll(List)}.
 */
class CompileAllTest {

    /** Minimal valid source: entity + actor + journey with required fields. */
    private static SourceUnit validUnit(String ns, String filename) {
        return new SourceUnit(filename, """
                namespace %s

                @description("A user")
                actor User

                entity Item { id: String }

                @kpi(metric: "Conv", target: ">80%%")
                journey DoTask {
                    actor: User
                    steps: [
                        step Act {
                            action: "User acts"
                            expectation: "System responds"
                        }
                    ]
                    outcomes: { success: "Done" }
                }
                """.formatted(ns));
    }

    /** Source with a syntax error. */
    private static final SourceUnit SYNTAX_ERROR_UNIT = new SourceUnit(
            "bad.chronos",
            """
            namespace com.example.bad

            entity {
                id: String
            }
            """);

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void twoValidFiles_returnsFinalizedWithTwoModels() {
        var unit1 = validUnit("com.example.one", "one.chronos");
        var unit2 = validUnit("com.example.two", "two.chronos");

        var result = new ChronosCompiler().compileAll(List.of(unit1, unit2));

        assertTrue(result.parsed(),    "all files should parse");
        assertTrue(result.finalized(), "all files should finalize");
        assertNotNull(result.unitOrNull(), "unit should be present when all files parse");
        assertEquals(2, result.unitOrNull().models().size(),
                "unit should contain one model per source file");

        // Models are in input order
        assertEquals("com.example.one", result.unitOrNull().models().get(0).namespace());
        assertEquals("com.example.two", result.unitOrNull().models().get(1).namespace());

        assertTrue(result.errors().isEmpty(),
                "no errors expected; got: " + result.errors());
    }

    @Test
    void oneInvalidFile_returnsNotFinalized() {
        var valid = validUnit("com.example.good", "good.chronos");

        var result = new ChronosCompiler().compileAll(List.of(valid, SYNTAX_ERROR_UNIT));

        assertFalse(result.parsed(),    "parsed must be false when any file has a syntax error");
        assertFalse(result.finalized(), "finalized must be false when any file fails");
        assertNull(result.unitOrNull(), "unit must be null when any file fails to parse");
        assertFalse(result.diagnostics().isEmpty(), "should have parse diagnostics");
    }

    @Test
    void emptySourceList_returnsFinalizedWithEmptyUnit() {
        var result = new ChronosCompiler().compileAll(List.of());

        assertTrue(result.parsed(),    "vacuously true — no files to fail");
        assertTrue(result.finalized(), "vacuously true — no files to fail");
        assertNotNull(result.unitOrNull());
        assertTrue(result.unitOrNull().models().isEmpty());
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    void diagnosticsAggregatedAcrossFiles() {
        // Both files are valid so no errors, but each will produce a CHR-009 warning
        // (no @kpi trait) if we omit the kpi. Use simpler sources that trigger warnings.
        var unit1 = new SourceUnit("a.chronos", """
                namespace com.example.a

                actor UserA

                entity OrderA { id: String }

                journey JourneyA {
                    actor: UserA
                    steps: [
                        step S1 {
                            action: "act"
                            expectation: "expect"
                        }
                    ]
                    outcomes: { success: "ok" }
                }
                """);
        var unit2 = new SourceUnit("b.chronos", """
                namespace com.example.b

                actor UserB

                entity OrderB { id: String }

                journey JourneyB {
                    actor: UserB
                    steps: [
                        step S1 {
                            action: "act"
                            expectation: "expect"
                        }
                    ]
                    outcomes: { success: "ok" }
                }
                """);

        var result = new ChronosCompiler().compileAll(List.of(unit1, unit2));

        assertTrue(result.parsed());
        // Both files produce CHR-009 (missing @kpi) → 2 warnings total
        long chr009Count = result.warnings().stream()
                .filter(d -> "CHR-009".equals(d.code()))
                .count();
        assertEquals(2, chr009Count,
                "expected one CHR-009 warning per file; got: " + result.warnings());
    }
}
