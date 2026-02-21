package com.genairus.chronos.validator;

import com.genairus.chronos.core.diagnostics.Diagnostic;
import com.genairus.chronos.core.refs.Span;
import com.genairus.chronos.core.refs.SymbolRef;
import com.genairus.chronos.ir.model.IrInheritanceResolver;
import com.genairus.chronos.ir.model.IrModel;
import com.genairus.chronos.ir.types.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates an {@link IrModel} against semantic rules CHR-001 through CHR-034 and CHR-W001.
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
 *   CHR-011 ERROR   Relationship targets must reference defined or imported entities
 *   CHR-012 ERROR   Composition targets cannot be referenced by more than one composing entity
 *   CHR-014 ERROR   Inverse field name (if specified) must exist on the target entity
 *   CHR-015 ERROR   Circular inheritance chains are a validation error
 *   CHR-016 ERROR   A child entity may not redefine a parent field with an incompatible type
 *   CHR-018 ERROR   Multiple inheritance is not supported
 *   CHR-019 ERROR   Invariant expressions must reference only fields visible in scope
 *   CHR-020 ERROR   Severity must be one of: error, warning, info
 *   CHR-021 ERROR   Global invariants must declare a scope listing all referenced entities
 *   CHR-022 ERROR   Invariant names must be unique within their enclosing scope
 *   CHR-023 ERROR   Every deny must include a description
 *   CHR-024 ERROR   Deny scope entities must be defined or imported
 *   CHR-025 ERROR   Deny severity must be one of: critical, high, medium, low
 *   CHR-026 ERROR   Error codes must be unique across the namespace
 *   CHR-027 ERROR   Variant triggers must reference a defined error type (string triggers are not supported)
 *   CHR-028 ERROR   Error severity must be one of: critical, high, medium, low
 *   CHR-029 ERROR   All states referenced in transitions must be declared in the states list
 *   CHR-030 ERROR   Every non-terminal state must have at least one outbound transition
 *   CHR-031 ERROR   The initial state must be in the states list
 *   CHR-032 ERROR   Terminal states must not have outbound transitions
 *   CHR-033 ERROR   The referenced entity and field must be defined; field type should be an enum
 *   CHR-034 ERROR   TransitionTo() in journey steps must reference a state declared in a statemachine
 *   CHR-W001 WARNING Invariant references optional field without null guard
 * </pre>
 */
public class ChronosValidator {

    /**
     * Validates the given IR model against all rules and returns the aggregated
     * result. Rules run in code order; within each rule, shapes are visited in
     * source order.
     */
    public ValidationResult validate(IrModel model) {
        var issues = new ArrayList<Diagnostic>();

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
        checkChr011(model, issues);
        checkChr012(model, issues);
        checkChr014(model, issues);
        checkChr015(model, issues);
        checkChr016(model, issues);
        checkChr018(model, issues);
        checkChr019(model, issues);
        checkChr020(model, issues);
        checkChr021(model, issues);
        checkChr022(model, issues);
        checkChr023(model, issues);
        checkChr024(model, issues);
        checkChr025(model, issues);
        checkChr026(model, issues);
        checkChr027(model, issues);
        checkChr028(model, issues);
        checkChr029(model, issues);
        checkChr030(model, issues);
        checkChr031(model, issues);
        checkChr032(model, issues);
        checkChr033(model, issues);
        checkChr034(model, issues);
        checkChrW001(model, issues);

        return new ValidationResult(Collections.unmodifiableList(issues));
    }

    // ── CHR-001 ────────────────────────────────────────────────────────────────

    /** Journey must declare an actor. */
    private void checkChr001(IrModel model, List<Diagnostic> issues) {
        for (JourneyDef journey : model.journeys()) {
            if (journey.actorName().isEmpty()) {
                issues.add(error("CHR-001",
                        "Journey '" + journey.name() + "' must declare an actor",
                        journey.span()));
            }
        }
    }

    // ── CHR-002 ────────────────────────────────────────────────────────────────

    /** Journey must have an outcomes block that contains at minimum a success outcome. */
    private void checkChr002(IrModel model, List<Diagnostic> issues) {
        for (JourneyDef journey : model.journeys()) {
            if (journey.journeyOutcomes().isEmpty()) {
                issues.add(error("CHR-002",
                        "Journey '" + journey.name() + "' must declare an outcomes block",
                        journey.span()));
            } else {
                JourneyOutcomes jo = journey.journeyOutcomes().get();
                if (jo.successOrNull() == null) {
                    issues.add(error("CHR-002",
                            "Journey '" + journey.name() + "' outcomes block must include a success outcome",
                            jo.span()));
                }
            }
        }
    }

