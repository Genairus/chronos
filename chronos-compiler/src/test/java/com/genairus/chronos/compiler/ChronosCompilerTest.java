package com.genairus.chronos.compiler;

import com.genairus.chronos.core.diagnostics.DiagnosticSeverity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link ChronosCompiler}.
 *
 * <p>Covers the four acceptance criteria:
 * <ol>
 *   <li>Duplicate top-level symbol → CHR-005 ERROR</li>
 *   <li>Unresolved type reference → CHR-008 ERROR</li>
 *   <li>Valid minimal program compiles with {@code finalized=true}</li>
 *   <li>Invalid program (missing actor) returns diagnostics but does not crash</li>
 * </ol>
 */
class ChronosCompilerTest {

    // ── Fixture sources ───────────────────────────────────────────────────────

    /** Minimal valid program: entity + actor + journey with all required fields. */
    private static final String VALID_MINIMAL = """
            namespace com.example.test

            @description("A test user")
            actor TestUser

            entity Order {
                id: String
            }

            @kpi(metric: "TestConversion", target: ">90%")
            journey DoSomething {
                actor: TestUser
                steps: [
                    step Perform {
                        action: "User performs action"
                        expectation: "System responds"
                    }
                ]
                outcomes: {
                    success: "All done"
                }
            }
            """;

    /** Source with a duplicate entity name. */
    private static final String DUPLICATE_SYMBOL = """
            namespace com.example.test

            entity Order {
                id: String
            }

            entity Order {
                name: String
            }
            """;

    /** Source with an unresolved type reference in an entity field. */
    private static final String UNRESOLVED_TYPE = """
            namespace com.example.test

            entity Order {
                item: UnknownType
            }
            """;

    /** Invalid program: journey missing actor and outcomes. */
    private static final String INVALID_JOURNEY = """
            namespace com.example.test

            journey MissingFields {
                steps: [
                    step DoIt {
                        action: "User does it"
                        expectation: "System processes"
                    }
                ]
            }
            """;

    /** Source with a syntax error. */
    private static final String SYNTAX_ERROR = """
            namespace com.example.test

            entity {
                id: String
            }
            """;

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void validMinimalProgram_compilesSuccessfully() {
        var result = new ChronosCompiler().compile(VALID_MINIMAL, "<test>");
        assertTrue(result.parsed(),    "should parse successfully");
        assertTrue(result.finalized(), "should be finalized");
        assertNotNull(result.modelOrNull(), "model should be present");

        // No errors; warnings are allowed (e.g. CHR-006 on Order with only id field)
        assertTrue(result.errors().isEmpty(),
                "expected no errors but got: " + result.errors());
    }

    @Test
    void duplicateSymbol_reportsChr005() {
        var result = new ChronosCompiler().compile(DUPLICATE_SYMBOL, "<test>");
        assertTrue(result.parsed(), "should still parse");
        assertFalse(result.success(), "should fail due to duplicate");

        boolean hasChr005 = result.errors().stream()
                .anyMatch(d -> "CHR-005".equals(d.code()));
        assertTrue(hasChr005, "expected CHR-005 but got: " + result.errors());
    }

    @Test
    void unresolvedTypeReference_reportsChr008() {
        var result = new ChronosCompiler().compile(UNRESOLVED_TYPE, "<test>");
        assertTrue(result.parsed(), "should still parse");

        boolean hasChr008 = result.diagnostics().stream()
                .anyMatch(d -> "CHR-008".equals(d.code())
                        && d.severity() == DiagnosticSeverity.ERROR);
        assertTrue(hasChr008, "expected CHR-008 but got: " + result.diagnostics());
    }

    @Test
    void invalidJourney_reportsDiagnosticsWithoutCrash() {
        var result = new ChronosCompiler().compile(INVALID_JOURNEY, "<test>");
        assertTrue(result.parsed(), "should parse");
        assertFalse(result.success(), "should fail validation");
        assertFalse(result.errors().isEmpty(), "should have errors");

        // CHR-001: missing actor
        boolean hasChr001 = result.errors().stream()
                .anyMatch(d -> "CHR-001".equals(d.code()));
        assertTrue(hasChr001, "expected CHR-001 for missing actor");

        // CHR-002: missing outcomes
        boolean hasChr002 = result.errors().stream()
                .anyMatch(d -> "CHR-002".equals(d.code()));
        assertTrue(hasChr002, "expected CHR-002 for missing outcomes");
    }

    @Test
    void syntaxError_returnsNotParsed() {
        var result = new ChronosCompiler().compile(SYNTAX_ERROR, "<test>");
        assertFalse(result.parsed(), "should not parse");
        assertNull(result.modelOrNull(), "model should be null");
        assertFalse(result.finalized(), "should not be finalized");
        assertFalse(result.diagnostics().isEmpty(), "should have parse diagnostics");
    }

    @Test
    void compileResult_successReturnsFalseWhenErrors() {
        var result = new ChronosCompiler().compile(INVALID_JOURNEY, "<test>");
        assertFalse(result.success());
    }

    @Test
    void compileResult_errorsAndWarningsFiltered() {
        var result = new ChronosCompiler().compile(VALID_MINIMAL, "<test>");
        // All diagnostics are either ERROR or WARNING
        for (var d : result.diagnostics()) {
            assertTrue(d.severity() == DiagnosticSeverity.ERROR
                    || d.severity() == DiagnosticSeverity.WARNING,
                    "unexpected severity: " + d.severity());
        }
    }
}
