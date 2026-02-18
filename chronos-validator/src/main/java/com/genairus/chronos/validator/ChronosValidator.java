package com.genairus.chronos.validator;

import com.genairus.chronos.model.*;

import java.util.*;

/**
 * Validates a {@link ChronosModel} against the ten semantic rules CHR-001
 * through CHR-010.
 *
 * <p>Grammar-enforced syntax rules (e.g. "namespace must be present") are not
 * re-checked here; the validator covers semantic constraints that the parser
 * intentionally leaves relaxed so that partially-written files can still be
 * parsed and provide better error messages.
 *
 * <h3>Rule summary</h3>
 * <pre>
 *   CHR-001 ERROR   Journey must declare an actor
 *   CHR-002 ERROR   Journey must have an outcomes block with a success outcome
 *   CHR-003 ERROR   Every step must declare both action and expectation
 *   CHR-004 WARNING Journey declares zero happy-path steps
 *   CHR-005 ERROR   Duplicate shape name within the same model
 *   CHR-006 WARNING Entity or shape struct declares no fields
 *   CHR-007 WARNING Actor is missing a @description trait
 *   CHR-008 ERROR   TypeRef.NamedTypeRef cannot be resolved in the model
 *   CHR-009 WARNING Journey is missing a @kpi trait
 *   CHR-010 WARNING Journey is missing @compliance when model has compliance policies
 * </pre>
 */
public class ChronosValidator {

    /**
     * Validates the given model against all rules and returns the aggregated
     * result. Rules run in code order; within each rule, shapes are visited in
     * source order.
     */
    public ValidationResult validate(ChronosModel model) {
        var issues = new ArrayList<ValidationIssue>();

        checkChr001(model, issues);
        checkChr002(model, issues);
        checkChr003(model, issues);
        checkChr004(model, issues);
        checkChr005(model, issues);
        checkChr006(model, issues);
        checkChr007(model, issues);
        checkChr008(model, issues);
        checkChr009(model, issues);
        checkChr010(model, issues);

        return new ValidationResult(Collections.unmodifiableList(issues));
    }

    // ── CHR-001 ────────────────────────────────────────────────────────────────

    /** Journey must declare an actor. */
    private void checkChr001(ChronosModel model, List<ValidationIssue> issues) {
        for (JourneyDef journey : model.journeys()) {
            if (journey.actorName().isEmpty()) {
                issues.add(error("CHR-001",
                        "Journey '" + journey.name() + "' must declare an actor",
                        journey.location()));
            }
        }
    }

    // ── CHR-002 ────────────────────────────────────────────────────────────────

    /** Journey must have an outcomes block that contains at minimum a success outcome. */
    private void checkChr002(ChronosModel model, List<ValidationIssue> issues) {
        for (JourneyDef journey : model.journeys()) {
            if (journey.journeyOutcomes().isEmpty()) {
                issues.add(error("CHR-002",
                        "Journey '" + journey.name() + "' must declare an outcomes block",
                        journey.location()));
            } else if (journey.journeyOutcomes().get().successOutcome().isEmpty()) {
                issues.add(error("CHR-002",
                        "Journey '" + journey.name() + "' outcomes block must include a success outcome",
                        journey.journeyOutcomes().get().location()));
            }
        }
    }

    // ── CHR-003 ────────────────────────────────────────────────────────────────

    /** Every step (happy-path and variant) must declare both action and expectation. */
    private void checkChr003(ChronosModel model, List<ValidationIssue> issues) {
        for (JourneyDef journey : model.journeys()) {
            checkStepsChr003(journey.name(), journey.steps(), issues);
            for (Variant variant : journey.variants().values()) {
                checkStepsChr003(journey.name() + "/" + variant.name(), variant.steps(), issues);
            }
        }
    }

    private void checkStepsChr003(String context, List<Step> steps, List<ValidationIssue> issues) {
        for (Step step : steps) {
            if (step.action().isEmpty()) {
                issues.add(error("CHR-003",
                        "Step '" + step.name() + "' in '" + context + "' must declare an action",
                        step.location()));
            }
            if (step.expectation().isEmpty()) {
                issues.add(error("CHR-003",
                        "Step '" + step.name() + "' in '" + context + "' must declare an expectation",
                        step.location()));
            }
        }
    }

    // ── CHR-004 ────────────────────────────────────────────────────────────────

    /** Journey should declare at least one happy-path step. */
    private void checkChr004(ChronosModel model, List<ValidationIssue> issues) {
        for (JourneyDef journey : model.journeys()) {
            if (journey.steps().isEmpty()) {
                issues.add(warning("CHR-004",
                        "Journey '" + journey.name() + "' declares no steps",
                        journey.location()));
            }
        }
    }

    // ── CHR-005 ────────────────────────────────────────────────────────────────

    /** Shape names must be unique within the model. */
    private void checkChr005(ChronosModel model, List<ValidationIssue> issues) {
        var seen = new LinkedHashMap<String, SourceLocation>();
        var reported = new HashSet<String>();

        for (ShapeDefinition shape : model.shapes()) {
            String name = shape.name();
            if (seen.containsKey(name)) {
                if (reported.add(name)) {
                    // Report at the first occurrence
                    issues.add(error("CHR-005",
                            "Duplicate shape name '" + name + "'",
                            seen.get(name)));
                }
                issues.add(error("CHR-005",
                        "Duplicate shape name '" + name + "'",
                        shape.location()));
            } else {
                seen.put(name, shape.location());
            }
        }
    }