    // ── CHR-003 ────────────────────────────────────────────────────────────────

    /** Every step (happy-path and variant) must declare both action and expectation. */
    private void checkChr003(IrModel model, List<Diagnostic> issues) {
        for (JourneyDef journey : model.journeys()) {
            checkStepsChr003(journey.name(), journey.steps(), issues);
            for (Variant variant : journey.variants().values()) {
                checkStepsChr003(journey.name() + "/" + variant.name(), variant.steps(), issues);
            }
        }
    }

    private void checkStepsChr003(String context, List<Step> steps, List<Diagnostic> issues) {
        for (Step step : steps) {
            if (step.action().isEmpty()) {
                issues.add(error("CHR-003",
                        "Step '" + step.name() + "' in '" + context + "' must declare an action",
                        step.span()));
            }
            if (step.expectation().isEmpty()) {
                issues.add(error("CHR-003",
                        "Step '" + step.name() + "' in '" + context + "' must declare an expectation",
                        step.span()));
            }
        }
    }

    // ── CHR-004 ────────────────────────────────────────────────────────────────

    /** Journey should declare at least one happy-path step. */
    private void checkChr004(IrModel model, List<Diagnostic> issues) {
        for (JourneyDef journey : model.journeys()) {
            if (journey.steps().isEmpty()) {
                issues.add(warning("CHR-004",
                        "Journey '" + journey.name() + "' declares no steps",
                        journey.span()));
            }
        }
    }

    // ── CHR-005 ────────────────────────────────────────────────────────────────

