package com.genairus.chronos.validator;

import com.genairus.chronos.model.ChronosModel;
import com.genairus.chronos.parser.ChronosModelParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link ChronosValidator} — one focused test per rule
 * CHR-001 through CHR-010, each parsing a real Chronos source string with
 * {@link ChronosModelParser#parseString} and asserting the exact rule code,
 * severity, and source location.
 */
class ChronosValidatorIntegrationTest {

    private static final ChronosValidator VALIDATOR = new ChronosValidator();

    private static ChronosModel parse(String source) {
        return ChronosModelParser.parseString("<test>", source);
    }

    // ── CHR-001 ───────────────────────────────────────────────────────────────

    @Test
    void chr001_journeyWithoutActor_isError() {
        // Journey on line 3; no actor: field → CHR-001 at line 3
        var model = parse("""
                namespace test

                journey J {
                    steps: [
                        step DoIt {
                            action: "act"
                            expectation: "expect"
                        }
                    ]
                    outcomes: {
                        success: "ok"
                    }
                }
                """);
        var issues = VALIDATOR.validate(model).errors().stream()
                .filter(i -> "CHR-001".equals(i.ruleCode())).toList();
        assertEquals(1, issues.size());
        assertEquals(ValidationSeverity.ERROR, issues.get(0).severity());
        assertEquals(3, issues.get(0).location().line());
    }

    // ── CHR-002 ───────────────────────────────────────────────────────────────

    @Test
    void chr002_journeyWithoutOutcomes_isError() {
        // Journey on line 5; no outcomes: block → CHR-002 at line 5
        var model = parse("""
                namespace test

                actor User

                journey J {
                    actor: User
                    steps: [
                        step DoIt {
                            action: "act"
                            expectation: "expect"
                        }
                    ]
                }
                """);
        var issues = VALIDATOR.validate(model).errors().stream()
                .filter(i -> "CHR-002".equals(i.ruleCode())).toList();
        assertEquals(1, issues.size());
        assertEquals(ValidationSeverity.ERROR, issues.get(0).severity());
        assertEquals(5, issues.get(0).location().line());
    }

    // ── CHR-003 ───────────────────────────────────────────────────────────────

    @Test
    void chr003_stepWithoutAction_isError() {
        // Step on line 8; missing action: field → CHR-003 at line 8
        var model = parse("""
                namespace test

                actor User

                journey J {
                    actor: User
                    steps: [
                        step BadStep {
                            expectation: "expect"
                        }
                    ]
                    outcomes: {
                        success: "ok"
                    }
                }
                """);
        var issues = VALIDATOR.validate(model).errors().stream()
                .filter(i -> "CHR-003".equals(i.ruleCode())).toList();
        assertEquals(1, issues.size());
        assertEquals(ValidationSeverity.ERROR, issues.get(0).severity());
        assertEquals(8, issues.get(0).location().line());
    }

    // ── CHR-004 ───────────────────────────────────────────────────────────────

    @Test
    void chr004_journeyWithNoSteps_isWarning() {
        // Journey on line 5; omitted steps: block → CHR-004 at line 5
        var model = parse("""
                namespace test

                actor User

                journey J {
                    actor: User
                    outcomes: {
                        success: "ok"
                    }
                }
                """);
        var issues = VALIDATOR.validate(model).warnings().stream()
                .filter(i -> "CHR-004".equals(i.ruleCode())).toList();
        assertEquals(1, issues.size());
        assertEquals(ValidationSeverity.WARNING, issues.get(0).severity());
        assertEquals(5, issues.get(0).location().line());
    }

    // ── CHR-005 ───────────────────────────────────────────────────────────────

    @Test
    void chr005_duplicateShapeName_reportsBothOccurrences() {
        // First entity Item on line 3, second on line 7 → two CHR-005 errors
        var model = parse("""
                namespace test

                entity Item {
                    id: String
                }

                entity Item {
                    name: String
                }
                """);
        var issues = VALIDATOR.validate(model).errors().stream()
                .filter(i -> "CHR-005".equals(i.ruleCode())).toList();
        assertEquals(2, issues.size(), "One issue per occurrence");
        assertEquals(ValidationSeverity.ERROR, issues.get(0).severity());
        assertEquals(3, issues.get(0).location().line());
        assertEquals(7, issues.get(1).location().line());
    }

    // ── CHR-006 ───────────────────────────────────────────────────────────────

    @Test
    void chr006_emptyEntity_isWarning() {
        // Empty entity on line 3 → CHR-006 at line 3
        var model = parse("""
                namespace test

                entity Empty {}
                """);
        var issues = VALIDATOR.validate(model).warnings().stream()
                .filter(i -> "CHR-006".equals(i.ruleCode())).toList();
        assertEquals(1, issues.size());
        assertEquals(ValidationSeverity.WARNING, issues.get(0).severity());
        assertEquals(3, issues.get(0).location().line());
    }

    // ── CHR-007 ───────────────────────────────────────────────────────────────

    @Test
    void chr007_actorWithoutDescription_isWarning() {
        // Actor on line 3; no @description trait → CHR-007 at line 3
        var model = parse("""
                namespace test

                actor Customer
                """);
        var issues = VALIDATOR.validate(model).warnings().stream()
                .filter(i -> "CHR-007".equals(i.ruleCode())).toList();
        assertEquals(1, issues.size());
        assertEquals(ValidationSeverity.WARNING, issues.get(0).severity());
        assertEquals(3, issues.get(0).location().line());
    }

    // ── CHR-008 ───────────────────────────────────────────────────────────────

    @Test
    void chr008_unresolvedTypeRef_isError() {
        // Ghost is never declared; TypeRef carries no source location → SourceLocation.unknown()
        var model = parse("""
                namespace test

                entity Cart {
                    order: Ghost
                }
                """);
        var issues = VALIDATOR.validate(model).errors().stream()
                .filter(i -> "CHR-008".equals(i.ruleCode())).toList();
        assertEquals(1, issues.size());
        assertEquals(ValidationSeverity.ERROR, issues.get(0).severity());
        assertEquals("<unknown>", issues.get(0).location().file());
        assertEquals(0, issues.get(0).location().line());
    }

    // ── CHR-009 ───────────────────────────────────────────────────────────────

    @Test
    void chr009_journeyWithoutKpi_isWarning() {
        // Journey on line 5; no @kpi trait → CHR-009 at line 5
        var model = parse("""
                namespace test

                actor User

                journey J {
                    actor: User
                    steps: [
                        step DoIt {
                            action: "act"
                            expectation: "expect"
                        }
                    ]
                    outcomes: {
                        success: "ok"
                    }
                }
                """);
        var issues = VALIDATOR.validate(model).warnings().stream()
                .filter(i -> "CHR-009".equals(i.ruleCode())).toList();
        assertEquals(1, issues.size());
        assertEquals(ValidationSeverity.WARNING, issues.get(0).severity());
        assertEquals(5, issues.get(0).location().line());
    }

    // ── CHR-010 ───────────────────────────────────────────────────────────────

    @Test
    void chr010_journeyMissingComplianceWhenPolicyHasIt_isWarning() {
        // policy P (line 6) has @compliance; journey J (line 11) has @kpi but no @compliance
        // → CHR-010 at line 11
        var model = parse("""
                namespace test

                actor User

                @compliance("PCI-DSS")
                policy P {
                    description: "card data"
                }

                @kpi(metric: "m", target: "t")
                journey J {
                    actor: User
                    steps: [
                        step DoIt {
                            action: "act"
                            expectation: "expect"
                        }
                    ]
                    outcomes: {
                        success: "ok"
                    }
                }
                """);
        var issues = VALIDATOR.validate(model).warnings().stream()
                .filter(i -> "CHR-010".equals(i.ruleCode())).toList();
        assertEquals(1, issues.size());
        assertEquals(ValidationSeverity.WARNING, issues.get(0).severity());
        assertEquals(11, issues.get(0).location().line());
    }
}