    // ── CHR-006 ────────────────────────────────────────────────────────────────

    /** Entity and shape struct definitions should declare at least one field. */
    private void checkChr006(ChronosModel model, List<ValidationIssue> issues) {
        for (ShapeDefinition shape : model.shapes()) {
            switch (shape) {
                case EntityDef e when e.fields().isEmpty() ->
                        issues.add(warning("CHR-006",
                                "Entity '" + e.name() + "' declares no fields",
                                e.location()));
                case ShapeStructDef s when s.fields().isEmpty() ->
                        issues.add(warning("CHR-006",
                                "Shape '" + s.name() + "' declares no fields",
                                s.location()));
                default -> { /* other shape types have no fields */ }
            }
        }
    }

    // ── CHR-007 ────────────────────────────────────────────────────────────────

    /** Actors should carry a @description trait. */
    private void checkChr007(ChronosModel model, List<ValidationIssue> issues) {
        for (ActorDef actor : model.actors()) {
            if (actor.description().isEmpty()) {
                issues.add(warning("CHR-007",
                        "Actor '" + actor.name() + "' is missing a @description trait",
                        actor.location()));
            }
        }
    }

    // ── CHR-008 ────────────────────────────────────────────────────────────────

    /**
     * Named type references must resolve to a shape declared in the model or
     * listed in the model's import declarations.
     */
    private void checkChr008(ChronosModel model, List<ValidationIssue> issues) {
        // Collect all imported shape names for fast lookup
        var importedNames = new HashSet<String>();
        for (UseDecl imp : model.imports()) {
            importedNames.add(imp.shapeName());
        }

        for (ShapeDefinition shape : model.shapes()) {
            switch (shape) {
                case EntityDef e ->
                        e.fields().forEach(f -> checkTypeRefChr008(f.type(), e.name(), model, importedNames, issues));
                case ShapeStructDef s ->
                        s.fields().forEach(f -> checkTypeRefChr008(f.type(), s.name(), model, importedNames, issues));
                case ListDef l ->
                        checkTypeRefChr008(l.memberType(), l.name(), model, importedNames, issues);
                case MapDef m -> {
                    checkTypeRefChr008(m.keyType(),   m.name(), model, importedNames, issues);
                    checkTypeRefChr008(m.valueType(), m.name(), model, importedNames, issues);
                }
                default -> { /* ActorDef, PolicyDef, EnumDef, JourneyDef have no field types */ }
            }
        }
    }

    private void checkTypeRefChr008(TypeRef type, String context,
                                     ChronosModel model, Set<String> importedNames,
                                     List<ValidationIssue> issues) {
        switch (type) {
            case TypeRef.NamedTypeRef r -> {
                String name = r.qualifiedId();
                boolean resolved = model.findShape(name).isPresent()
                        || importedNames.contains(name);
                if (!resolved) {
                    // Use unknown location since TypeRef doesn't carry source location
                    issues.add(error("CHR-008",
                            "Unresolved type reference '" + name + "' in '" + context + "'",
                            SourceLocation.unknown()));
                }
            }
            case TypeRef.ListType l -> checkTypeRefChr008(l.elementType(), context, model, importedNames, issues);
            case TypeRef.MapType m -> {
                checkTypeRefChr008(m.keyType(),   context, model, importedNames, issues);
                checkTypeRefChr008(m.valueType(), context, model, importedNames, issues);
            }
            case TypeRef.PrimitiveType p -> { /* primitives always resolve */ }
        }
    }

    // ── CHR-009 ────────────────────────────────────────────────────────────────

    /** Journeys should carry a @kpi trait. */
    private void checkChr009(ChronosModel model, List<ValidationIssue> issues) {
        for (JourneyDef journey : model.journeys()) {
            if (journey.kpiMetric().isEmpty()) {
                issues.add(warning("CHR-009",
                        "Journey '" + journey.name() + "' is missing a @kpi trait",
                        journey.location()));
            }
        }
    }

    // ── CHR-010 ────────────────────────────────────────────────────────────────

    /**
     * When the model contains at least one {@code @compliance} policy, every
     * journey should carry a {@code @compliance} trait.
     */
    private void checkChr010(ChronosModel model, List<ValidationIssue> issues) {
        boolean hasCompliancePolicies = model.policies().stream()
                .anyMatch(p -> p.complianceFramework().isPresent());

        if (!hasCompliancePolicies) return;

        for (JourneyDef journey : model.journeys()) {
            if (!journey.hasComplianceTrait()) {
                issues.add(warning("CHR-010",
                        "Journey '" + journey.name() + "' should carry a @compliance trait"
                                + " (model contains compliance policies)",
                        journey.location()));
            }
        }
    }

    // ── Factories ──────────────────────────────────────────────────────────────

    private static ValidationIssue error(String code, String message, SourceLocation loc) {
        return new ValidationIssue(code, ValidationSeverity.ERROR, message, loc);
    }

    private static ValidationIssue warning(String code, String message, SourceLocation loc) {
        return new ValidationIssue(code, ValidationSeverity.WARNING, message, loc);
    }
}