    /** Shape names must be unique within the model. */
    private void checkChr005(IrModel model, List<Diagnostic> issues) {
        var seen = new LinkedHashMap<String, Span>();
        var reported = new HashSet<String>();

        for (IrShape shape : model.shapes()) {
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
                        shape.span()));
            } else {
                seen.put(name, shape.span());
            }
        }
    }

    // ── CHR-006 ────────────────────────────────────────────────────────────────

    /** Entity and shape struct definitions should declare at least one field. */
    private void checkChr006(IrModel model, List<Diagnostic> issues) {
        for (IrShape shape : model.shapes()) {
            switch (shape) {
                case EntityDef e when e.fields().isEmpty() ->
                        issues.add(warning("CHR-006",
                                "Entity '" + e.name() + "' declares no fields",
                                e.span()));
                case ShapeStructDef s when s.fields().isEmpty() ->
                        issues.add(warning("CHR-006",
                                "Shape '" + s.name() + "' declares no fields",
                                s.span()));
                default -> { /* other shape types have no fields */ }
            }
        }
    }

    // ── CHR-007 ────────────────────────────────────────────────────────────────

    /** Actors should carry a @description trait. */
    private void checkChr007(IrModel model, List<Diagnostic> issues) {
        for (ActorDef actor : model.actors()) {
            if (actor.description().isEmpty()) {
                issues.add(warning("CHR-007",
                        "Actor '" + actor.name() + "' is missing a @description trait",
                        actor.span()));
            }
        }
    }

    // ── CHR-008 ────────────────────────────────────────────────────────────────

    /**
     * Named type references must resolve to a shape declared in the model or
     * listed in the model's import declarations.
     */
    private void checkChr008(IrModel model, List<Diagnostic> issues) {
        // Collect all imported shape names for fast lookup
        var importedNames = new HashSet<String>();
        for (UseDecl imp : model.imports()) {
            importedNames.add(imp.name());
        }

        for (IrShape shape : model.shapes()) {
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
                default -> { /* ActorDef, PolicyDef, EnumDef, JourneyDef, InvariantDef have no field types */ }
            }
        }
    }

    private void checkTypeRefChr008(TypeRef type, String context,
                                     IrModel model, Set<String> importedNames,
                                     List<Diagnostic> issues) {
        switch (type) {
            case TypeRef.NamedTypeRef r -> {
                // If TypeResolutionPhase already resolved this ref globally, trust it.
                if (r.ref().isResolved()) break;
                String name = r.qualifiedId();
                boolean resolved = model.findShape(name).isPresent()
                        || importedNames.contains(name);
                if (!resolved) {
                    issues.add(error("CHR-008",
                            "Unresolved type reference '" + name + "' in '" + context + "'",
                            r.ref().span()));
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
    private void checkChr009(IrModel model, List<Diagnostic> issues) {
        for (JourneyDef journey : model.journeys()) {
            if (kpiMetric(journey).isEmpty()) {
                issues.add(warning("CHR-009",
                        "Journey '" + journey.name() + "' is missing a @kpi trait",
                        journey.span()));
            }
        }
    }

    // ── CHR-010 ────────────────────────────────────────────────────────────────

    /**
     * When the model contains at least one {@code @compliance} policy, every
     * journey should carry a {@code @compliance} trait.
     */
    private void checkChr010(IrModel model, List<Diagnostic> issues) {
        boolean hasCompliancePolicies = model.policies().stream()
                .anyMatch(p -> p.complianceFramework().isPresent());

        if (!hasCompliancePolicies) return;

        for (JourneyDef journey : model.journeys()) {
            if (!hasComplianceTrait(journey)) {
                issues.add(warning("CHR-010",
                        "Journey '" + journey.name() + "' should carry a @compliance trait"
                                + " (model contains compliance policies)",
                        journey.span()));
            }
        }
    }

    // ── CHR-011 ────────────────────────────────────────────────────────────────

    /** Relationship targets must reference defined or imported entities. */
    private void checkChr011(IrModel model, List<Diagnostic> issues) {
        Set<String> importedNames = model.imports().stream()
                .map(UseDecl::name)
                .collect(Collectors.toSet());

        for (RelationshipDef rel : model.relationships()) {
            String fromName = symRefName(rel.fromEntityRef());
            String toName   = symRefName(rel.toEntityRef());

            if (!isEntityDefined(fromName, model, importedNames)) {
                issues.add(error("CHR-011",
                        "Relationship '" + rel.name() + "' references undefined entity '" + fromName + "' in 'from' field",
                        rel.span()));
            }

            if (!isEntityDefined(toName, model, importedNames)) {
                issues.add(error("CHR-011",
                        "Relationship '" + rel.name() + "' references undefined entity '" + toName + "' in 'to' field",
                        rel.span()));
            }
        }
    }

    private boolean isEntityDefined(String entityName, IrModel model, Set<String> importedNames) {
        boolean isDefined = model.entities().stream()
                .anyMatch(e -> e.name().equals(entityName));
        boolean isImported = importedNames.contains(entityName);
        return isDefined || isImported;
    }

    // ── CHR-012 ────────────────────────────────────────────────────────────────

    /** Composition targets cannot be referenced by more than one composing entity. */
    private void checkChr012(IrModel model, List<Diagnostic> issues) {
        var compositionTargets = new HashMap<String, ArrayList<RelationshipDef>>();

        for (RelationshipDef rel : model.relationships()) {
            if (rel.effectiveSemantics() == RelationshipSemantics.COMPOSITION) {
                compositionTargets
                    .computeIfAbsent(symRefName(rel.toEntityRef()), k -> new ArrayList<>())
                    .add(rel);
            }
        }

        for (var entry : compositionTargets.entrySet()) {
            String target = entry.getKey();
            var relationships = entry.getValue();

            if (relationships.size() > 1) {
                for (int i = 1; i < relationships.size(); i++) {
                    RelationshipDef rel = relationships.get(i);
                    issues.add(error("CHR-012",
                            "Entity '" + target + "' is already composed by relationship '" +
                            relationships.get(0).name() + "'; composition target cannot be referenced by multiple composing entities",
                            rel.span()));
                }
            }
        }
    }

    // ── CHR-014 ────────────────────────────────────────────────────────────────

    /** Inverse field name (if specified) must exist on the target entity. */
    private void checkChr014(IrModel model, List<Diagnostic> issues) {
        for (RelationshipDef rel : model.relationships()) {
            if (rel.inverseField().isEmpty()) {
                continue;
            }

            String inverseFieldName = rel.inverseField().get();
            String targetEntityName = symRefName(rel.toEntityRef());

            var targetEntity = model.entities().stream()
                    .filter(e -> e.name().equals(targetEntityName))
                    .findFirst();

            if (targetEntity.isEmpty()) {
                // Target entity doesn't exist - already caught by CHR-011
                continue;
            }

            boolean fieldExists = targetEntity.get().fields().stream()
                    .anyMatch(f -> f.name().equals(inverseFieldName));

            if (!fieldExists) {
                issues.add(error("CHR-014",
                        "Relationship '" + rel.name() + "' specifies inverse field '" + inverseFieldName +
                        "' but entity '" + targetEntityName + "' has no such field",
                        rel.span()));
            }
        }
    }

    // ── CHR-015 ────────────────────────────────────────────────────────────────

    /** Circular inheritance chains are a validation error. */
    private void checkChr015(IrModel model, List<Diagnostic> issues) {
        var resolver = new IrInheritanceResolver(model);

        for (EntityDef entity : model.entities()) {
            if (resolver.hasCircularInheritance(entity)) {
                issues.add(error("CHR-015",
                        "Entity '" + entity.name() + "' has a circular inheritance chain",
                        entity.span()));
            }
        }

        for (ActorDef actor : model.actors()) {
            if (resolver.hasCircularInheritance(actor)) {
                issues.add(error("CHR-015",
                        "Actor '" + actor.name() + "' has a circular inheritance chain",
                        actor.span()));
            }
        }
    }

    // ── CHR-016 ────────────────────────────────────────────────────────────────

    /** A child entity may not redefine a parent field with an incompatible type. */
    private void checkChr016(IrModel model, List<Diagnostic> issues) {
        for (EntityDef entity : model.entities()) {
            Optional<String> parentNameOpt = IrInheritanceResolver.parentName(entity);
            if (parentNameOpt.isEmpty()) {
                continue;
            }

            String parentName = parentNameOpt.get();
            var parent = model.entities().stream()
                    .filter(e -> e.name().equals(parentName))
                    .findFirst();

            if (parent.isEmpty()) {
                // Parent not found - already caught by CHR-008
                continue;
            }

            for (FieldDef childField : entity.fields()) {
                var parentField = parent.get().fields().stream()
                        .filter(f -> f.name().equals(childField.name()))
                        .findFirst();

                if (parentField.isPresent()) {
                    if (!areTypesCompatible(parentField.get().type(), childField.type())) {
                        issues.add(error("CHR-016",
                                "Entity '" + entity.name() + "' redefines field '" + childField.name() +
                                "' with incompatible type '" + formatType(childField.type()) +
                                "' (parent type is '" + formatType(parentField.get().type()) + "')",
                                entity.span()));
                    }
                }
            }
        }
    }

    private boolean areTypesCompatible(TypeRef parentType, TypeRef childType) {
        return parentType.equals(childType);
    }

    private String formatType(TypeRef type) {
        return switch (type) {
            case TypeRef.PrimitiveType p -> p.kind().toString().toLowerCase();
            case TypeRef.NamedTypeRef n -> n.qualifiedId();
            case TypeRef.ListType l -> "List<" + formatType(l.elementType()) + ">";
            case TypeRef.MapType m -> "Map<" + formatType(m.keyType()) + ", " + formatType(m.valueType()) + ">";
        };
    }

    // ── CHR-018 ────────────────────────────────────────────────────────────────

    /** Multiple inheritance is not supported. */
    private void checkChr018(IrModel model, List<Diagnostic> issues) {
        // The grammar enforces single inheritance by allowing only one parent in the 'extends'
        // clause. This check is a placeholder for future-proofing.
    }

    // ── CHR-019 ────────────────────────────────────────────────────────────────

    /** Invariant expressions must reference only fields visible in scope. */
    private void checkChr019(IrModel model, List<Diagnostic> issues) {
        for (EntityDef entity : model.entities()) {
            var resolver = new IrInheritanceResolver(model);
            var allFields = resolver.resolveAllFields(entity);
            var fieldNames = allFields.stream().map(FieldDef::name).toList();

            for (EntityInvariant inv : entity.invariants()) {
                validateFieldReferences(inv.expression(), fieldNames,
                    "Entity invariant '" + inv.name() + "' in entity '" + entity.name() + "'",
                    inv.span(), issues);
            }
        }

        for (InvariantDef inv : model.invariants()) {
            var scopeFieldNames = new ArrayList<String>();
            for (String entityName : inv.scope()) {
                var entity = model.entities().stream()
                    .filter(e -> e.name().equals(entityName))
                    .findFirst();

                if (entity.isPresent()) {
                    var resolver = new IrInheritanceResolver(model);
                    var allFields = resolver.resolveAllFields(entity.get());
                    for (FieldDef field : allFields) {
                        scopeFieldNames.add(entityName + "." + field.name());
                    }
                }
            }

            validateFieldReferences(inv.expression(), scopeFieldNames,
                "Global invariant '" + inv.name() + "'",
                inv.span(), issues);
        }
    }

    /**
     * Validates that field references in an expression exist in the allowed field names.
     * This is a simple lexical check that looks for identifiers in the expression.
     */
    private void validateFieldReferences(String expression, List<String> allowedFields,
                                        String context, Span span, List<Diagnostic> issues) {
        var lambdaParams = new HashSet<String>();
        var lambdaPattern = java.util.regex.Pattern.compile("(\\w+)\\s*=>");
        var lambdaMatcher = lambdaPattern.matcher(expression);
        while (lambdaMatcher.find()) {
            lambdaParams.add(lambdaMatcher.group(1));
        }

        var tokens = expression.split("[\\s()\\[\\],+\\-*/=!<>&|]+");

        for (String token : tokens) {
            if (token.isEmpty() || token.matches("\\d+(\\.\\d+)?")) {
                continue;
            }
            if (token.equals("true") || token.equals("false") || token.equals("null")) {
                continue;
            }
            if (token.matches("\".*\"") || token.matches("'.*'")) {
                continue;
            }
            if (token.equals("count") || token.equals("sum") || token.equals("min") ||
                token.equals("max") || token.equals("exists") || token.equals("forAll") ||
                token.equals("error") || token.equals("warning") || token.equals("info")) {
                continue;
            }
            if (!token.isEmpty() && !token.contains(".") && Character.isUpperCase(token.charAt(0))) {
                continue;
            }

            if (token.contains(".")) {
                String prefix = token.substring(0, token.indexOf('.'));
                if (lambdaParams.contains(prefix)) {
                    continue;
                }
            } else if (lambdaParams.contains(token)) {
                continue;
            }

            boolean isValid;
            if (token.contains(".")) {
                isValid = allowedFields.contains(token);
            } else {
                isValid = allowedFields.stream().anyMatch(f ->
                    f.equals(token) || f.endsWith("." + token));
            }

            if (!isValid && !token.isEmpty()) {
                if (token.contains(".") || Character.isLowerCase(token.charAt(0))) {
                    issues.add(error("CHR-019",
                        context + " references undefined field '" + token + "'",
                        span));
                }
            }
        }
    }

    // ── CHR-020 ────────────────────────────────────────────────────────────────

    /** Severity must be one of: error, warning, info. */
    private void checkChr020(IrModel model, List<Diagnostic> issues) {
        var validSeverities = Set.of("error", "warning", "info");

        for (EntityDef entity : model.entities()) {
            for (EntityInvariant inv : entity.invariants()) {
                if (!validSeverities.contains(inv.severity())) {
                    issues.add(error("CHR-020",
                        "Invariant '" + inv.name() + "' has invalid severity '" + inv.severity() +
                        "' (must be one of: error, warning, info)",
                        inv.span()));
                }
            }
        }

        for (InvariantDef inv : model.invariants()) {
            if (!validSeverities.contains(inv.severity())) {
                issues.add(error("CHR-020",
                    "Invariant '" + inv.name() + "' has invalid severity '" + inv.severity() +
                    "' (must be one of: error, warning, info)",
                    inv.span()));
            }
        }
    }

    // ── CHR-021 ────────────────────────────────────────────────────────────────

    /** Global invariants must declare a scope listing all referenced entities. */
    private void checkChr021(IrModel model, List<Diagnostic> issues) {
        for (InvariantDef inv : model.invariants()) {
            if (inv.scope().isEmpty()) {
                issues.add(error("CHR-021",
                    "Global invariant '" + inv.name() + "' must declare a non-empty scope",
                    inv.span()));
            }

            for (String entityName : inv.scope()) {
                boolean exists = model.entities().stream()
                    .anyMatch(e -> e.name().equals(entityName));

                if (!exists) {
                    issues.add(error("CHR-021",
                        "Global invariant '" + inv.name() + "' references undefined entity '" +
                        entityName + "' in scope",
                        inv.span()));
                }
            }
        }
    }

    // ── CHR-022 ────────────────────────────────────────────────────────────────

    /** Invariant names must be unique within their enclosing scope. */
    private void checkChr022(IrModel model, List<Diagnostic> issues) {
        for (EntityDef entity : model.entities()) {
            var seen = new HashMap<String, Span>();
            for (EntityInvariant inv : entity.invariants()) {
                if (seen.containsKey(inv.name())) {
                    issues.add(error("CHR-022",
                        "Duplicate invariant name '" + inv.name() + "' in entity '" + entity.name() + "'",
                        seen.get(inv.name())));
                    issues.add(error("CHR-022",
                        "Duplicate invariant name '" + inv.name() + "' in entity '" + entity.name() + "'",
                        inv.span()));
                } else {
                    seen.put(inv.name(), inv.span());
                }
            }
        }

        var seen = new HashMap<String, Span>();
        for (InvariantDef inv : model.invariants()) {
            if (seen.containsKey(inv.name())) {
                issues.add(error("CHR-022",
                    "Duplicate global invariant name '" + inv.name() + "'",
                    seen.get(inv.name())));
                issues.add(error("CHR-022",
                    "Duplicate global invariant name '" + inv.name() + "'",
                    inv.span()));
            } else {
                seen.put(inv.name(), inv.span());
            }
        }
    }

    // ── CHR-023 ────────────────────────────────────────────────────────────────

    /** Every deny must include a description. */
    private void checkChr023(IrModel model, List<Diagnostic> issues) {
        for (DenyDef deny : model.denies()) {
            if (deny.description() == null || deny.description().isEmpty()) {
                issues.add(error("CHR-023",
                    "Deny '" + deny.name() + "' must include a description",
                    deny.span()));
            }
        }
    }

    // ── CHR-024 ────────────────────────────────────────────────────────────────

    /** Deny scope entities must be defined or imported. */
    private void checkChr024(IrModel model, List<Diagnostic> issues) {
        Set<String> importedNames = model.imports().stream()
                .map(UseDecl::name)
                .collect(Collectors.toSet());

        for (DenyDef deny : model.denies()) {
            for (String entityName : deny.scope()) {
                boolean resolved = model.findShape(entityName).isPresent()
                        || importedNames.contains(entityName);
                if (!resolved) {
                    issues.add(error("CHR-024",
                        "Deny '" + deny.name() + "' references undefined entity '" + entityName + "' in scope",
                        deny.span()));
                }
            }
        }
    }

    // ── CHR-025 ────────────────────────────────────────────────────────────────

    /** Deny severity must be one of: critical, high, medium, low. */
    private void checkChr025(IrModel model, List<Diagnostic> issues) {
        Set<String> validSeverities = Set.of("critical", "high", "medium", "low");

        for (DenyDef deny : model.denies()) {
            if (deny.severity() == null || deny.severity().isEmpty()) {
                issues.add(error("CHR-025",
                    "Deny '" + deny.name() + "' must specify a severity",
                    deny.span()));
            } else if (!validSeverities.contains(deny.severity())) {
                issues.add(error("CHR-025",
                    "Deny '" + deny.name() + "' has invalid severity '" + deny.severity() +
                    "' (must be one of: critical, high, medium, low)",
                    deny.span()));
            }
        }
    }

    // ── CHR-026 ────────────────────────────────────────────────────────────────

    /** Error codes must be unique across the namespace. */
    private void checkChr026(IrModel model, List<Diagnostic> issues) {
        var seen = new LinkedHashMap<String, Span>();
        var reported = new HashSet<String>();

        for (ErrorDef errorDef : model.errors()) {
            String code = errorDef.code();
            if (seen.containsKey(code)) {
                if (reported.add(code)) {
                    issues.add(error("CHR-026",
                            "Duplicate error code '" + code + "'",
                            seen.get(code)));
                }
                issues.add(error("CHR-026",
                        "Duplicate error code '" + code + "'",
                        errorDef.span()));
            } else {
                seen.put(code, errorDef.span());
            }
        }
    }

    // ── CHR-027 ────────────────────────────────────────────────────────────────

    /** Variant triggers must reference a defined error type (string triggers are not supported). */
    private void checkChr027(IrModel model, List<Diagnostic> issues) {
        Set<String> definedErrors = model.errors().stream()
                .map(ErrorDef::name)
                .collect(Collectors.toSet());
        // In multi-file compilation the error type may be imported rather than locally defined.
        Set<String> importedNames = model.imports().stream()
                .map(UseDecl::name)
                .collect(Collectors.toSet());

        for (JourneyDef journey : model.journeys()) {
            for (Variant variant : journey.variants().values()) {
                String trigger = variant.triggerName();

                if (!definedErrors.contains(trigger) && !importedNames.contains(trigger)) {
                    issues.add(error("CHR-027",
                            "Variant trigger must reference a defined error type. " +
                            "Error type '" + trigger + "' is not defined. " +
                            "Define the error type or check for typos.",
                            variant.span()));
                }
            }
        }
    }

    // ── CHR-028 ────────────────────────────────────────────────────────────────

    /** Error severity must be one of: critical, high, medium, low. */
    private void checkChr028(IrModel model, List<Diagnostic> issues) {
        Set<String> validSeverities = Set.of("critical", "high", "medium", "low");

        for (ErrorDef errorDef : model.errors()) {
            if (!validSeverities.contains(errorDef.severity())) {
                issues.add(error("CHR-028",
                    "Error '" + errorDef.name() + "' has invalid severity '" + errorDef.severity() +
                    "' (must be one of: critical, high, medium, low)",
                    errorDef.span()));
            }
        }
    }

    // ── CHR-029 ────────────────────────────────────────────────────────────────

    /** All states referenced in transitions must be declared in the states list. */
    private void checkChr029(IrModel model, List<Diagnostic> issues) {
        for (StateMachineDef sm : model.stateMachines()) {
            Set<String> declaredStates = new HashSet<>(sm.states());

            for (Transition t : sm.transitions()) {
                if (!declaredStates.contains(t.fromState())) {
                    issues.add(error("CHR-029",
                            "State '" + t.fromState() + "' in transition is not declared in states list",
                            t.span()));
                }
                if (!declaredStates.contains(t.toState())) {
                    issues.add(error("CHR-029",
                            "State '" + t.toState() + "' in transition is not declared in states list",
                            t.span()));
                }
            }
        }
    }

    // ── CHR-030 ────────────────────────────────────────────────────────────────

    /** Every non-terminal state must have at least one outbound transition. */
    private void checkChr030(IrModel model, List<Diagnostic> issues) {
        for (StateMachineDef sm : model.stateMachines()) {
            Set<String> terminalStates = new HashSet<>(sm.terminalStates());
            Set<String> statesWithOutbound = new HashSet<>();

            for (Transition t : sm.transitions()) {
                statesWithOutbound.add(t.fromState());
            }

            for (String state : sm.states()) {
                if (!terminalStates.contains(state) && !statesWithOutbound.contains(state)) {
                    issues.add(error("CHR-030",
                            "Non-terminal state '" + state + "' has no outbound transitions",
                            sm.span()));
                }
            }
        }
    }

    // ── CHR-031 ────────────────────────────────────────────────────────────────

    /** The initial state must be in the states list. */
    private void checkChr031(IrModel model, List<Diagnostic> issues) {
        for (StateMachineDef sm : model.stateMachines()) {
            if (!sm.initialState().isEmpty() && !sm.states().contains(sm.initialState())) {
                issues.add(error("CHR-031",
                        "Initial state '" + sm.initialState() + "' is not declared in states list",
                        sm.span()));
            }
        }
    }

    // ── CHR-032 ────────────────────────────────────────────────────────────────

    /** Terminal states must not have outbound transitions. */
    private void checkChr032(IrModel model, List<Diagnostic> issues) {
        for (StateMachineDef sm : model.stateMachines()) {
            Set<String> terminalStates = new HashSet<>(sm.terminalStates());

            for (Transition t : sm.transitions()) {
                if (terminalStates.contains(t.fromState())) {
                    issues.add(error("CHR-032",
                            "Terminal state '" + t.fromState() + "' must not have outbound transitions",
                            t.span()));
                }
            }
        }
    }

    // ── CHR-033 ────────────────────────────────────────────────────────────────

    /** The referenced entity and field must be defined; field type should be an enum. */
    private void checkChr033(IrModel model, List<Diagnostic> issues) {
        Map<String, EntityDef> entityMap = model.entities().stream()
                .collect(Collectors.toMap(EntityDef::name, e -> e, (e1, e2) -> e1));
        Map<String, EnumDef> enumMap = model.enums().stream()
                .collect(Collectors.toMap(EnumDef::name, e -> e, (e1, e2) -> e1));
        // In multi-file compilation the entity may be imported rather than locally defined.
        Set<String> importedNames = model.imports().stream()
                .map(UseDecl::name)
                .collect(Collectors.toSet());

        for (StateMachineDef sm : model.stateMachines()) {
            EntityDef entity = entityMap.get(sm.entityName());
            if (entity == null) {
                if (importedNames.contains(sm.entityName())) {
                    // Entity is imported from another file; field/enum checks are skipped.
                    continue;
                }
                issues.add(error("CHR-033",
                        "Entity '" + sm.entityName() + "' referenced in statemachine '" + sm.name() + "' is not defined",
                        sm.span()));
                continue;
            }

            FieldDef field = entity.fields().stream()
                    .filter(f -> f.name().equals(sm.fieldName()))
                    .findFirst()
                    .orElse(null);

            if (field == null) {
                issues.add(error("CHR-033",
                        "Field '" + sm.fieldName() + "' referenced in statemachine '" + sm.name() +
                        "' is not defined on entity '" + sm.entityName() + "'",
                        sm.span()));
                continue;
            }

            if (field.type() instanceof TypeRef.NamedTypeRef namedType) {
                String typeName = namedType.qualifiedId();
                if (!enumMap.containsKey(typeName)) {
                    issues.add(error("CHR-033",
                            "Field '" + sm.fieldName() + "' on entity '" + sm.entityName() +
                            "' has type '" + typeName + "' which is not an enum",
                            sm.span()));
                }
            } else {
                issues.add(error("CHR-033",
                        "Field '" + sm.fieldName() + "' on entity '" + sm.entityName() +
                        "' must have an enum type, but has type '" + field.type() + "'",
                        sm.span()));
            }
        }
    }

    // ── CHR-034 ────────────────────────────────────────────────────────────────

    /** TransitionTo() in journey steps must reference a state declared in a statemachine. */
    private void checkChr034(IrModel model, List<Diagnostic> issues) {
        // In multi-file compilation, statemachines may be defined in a different file.
        // If this file declares no statemachines, skip the check rather than false-positiving.
        if (model.stateMachines().isEmpty()) return;

        Set<String> allDeclaredStates = new HashSet<>();
        for (StateMachineDef sm : model.stateMachines()) {
            allDeclaredStates.addAll(sm.states());
        }

        for (JourneyDef journey : model.journeys()) {
            for (Step step : journey.steps()) {
                checkStepOutcome(step, allDeclaredStates, issues);
            }

            for (Variant variant : journey.variants().values()) {
                for (Step step : variant.steps()) {
                    checkStepOutcome(step, allDeclaredStates, issues);
                }
            }
        }
    }

    private void checkStepOutcome(Step step, Set<String> allDeclaredStates, List<Diagnostic> issues) {
        step.outcome().ifPresent(outcome -> {
            if (outcome instanceof OutcomeExpr.TransitionTo transitionTo) {
                String targetState = transitionTo.stateId();
                if (!allDeclaredStates.contains(targetState)) {
                    issues.add(error("CHR-034",
                            "TransitionTo('" + targetState + "') in step '" + step.name() +
                            "' references a state that is not declared in any statemachine",
                            transitionTo.span()));
                }
            }
        });
    }

    // ── CHR-W001 ───────────────────────────────────────────────────────────────

    /** Warn when invariants reference optional fields without null guards. */
    private void checkChrW001(IrModel model, List<Diagnostic> issues) {
        var resolver = new IrInheritanceResolver(model);

        for (EntityDef entity : model.entities()) {
            var allFields = resolver.resolveAllFields(entity);
            var optionalFields = allFields.stream()
                    .filter(f -> !f.isRequired())
                    .map(FieldDef::name)
                    .collect(Collectors.toSet());

            for (EntityInvariant inv : entity.invariants()) {
                checkOptionalFieldReferences(inv.expression(), optionalFields,
                        inv.name(), inv.span(), issues);
            }
        }

        for (InvariantDef inv : model.invariants()) {
            var optionalFields = new HashSet<String>();

            for (String scopeEntity : inv.scope()) {
                var entity = model.entities().stream()
                        .filter(e -> e.name().equals(scopeEntity))
                        .findFirst();

                if (entity.isPresent()) {
                    var allFields = resolver.resolveAllFields(entity.get());
                    allFields.stream()
                            .filter(f -> !f.isRequired())
                            .forEach(f -> optionalFields.add(scopeEntity + "." + f.name()));
                }
            }

            checkOptionalFieldReferences(inv.expression(), optionalFields,
                    inv.name(), inv.span(), issues);
        }
    }

    private void checkOptionalFieldReferences(String expression, Set<String> optionalFields,
                                               String invariantName, Span span,
                                               List<Diagnostic> issues) {
        if (optionalFields.isEmpty()) {
            return;
        }

        var tokens = expression.split("[\\s()\\[\\],+\\-*/=!<>&|]+");
        var tokenSet = Arrays.stream(tokens)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toSet());

        for (String optionalField : optionalFields) {
            boolean isReferenced = tokenSet.contains(optionalField);

            if (isReferenced) {
                boolean hasNullGuard = expression.contains(optionalField + " != null") ||
                                       expression.contains(optionalField + " == null") ||
                                       expression.contains("null != " + optionalField) ||
                                       expression.contains("null == " + optionalField);

                if (!hasNullGuard) {
                    issues.add(warning("CHR-W001",
                            "Invariant '" + invariantName + "' references optional field '" +
                            optionalField + "' without a null guard",
                            span));
                }
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Extracts the simple name from a {@link SymbolRef}, using the resolved id when
     * available and falling back to the unresolved qualified name.
     */
    private static String symRefName(SymbolRef ref) {
        if (ref == null) return "";
        return ref.isResolved() ? ref.id().name() : ref.name().name();
    }

    /**
     * Returns the value of the first {@code @kpi} trait's {@code metric} argument,
     * or empty if no {@code @kpi} trait is present.
     */
    private static Optional<String> kpiMetric(JourneyDef journey) {
        return journey.traits().stream()
                .filter(t -> "kpi".equals(t.name()))
                .flatMap(t -> t.namedValue("metric").stream())
                .filter(v -> v instanceof TraitValue.StringValue)
                .map(v -> ((TraitValue.StringValue) v).value())
                .findFirst();
    }

    /** Returns {@code true} if this journey carries any {@code @compliance} trait. */
    private static boolean hasComplianceTrait(JourneyDef journey) {
        return journey.traits().stream().anyMatch(t -> "compliance".equals(t.name()));
    }

    // ── Factories ──────────────────────────────────────────────────────────────

    private static Diagnostic error(String code, String message, Span span) {
        return Diagnostic.error(code, message, span != null ? span : Span.UNKNOWN);
    }

    private static Diagnostic warning(String code, String message, Span span) {
        return Diagnostic.warning(code, message, span != null ? span : Span.UNKNOWN);
    }
}
