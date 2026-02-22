package com.genairus.chronos.compiler;

import com.genairus.chronos.validator.ChronosValidator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression guard for CHR diagnostic-code uniqueness.
 *
 * <p>Each CHR code must have exactly one meaning across the entire codebase.
 * This test class pins the behavioral contract for codes that have been at risk
 * of numbering collision, and provides a canonical registry so contributors can
 * see at a glance which codes are taken.
 *
 * <h3>Authoritative code registry</h3>
 * <pre>
 *   Code      Owner                   Meaning
 *   --------  ----------------------  -----------------------------------------------
 *   CHR-001   ChronosValidator        Journey must declare an actor
 *   CHR-002   ChronosValidator        Journey must have a success outcome
 *   CHR-003   ChronosValidator        Every step must declare action and expectation
 *   CHR-004   ChronosValidator        Journey declares zero happy-path steps (WARNING)
 *   CHR-005   IndexCompilationUnit    Duplicate shape name within the same model
 *   CHR-006   ChronosValidator        Entity/shape declares no fields (WARNING)
 *   CHR-007   ChronosValidator        Actor missing @description (WARNING)
 *   CHR-008   ChronosValidator        NamedTypeRef cannot be resolved
 *   CHR-009   ChronosValidator        Journey missing @kpi (WARNING)
 *   CHR-010   ChronosValidator        Journey missing @compliance (WARNING)
 *   CHR-011   ChronosValidator        Relationship targets must reference defined entities
 *   CHR-012   FinalizeIrPhase         Unresolved SymbolRef after all resolution phases
 *   CHR-013   TypeResolutionPhase     Type name cannot be resolved during type resolution
 *   CHR-014   IndexCompilationUnit    Inverse field name must exist on target entity
 *   CHR-015   ChronosValidator        Circular inheritance chain detected
 *   CHR-016   ImportResolver          Unknown import target
 *   CHR-017   ImportResolver          Ambiguous import: same simple name → different targets
 *   CHR-018   ChronosValidator        Multiple inheritance is not supported
 *   CHR-019   ChronosValidator        Invariant expression references undeclared field
 *   CHR-020   ChronosValidator        Invariant severity must be error/warning/info
 *   CHR-021   ChronosValidator        Global invariant must declare non-empty scope
 *   CHR-022   ChronosValidator        Invariant names must be unique within scope
 *   CHR-023   ChronosValidator        Every deny must include a description
 *   CHR-024   ChronosValidator        Deny scope entities must be defined or imported
 *   CHR-025   ChronosValidator        Deny severity must be critical/high/medium/low
 *   CHR-026   ChronosValidator        Error codes must be unique across namespace
 *   CHR-027   ChronosValidator        Variant trigger must reference a defined error type
 *   CHR-028   ChronosValidator        Error severity must be critical/high/medium/low
 *   CHR-029   ChronosValidator        All transition states must be declared in states list
 *   CHR-030   ChronosValidator        Every non-terminal state must have an outbound transition
 *   CHR-031   ChronosValidator        The initial state must be in the states list
 *   CHR-032   ChronosValidator        Terminal states must not have outbound transitions
 *   CHR-033   ChronosValidator        Referenced entity and field must be defined
 *   CHR-034   ChronosValidator        TransitionTo() must reference a declared statemachine state
 *   CHR-035   ChronosValidator        Output field names must be unique across all steps in journey scope
 *   CHR-036   ChronosValidator        Step input field must be produced by a preceding step's output
 *   CHR-037   ChronosValidator        @authorize role name must reference a declared role
 *   CHR-038   ChronosValidator        @authorize permission must be listed in the role's allow list
 *   CHR-039   ChronosValidator        Journey actor must carry @authorize(role: X) matching the journey's required role
 *   CHR-040   ChronosValidator        @authorize permission must not be in the role's deny list
 *   CHR-W001  ChronosValidator        Invariant references optional field without null guard (WARNING)
 * </pre>
 */
