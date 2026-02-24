package com.genairus.chronos.core.diagnostics;

import java.util.List;
import java.util.Set;

/**
 * Authoritative registry of all Chronos diagnostic codes.
 *
 * <p>Every CHR code has exactly one meaning across the entire codebase. This class
 * provides a production-accessible, build-time-verifiable list so both the compiler
 * pipeline and the MCP knowledge layer can assert full coverage without depending on
 * test-scoped code.
 *
 * <p>{@link Set#copyOf} is used intentionally: it throws {@link IllegalArgumentException}
 * on duplicate entries, making the uniqueness invariant self-enforcing at class initialization.
 *
 * <h3>Code registry</h3>
 * <pre>
 *   Code      Owner                   Meaning
 *   --------  ----------------------  -----------------------------------------------
 *   CHR-001   ChronosValidator        Journey must declare an actor
 *   CHR-002   ChronosValidator        Journey must have a success outcome
 *   CHR-003   ChronosValidator        Every step must declare action and expectation
 *   CHR-004   ChronosValidator        Journey declares zero happy-path steps (WARNING)
 *   CHR-005   ChronosValidator /       Duplicate shape name (per-file: ChronosValidator;
 *             GlobalSymbolTable        cross-file: GlobalSymbolTable)
 *   CHR-006   ChronosValidator        Entity/shape declares no fields (WARNING)
 *   CHR-007   ChronosValidator        Actor missing @description (WARNING)
 *   CHR-008   ChronosValidator        NamedTypeRef cannot be resolved
 *   CHR-009   ChronosValidator        Journey missing @kpi (WARNING)
 *   CHR-010   ChronosValidator        Journey missing @compliance (WARNING)
 *   CHR-011   ChronosValidator        Relationship targets must reference defined entities
 *   CHR-012   FinalizeIrPhase         Unresolved SymbolRef after all resolution phases
 *   CHR-013   TypeResolutionPhase     Type name cannot be resolved during type resolution
 *   CHR-014   ChronosValidator        Inverse field name must exist on target entity
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
 *   CHR-041   ChronosValidator        Step telemetry event must be a declared or imported event type
 *   CHR-042   ChronosValidator        Invariant expression failed to parse
 *   CHR-043   ChronosValidator        Type mismatch in invariant expression (WARNING)
 *   CHR-044   ChronosValidator        Statemachine state is not a member of the bound enum
 *   CHR-045   ChronosValidator        Bound enum member not covered by any statemachine state (WARNING)
 *   CHR-046   ChronosValidator        TransitionTo() target state is ambiguous (declared in multiple statemachines)
 *   CHR-047   ChronosValidator        TransitionTo() target state has no declared incoming transitions (WARNING)
 *   CHR-048   ChronosValidator        Composition target cannot be referenced by multiple composing entities
 *   CHR-049   ChronosValidator        Child entity redefines parent field with incompatible type
 *   CHR-050   ChronosValidator        @timeout/@ttl duration argument is not a valid duration literal
 *   CHR-051   ChronosValidator        @timeout onExpiry references an undeclared variant in the journey
 *   CHR-052   ChronosValidator        @ttl action must be one of: delete, archive, notify
 *   CHR-053   ChronosValidator        @schedule cron is not a valid 5-field cron expression
 *   CHR-W001  ChronosValidator        Invariant references optional field without null guard (WARNING)
 * </pre>
 */
public final class DiagnosticCodeRegistry {

    /**
     * Immutable set of all 54 known diagnostic codes (CHR-001 through CHR-053 + CHR-W001).
     * Duplicate entries at construction time will cause an {@link IllegalArgumentException},
     * making the uniqueness invariant self-enforcing.
     */
    public static final Set<String> ALL_KNOWN_CODES = Set.copyOf(List.of(
            "CHR-001", "CHR-002", "CHR-003", "CHR-004", "CHR-005",
            "CHR-006", "CHR-007", "CHR-008", "CHR-009", "CHR-010",
            "CHR-011", "CHR-012", "CHR-013", "CHR-014", "CHR-015",
            "CHR-016", "CHR-017", "CHR-018", "CHR-019", "CHR-020",
            "CHR-021", "CHR-022", "CHR-023", "CHR-024", "CHR-025",
            "CHR-026", "CHR-027", "CHR-028", "CHR-029", "CHR-030",
            "CHR-031", "CHR-032", "CHR-033", "CHR-034",
            "CHR-035", "CHR-036",
            "CHR-037", "CHR-038", "CHR-039", "CHR-040",
            "CHR-041",
            "CHR-042", "CHR-043",
            "CHR-044", "CHR-045",
            "CHR-046", "CHR-047",
            "CHR-048", "CHR-049",
            "CHR-050", "CHR-051", "CHR-052", "CHR-053",
            "CHR-W001"
    ));

    private DiagnosticCodeRegistry() {}
}