class DiagnosticCodeRegistryTest {

    /**
     * All known CHR codes, each listed exactly once.
     * A duplicate entry here is a compile-time mistake caught by the uniqueness test below.
     */
    private static final List<String> ALL_KNOWN_CODES = List.of(
            "CHR-001", "CHR-002", "CHR-003", "CHR-004", "CHR-005",
            "CHR-006", "CHR-007", "CHR-008", "CHR-009", "CHR-010",
            "CHR-011", "CHR-012", "CHR-013", "CHR-014", "CHR-015",
            "CHR-016", "CHR-017", "CHR-018", "CHR-019", "CHR-020",
            "CHR-021", "CHR-022", "CHR-023", "CHR-024", "CHR-025",
            "CHR-026", "CHR-027", "CHR-028", "CHR-029", "CHR-030",
            "CHR-031", "CHR-032", "CHR-033", "CHR-034",
            "CHR-035", "CHR-036",
            "CHR-037", "CHR-038", "CHR-039", "CHR-040",
            "CHR-W001"
    );

    // ── Registry self-check ───────────────────────────────────────────────────

    @Test
    void registryHasNoDuplicateCodes() {
        var seen = new HashSet<String>();
        var duplicates = new ArrayList<String>();
        for (String code : ALL_KNOWN_CODES) {
            if (!seen.add(code)) duplicates.add(code);
        }
        assertTrue(duplicates.isEmpty(),
                "Duplicate CHR codes in registry: " + duplicates
                        + ". Each code must appear exactly once.");
    }

    // ── CHR-017 behavioral contract ───────────────────────────────────────────

    private static final String MINIMAL_VALID = """
            namespace test
            entity Foo { id: String }
            """;

    /**
     * CHR-017 is owned by {@link com.genairus.chronos.compiler.imports.ImportResolver}.
     * {@link ChronosValidator} must never emit it — the ambiguous-import condition
     * is resolved before validation runs.
     */
    @Test
    void chr017_neverEmittedByValidator() {
        var result = new ChronosCompiler().compile(MINIMAL_VALID, "test.chronos");
        assertTrue(result.finalized(),
                "minimal model must finalize cleanly; got: " + result.diagnostics());

        var validationResult = new ChronosValidator().validate(result.modelOrNull());
        boolean hasChr017 = validationResult.diagnostics().stream()
                .anyMatch(d -> "CHR-017".equals(d.code()));
        assertFalse(hasChr017,
                "CHR-017 (ambiguous import) is a compiler-phase code; "
                        + "ChronosValidator must never emit it. Got: "
                        + validationResult.diagnostics());
    }

    /**
     * CHR-017 message must name the conflicting simple name and both
     * fully-qualified targets so the user can act on the error.
     *
     * <p>Full import-binding coverage is in {@link ImportBindingTest}.
     */
    @Test
    void chr017_messageNamesAllConflictingParties() {
        var unitA = new SourceUnit("a.chronos", "namespace x\nentity Order { id: String }");
        var unitB = new SourceUnit("b.chronos", "namespace y\nentity Order { id: String }");
        var unitC = new SourceUnit("c.chronos", """
                namespace z
                use x#Order
                use y#Order
                entity Foo { id: String }
                """);

        var result = new ChronosCompiler().compileAll(List.of(unitA, unitB, unitC));

        var chr017 = result.diagnostics().stream()
                .filter(d -> "CHR-017".equals(d.code()))
                .findFirst();
        assertTrue(chr017.isPresent(),
                "expected CHR-017 for ambiguous import; got: " + result.diagnostics());

        String msg = chr017.get().message();
        assertTrue(msg.contains("Order"),   "message must name the conflicting simple name; got: " + msg);
        assertTrue(msg.contains("x#Order"), "message must name first target; got: " + msg);
        assertTrue(msg.contains("y#Order"), "message must name second target; got: " + msg);
    }
}
