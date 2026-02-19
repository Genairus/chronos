package com.genairus.chronos.validator;

import com.genairus.chronos.model.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates a {@link ChronosModel} against semantic rules CHR-001 through CHR-032 and CHR-W001.
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
 *   CHR-017 ERROR   Traits on parent entities propagate to children unless explicitly overridden
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
                default -> { /* ActorDef, PolicyDef, EnumDef, JourneyDef, InvariantDef have no field types */ }
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

    // ── CHR-011 ────────────────────────────────────────────────────────────────

    /** Relationship targets must reference defined or imported entities. */
    private void checkChr011(ChronosModel model, List<ValidationIssue> issues) {
        Set<String> importedNames = model.imports().stream()
                .map(UseDecl::shapeName)
                .collect(java.util.stream.Collectors.toSet());

        for (RelationshipDef rel : model.relationships()) {
            // Check fromEntity
            if (!isEntityDefined(rel.fromEntity(), model, importedNames)) {
                issues.add(error("CHR-011",
                        "Relationship '" + rel.name() + "' references undefined entity '" + rel.fromEntity() + "' in 'from' field",
                        rel.location()));
            }

            // Check toEntity
            if (!isEntityDefined(rel.toEntity(), model, importedNames)) {
                issues.add(error("CHR-011",
                        "Relationship '" + rel.name() + "' references undefined entity '" + rel.toEntity() + "' in 'to' field",
                        rel.location()));
            }
        }
    }

    private boolean isEntityDefined(String entityName, ChronosModel model, Set<String> importedNames) {
        // Check if it's a defined entity in the model
        boolean isDefined = model.entities().stream()
                .anyMatch(e -> e.name().equals(entityName));

        // Check if it's imported
        boolean isImported = importedNames.contains(entityName);

        return isDefined || isImported;
    }

    // ── CHR-012 ────────────────────────────────────────────────────────────────

    /** Composition targets cannot be referenced by more than one composing entity. */
    private void checkChr012(ChronosModel model, List<ValidationIssue> issues) {
        // Build a map of composition target -> list of relationships that compose it
        var compositionTargets = new java.util.HashMap<String, java.util.ArrayList<RelationshipDef>>();

        for (RelationshipDef rel : model.relationships()) {
            if (rel.effectiveSemantics() == RelationshipSemantics.COMPOSITION) {
                compositionTargets
                    .computeIfAbsent(rel.toEntity(), k -> new java.util.ArrayList<>())
                    .add(rel);
            }
        }

        // Check for targets referenced by multiple composition relationships
        for (var entry : compositionTargets.entrySet()) {
            String target = entry.getKey();
            var relationships = entry.getValue();

            if (relationships.size() > 1) {
                // Report error on all but the first relationship
                for (int i = 1; i < relationships.size(); i++) {
                    RelationshipDef rel = relationships.get(i);
                    issues.add(error("CHR-012",
                            "Entity '" + target + "' is already composed by relationship '" +
                            relationships.get(0).name() + "'; composition target cannot be referenced by multiple composing entities",
                            rel.location()));
                }
            }
        }
    }

    // ── CHR-014 ────────────────────────────────────────────────────────────────

    /** Inverse field name (if specified) must exist on the target entity. */
    private void checkChr014(ChronosModel model, List<ValidationIssue> issues) {
        for (RelationshipDef rel : model.relationships()) {
            if (rel.inverseField().isEmpty()) {
                continue; // No inverse field specified, nothing to validate
            }

            String inverseFieldName = rel.inverseField().get();
            String targetEntityName = rel.toEntity();

            // Find the target entity
            var targetEntity = model.entities().stream()
                    .filter(e -> e.name().equals(targetEntityName))
                    .findFirst();

            if (targetEntity.isEmpty()) {
                // Target entity doesn't exist - this is already caught by CHR-011
                continue;
            }

            // Check if the inverse field exists on the target entity
            boolean fieldExists = targetEntity.get().fields().stream()
                    .anyMatch(f -> f.name().equals(inverseFieldName));

            if (!fieldExists) {
                issues.add(error("CHR-014",
                        "Relationship '" + rel.name() + "' specifies inverse field '" + inverseFieldName +
                        "' but entity '" + targetEntityName + "' has no such field",
                        rel.location()));
            }
        }
    }

    // ── CHR-015 ────────────────────────────────────────────────────────────────

    /** Circular inheritance chains are a validation error. */
    private void checkChr015(ChronosModel model, List<ValidationIssue> issues) {
        var resolver = new InheritanceResolver(model);

        // Check entities for circular inheritance
        for (EntityDef entity : model.entities()) {
            if (resolver.hasCircularInheritance(entity)) {
                issues.add(error("CHR-015",
                        "Entity '" + entity.name() + "' has a circular inheritance chain",
                        entity.location()));
            }
        }

        // Check actors for circular inheritance
        for (ActorDef actor : model.actors()) {
            if (resolver.hasCircularInheritance(actor)) {
                issues.add(error("CHR-015",
                        "Actor '" + actor.name() + "' has a circular inheritance chain",
                        actor.location()));
            }
        }
    }

    // ── CHR-016 ────────────────────────────────────────────────────────────────

    /** A child entity may not redefine a parent field with an incompatible type. */
    private void checkChr016(ChronosModel model, List<ValidationIssue> issues) {
        for (EntityDef entity : model.entities()) {
            // Skip entities without a parent
            if (entity.parentType().isEmpty()) {
                continue;
            }

            // Find the parent entity
            String parentName = entity.parentType().get();
            var parent = model.entities().stream()
                    .filter(e -> e.name().equals(parentName))
                    .findFirst();

            if (parent.isEmpty()) {
                // Parent not found - this is already caught by CHR-008
                continue;
            }

            // Check each field in the child entity
            for (FieldDef childField : entity.fields()) {
                // See if this field exists in the parent
                var parentField = parent.get().fields().stream()
                        .filter(f -> f.name().equals(childField.name()))
                        .findFirst();

                if (parentField.isPresent()) {
                    // Field is being overridden - check type compatibility
                    if (!areTypesCompatible(parentField.get().type(), childField.type())) {
                        issues.add(error("CHR-016",
                                "Entity '" + entity.name() + "' redefines field '" + childField.name() +
                                "' with incompatible type '" + formatType(childField.type()) +
                                "' (parent type is '" + formatType(parentField.get().type()) + "')",
                                entity.location()));
                    }
                }
            }
        }
    }

    /**
     * Checks if two types are compatible for field override.
     * Currently implements strict equality - the types must be exactly the same.
     * Future enhancement: support actual subtype relationships.
     */
    private boolean areTypesCompatible(TypeRef parentType, TypeRef childType) {
        return parentType.equals(childType);
    }

    /**
     * Formats a TypeRef for display in error messages.
     */
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
    private void checkChr018(ChronosModel model, List<ValidationIssue> issues) {
        // Note: The grammar already enforces single inheritance by allowing only one
        // parent in the 'extends' clause. This check is here for completeness and
        // to provide a clear error message if the grammar is ever extended.
        // Currently, this check will never trigger because the parser only allows
        // a single parent type.

        // This is a placeholder for future-proofing. If we ever support multiple
        // inheritance syntax in the grammar, this check would detect it.
        // For now, we rely on the grammar to enforce single inheritance.
    }

    // ── CHR-019 ────────────────────────────────────────────────────────────────

    /** Invariant expressions must reference only fields visible in scope. */
    private void checkChr019(ChronosModel model, List<ValidationIssue> issues) {
        // For entity-scoped invariants, validate field references against entity fields
        for (EntityDef entity : model.entities()) {
            var resolver = new InheritanceResolver(model);
            var allFields = resolver.resolveAllFields(entity);
            var fieldNames = allFields.stream().map(FieldDef::name).toList();

            for (EntityInvariant inv : entity.invariants()) {
                validateFieldReferences(inv.expression(), fieldNames,
                    "Entity invariant '" + inv.name() + "' in entity '" + entity.name() + "'",
                    inv.location(), issues);
            }
        }

        // For global invariants, validate field references against scope entities
        for (InvariantDef inv : model.invariants()) {
            var scopeFieldNames = new java.util.ArrayList<String>();
            for (String entityName : inv.scope()) {
                var entity = model.entities().stream()
                    .filter(e -> e.name().equals(entityName))
                    .findFirst();

                if (entity.isPresent()) {
                    var resolver = new InheritanceResolver(model);
                    var allFields = resolver.resolveAllFields(entity.get());
                    // Add qualified field names (entity.field)
                    for (FieldDef field : allFields) {
                        scopeFieldNames.add(entityName + "." + field.name());
                    }
                }
            }

            validateFieldReferences(inv.expression(), scopeFieldNames,
                "Global invariant '" + inv.name() + "'",
                inv.location(), issues);
        }
    }

    /**
     * Validates that field references in an expression exist in the allowed field names.
     * This is a simple lexical check that looks for identifiers in the expression.
     * Note: This is a basic implementation that handles common cases but may have
     * false positives/negatives with complex expressions. A full implementation
     * would require parsing the expression AST.
     */
    private void validateFieldReferences(String expression, List<String> allowedFields,
                                        String context, SourceLocation loc, List<ValidationIssue> issues) {
        // Simple validation: extract potential field references and check if they exist
        // This handles simple cases like "total > 0" or qualified names like "Order.customerId"

        // Extract lambda parameter names to skip them during validation
        // Pattern: "identifier =>" captures the lambda parameter
        var lambdaParams = new java.util.HashSet<String>();
        var lambdaPattern = java.util.regex.Pattern.compile("(\\w+)\\s*=>");
        var lambdaMatcher = lambdaPattern.matcher(expression);
        while (lambdaMatcher.find()) {
            lambdaParams.add(lambdaMatcher.group(1));
        }

        // Split on common delimiters, but preserve dots for qualified names
        var tokens = expression.split("[\\s()\\[\\],+\\-*/=!<>&|]+");

        for (String token : tokens) {
            if (token.isEmpty() || token.matches("\\d+(\\.\\d+)?")) {
                continue; // Skip empty tokens and numeric literals
            }
            if (token.equals("true") || token.equals("false") || token.equals("null")) {
                continue; // Skip boolean and null literals
            }
            if (token.matches("\".*\"") || token.matches("'.*'")) {
                continue; // Skip string literals
            }
            // Check if it's a known aggregation function, operator, or entity type name
            if (token.equals("count") || token.equals("sum") || token.equals("min") ||
                token.equals("max") || token.equals("exists") || token.equals("forAll") ||
                token.equals("error") || token.equals("warning") || token.equals("info")) {
                continue;
            }
            // Skip entity type names (start with uppercase) - but only for simple names, not qualified names
            if (!token.isEmpty() && !token.contains(".") && Character.isUpperCase(token.charAt(0))) {
                continue;
            }

            // Skip lambda parameters and their qualified references (e.g., "c" and "c.id")
            if (token.contains(".")) {
                String prefix = token.substring(0, token.indexOf('.'));
                if (lambdaParams.contains(prefix)) {
                    continue; // This is a lambda parameter reference like "c.id"
                }
            } else if (lambdaParams.contains(token)) {
                continue; // This is a lambda parameter like "c"
            }

            // Check if this token is a valid field reference
            // For qualified names (e.g., "Order.customerId"), check if it's in the allowed list
            // For simple names (e.g., "total"), check if any allowed field ends with it
            boolean isValid = false;
            if (token.contains(".")) {
                // Qualified name - must match exactly
                isValid = allowedFields.contains(token);
            } else {
                // Simple name - check if it matches any allowed field (unqualified or qualified)
                isValid = allowedFields.stream().anyMatch(f ->
                    f.equals(token) || f.endsWith("." + token));
            }

            if (!isValid && !token.isEmpty()) {
                // Report error for invalid field references
                // For qualified names, always report (they should be field references)
                // For simple names, only report if they start with lowercase (likely field names)
                if (token.contains(".") || Character.isLowerCase(token.charAt(0))) {
                    issues.add(error("CHR-019",
                        context + " references undefined field '" + token + "'",
                        loc));
                }
            }
        }
    }

    // ── CHR-020 ────────────────────────────────────────────────────────────────

    /** Severity must be one of: error, warning, info. */
    private void checkChr020(ChronosModel model, List<ValidationIssue> issues) {
        var validSeverities = java.util.Set.of("error", "warning", "info");

        // Check entity-scoped invariants
        for (EntityDef entity : model.entities()) {
            for (EntityInvariant inv : entity.invariants()) {
                if (!validSeverities.contains(inv.severity())) {
                    issues.add(error("CHR-020",
                        "Invariant '" + inv.name() + "' has invalid severity '" + inv.severity() +
                        "' (must be one of: error, warning, info)",
                        inv.location()));
                }
            }
        }

        // Check global invariants
        for (InvariantDef inv : model.invariants()) {
            if (!validSeverities.contains(inv.severity())) {
                issues.add(error("CHR-020",
                    "Invariant '" + inv.name() + "' has invalid severity '" + inv.severity() +
                    "' (must be one of: error, warning, info)",
                    inv.location()));
            }
        }
    }

    // ── CHR-021 ────────────────────────────────────────────────────────────────

    /** Global invariants must declare a scope listing all referenced entities. */
    private void checkChr021(ChronosModel model, List<ValidationIssue> issues) {
        for (InvariantDef inv : model.invariants()) {
            if (inv.scope().isEmpty()) {
                issues.add(error("CHR-021",
                    "Global invariant '" + inv.name() + "' must declare a non-empty scope",
                    inv.location()));
            }

            // Validate that all entities in scope exist
            for (String entityName : inv.scope()) {
                boolean exists = model.entities().stream()
                    .anyMatch(e -> e.name().equals(entityName));

                if (!exists) {
                    issues.add(error("CHR-021",
                        "Global invariant '" + inv.name() + "' references undefined entity '" +
                        entityName + "' in scope",
                        inv.location()));
                }
            }
        }
    }

    // ── CHR-022 ────────────────────────────────────────────────────────────────

    /** Invariant names must be unique within their enclosing scope. */
    private void checkChr022(ChronosModel model, List<ValidationIssue> issues) {
        // Check entity-scoped invariants for uniqueness within each entity
        for (EntityDef entity : model.entities()) {
            var seen = new java.util.HashMap<String, SourceLocation>();
            for (EntityInvariant inv : entity.invariants()) {
                if (seen.containsKey(inv.name())) {
                    issues.add(error("CHR-022",
                        "Duplicate invariant name '" + inv.name() + "' in entity '" + entity.name() + "'",
                        seen.get(inv.name())));
                    issues.add(error("CHR-022",
                        "Duplicate invariant name '" + inv.name() + "' in entity '" + entity.name() + "'",
                        inv.location()));
                } else {
                    seen.put(inv.name(), inv.location());
                }
            }
        }

        // Check global invariants for uniqueness at model level
        var seen = new java.util.HashMap<String, SourceLocation>();
        for (InvariantDef inv : model.invariants()) {
            if (seen.containsKey(inv.name())) {
                issues.add(error("CHR-022",
                    "Duplicate global invariant name '" + inv.name() + "'",
                    seen.get(inv.name())));
                issues.add(error("CHR-022",
                    "Duplicate global invariant name '" + inv.name() + "'",
                    inv.location()));
            } else {
                seen.put(inv.name(), inv.location());
            }
        }
    }

    // ── CHR-023 ────────────────────────────────────────────────────────────────

    /** Every deny must include a description. */
    private void checkChr023(ChronosModel model, List<ValidationIssue> issues) {
        for (DenyDef deny : model.denies()) {
            if (deny.description() == null || deny.description().isEmpty()) {
                issues.add(error("CHR-023",
                    "Deny '" + deny.name() + "' must include a description",
                    deny.location()));
            }
        }
    }

    // ── CHR-024 ────────────────────────────────────────────────────────────────

    /** Deny scope entities must be defined or imported. */
    private void checkChr024(ChronosModel model, List<ValidationIssue> issues) {
        // Build set of imported shape names
        Set<String> importedNames = model.imports().stream()
                .map(UseDecl::shapeName)
                .collect(java.util.stream.Collectors.toSet());

        for (DenyDef deny : model.denies()) {
            for (String entityName : deny.scope()) {
                boolean resolved = model.findShape(entityName).isPresent()
                        || importedNames.contains(entityName);
                if (!resolved) {
                    issues.add(error("CHR-024",
                        "Deny '" + deny.name() + "' references undefined entity '" + entityName + "' in scope",
                        deny.location()));
                }
            }
        }
    }

    // ── CHR-025 ────────────────────────────────────────────────────────────────

    /** Deny severity must be one of: critical, high, medium, low. */
    private void checkChr025(ChronosModel model, List<ValidationIssue> issues) {
        Set<String> validSeverities = Set.of("critical", "high", "medium", "low");

        for (DenyDef deny : model.denies()) {
            if (deny.severity() == null || deny.severity().isEmpty()) {
                issues.add(error("CHR-025",
                    "Deny '" + deny.name() + "' must specify a severity",
                    deny.location()));
            } else if (!validSeverities.contains(deny.severity())) {
                issues.add(error("CHR-025",
                    "Deny '" + deny.name() + "' has invalid severity '" + deny.severity() +
                    "' (must be one of: critical, high, medium, low)",
                    deny.location()));
            }
        }
    }

    // ── CHR-026 ────────────────────────────────────────────────────────────────

    /** Error codes must be unique across the namespace. */
    private void checkChr026(ChronosModel model, List<ValidationIssue> issues) {
        var seen = new LinkedHashMap<String, SourceLocation>();
        var reported = new HashSet<String>();

        for (ErrorDef error : model.errors()) {
            String code = error.code();
            if (seen.containsKey(code)) {
                if (reported.add(code)) {
                    // Report at the first occurrence
                    issues.add(error("CHR-026",
                            "Duplicate error code '" + code + "'",
                            seen.get(code)));
                }
                issues.add(error("CHR-026",
                        "Duplicate error code '" + code + "'",
                        error.location()));
            } else {
                seen.put(code, error.location());
            }
        }
    }

    // ── CHR-027 ────────────────────────────────────────────────────────────────

    /** Variant triggers must reference a defined error type (string triggers are not supported). */
    private void checkChr027(ChronosModel model, List<ValidationIssue> issues) {
        // Collect all defined error type names
        Set<String> definedErrors = model.errors().stream()
                .map(ErrorDef::name)
                .collect(java.util.stream.Collectors.toSet());

        // Check all variant triggers in all journeys
        for (JourneyDef journey : model.journeys()) {
            for (Variant variant : journey.variants().values()) {
                String trigger = variant.trigger();

                if (!definedErrors.contains(trigger)) {
                    issues.add(error("CHR-027",
                            "Variant trigger must reference a defined error type. " +
                            "Error type '" + trigger + "' is not defined. " +
                            "Define the error type or check for typos.",
                            variant.location()));
                }
            }
        }
    }

    // ── CHR-028 ────────────────────────────────────────────────────────────────

    /** Error severity must be one of: critical, high, medium, low. */
    private void checkChr028(ChronosModel model, List<ValidationIssue> issues) {
        Set<String> validSeverities = Set.of("critical", "high", "medium", "low");

        for (ErrorDef error : model.errors()) {
            if (!validSeverities.contains(error.severity())) {
                issues.add(error("CHR-028",
                    "Error '" + error.name() + "' has invalid severity '" + error.severity() +
                    "' (must be one of: critical, high, medium, low)",
                    error.location()));
            }
        }
    }

    // ── CHR-W001 ───────────────────────────────────────────────────────────────

    /** Warn when invariants reference optional fields without null guards. */
    private void checkChrW001(ChronosModel model, List<ValidationIssue> issues) {
        var resolver = new InheritanceResolver(model);

        // Check entity-scoped invariants
        for (EntityDef entity : model.entities()) {
            var allFields = resolver.resolveAllFields(entity);
            var optionalFields = allFields.stream()
                    .filter(f -> !f.isRequired())
                    .map(FieldDef::name)
                    .collect(java.util.stream.Collectors.toSet());

            for (EntityInvariant inv : entity.invariants()) {
                checkOptionalFieldReferences(inv.expression(), optionalFields,
                        inv.name(), inv.location(), issues);
            }
        }

        // Check global invariants
        for (InvariantDef inv : model.invariants()) {
            var optionalFields = new java.util.HashSet<String>();

            // Collect optional fields from all entities in scope
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
                    inv.name(), inv.location(), issues);
        }
    }

    /**
     * Check if an expression references optional fields without null guards.
     * A null guard is detected by checking if the expression contains patterns like:
     * - field != null
     * - field == null
     * - null != field
     * - null == field
     */
    private void checkOptionalFieldReferences(String expression, Set<String> optionalFields,
                                               String invariantName, SourceLocation location,
                                               List<ValidationIssue> issues) {
        if (optionalFields.isEmpty()) {
            return;
        }

        // Extract field references from the expression (simple lexical approach)
        // Split on common delimiters, but preserve dots for qualified names
        var tokens = expression.split("[\\s()\\[\\],+\\-*/=!<>&|]+");
        var tokenSet = java.util.Arrays.stream(tokens)
                .filter(t -> !t.isEmpty())
                .collect(java.util.stream.Collectors.toSet());

        // Check each optional field to see if it's referenced
        for (String optionalField : optionalFields) {
            boolean isReferenced = tokenSet.contains(optionalField);

            if (isReferenced) {
                // Check if there's a null guard for this field
                boolean hasNullGuard = expression.contains(optionalField + " != null") ||
                                       expression.contains(optionalField + " == null") ||
                                       expression.contains("null != " + optionalField) ||
                                       expression.contains("null == " + optionalField);

                if (!hasNullGuard) {
                    issues.add(warning("CHR-W001",
                            "Invariant '" + invariantName + "' references optional field '" +
                            optionalField + "' without a null guard",
                            location));
                }
            }
        }
    }

    // ── CHR-029 ────────────────────────────────────────────────────────────────

    /** All states referenced in transitions must be declared in the states list. */
    private void checkChr029(ChronosModel model, List<ValidationIssue> issues) {
        for (StateMachineDef sm : model.stateMachines()) {
            Set<String> declaredStates = new HashSet<>(sm.states());

            for (Transition t : sm.transitions()) {
                if (!declaredStates.contains(t.fromState())) {
                    issues.add(error("CHR-029",
                            "State '" + t.fromState() + "' in transition is not declared in states list",
                            t.location()));
                }
                if (!declaredStates.contains(t.toState())) {
                    issues.add(error("CHR-029",
                            "State '" + t.toState() + "' in transition is not declared in states list",
                            t.location()));
                }
            }
        }
    }

    // ── CHR-030 ────────────────────────────────────────────────────────────────

    /** Every non-terminal state must have at least one outbound transition. */
    private void checkChr030(ChronosModel model, List<ValidationIssue> issues) {
        for (StateMachineDef sm : model.stateMachines()) {
            Set<String> terminalStates = new HashSet<>(sm.terminalStates());
            Set<String> statesWithOutbound = new HashSet<>();

            // Collect all states that have outbound transitions
            for (Transition t : sm.transitions()) {
                statesWithOutbound.add(t.fromState());
            }

            // Check each non-terminal state has at least one outbound transition
            for (String state : sm.states()) {
                if (!terminalStates.contains(state) && !statesWithOutbound.contains(state)) {
                    issues.add(error("CHR-030",
                            "Non-terminal state '" + state + "' has no outbound transitions",
                            sm.location()));
                }
            }
        }
    }

    // ── CHR-031 ────────────────────────────────────────────────────────────────

    /** The initial state must be in the states list. */
    private void checkChr031(ChronosModel model, List<ValidationIssue> issues) {
        for (StateMachineDef sm : model.stateMachines()) {
            if (!sm.initialState().isEmpty() && !sm.states().contains(sm.initialState())) {
                issues.add(error("CHR-031",
                        "Initial state '" + sm.initialState() + "' is not declared in states list",
                        sm.location()));
            }
        }
    }

    // ── CHR-032 ────────────────────────────────────────────────────────────────

    /** Terminal states must not have outbound transitions. */
    private void checkChr032(ChronosModel model, List<ValidationIssue> issues) {
        for (StateMachineDef sm : model.stateMachines()) {
            Set<String> terminalStates = new HashSet<>(sm.terminalStates());

            for (Transition t : sm.transitions()) {
                if (terminalStates.contains(t.fromState())) {
                    issues.add(error("CHR-032",
                            "Terminal state '" + t.fromState() + "' must not have outbound transitions",
                            t.location()));
                }
            }
        }
    }

    /**
     * CHR-033: The referenced entity and field must be defined; field type should be an enum.
     */
    private void checkChr033(ChronosModel model, List<ValidationIssue> issues) {
        // Build maps for quick lookup - use merge function to handle duplicates gracefully
        Map<String, EntityDef> entityMap = model.entities().stream()
                .collect(Collectors.toMap(EntityDef::name, e -> e, (e1, e2) -> e1));
        Map<String, EnumDef> enumMap = model.enums().stream()
                .collect(Collectors.toMap(EnumDef::name, e -> e, (e1, e2) -> e1));

        for (StateMachineDef sm : model.stateMachines()) {
            // Check entity exists
            EntityDef entity = entityMap.get(sm.entityName());
            if (entity == null) {
                issues.add(error("CHR-033",
                        "Entity '" + sm.entityName() + "' referenced in statemachine '" + sm.name() + "' is not defined",
                        sm.location()));
                continue; // Can't check field if entity doesn't exist
            }

            // Check field exists on entity
            FieldDef field = entity.fields().stream()
                    .filter(f -> f.name().equals(sm.fieldName()))
                    .findFirst()
                    .orElse(null);

            if (field == null) {
                issues.add(error("CHR-033",
                        "Field '" + sm.fieldName() + "' referenced in statemachine '" + sm.name() + "' is not defined on entity '" + sm.entityName() + "'",
                        sm.location()));
                continue; // Can't check type if field doesn't exist
            }

            // Check field type is an enum
            if (field.type() instanceof TypeRef.NamedTypeRef namedType) {
                String typeName = namedType.qualifiedId();
                if (!enumMap.containsKey(typeName)) {
                    issues.add(error("CHR-033",
                            "Field '" + sm.fieldName() + "' on entity '" + sm.entityName() + "' has type '" + typeName + "' which is not an enum",
                            sm.location()));
                }
            } else {
                issues.add(error("CHR-033",
                        "Field '" + sm.fieldName() + "' on entity '" + sm.entityName() + "' must have an enum type, but has type '" + field.type() + "'",
                        sm.location()));
            }
        }
    }

    // ── CHR-034 ────────────────────────────────────────────────────────────────

    /** TransitionTo() in journey steps must reference a state declared in a statemachine. */
    private void checkChr034(ChronosModel model, List<ValidationIssue> issues) {
        // Build a set of all states declared in all statemachines
        Set<String> allDeclaredStates = new HashSet<>();
        for (StateMachineDef sm : model.stateMachines()) {
            allDeclaredStates.addAll(sm.states());
        }

        // Check all journey steps for TransitionTo() references
        for (JourneyDef journey : model.journeys()) {
            // Check main journey steps
            for (Step step : journey.steps()) {
                checkStepOutcome(step, allDeclaredStates, issues);
            }

            // Check variant steps
            for (Variant variant : journey.variants().values()) {
                for (Step step : variant.steps()) {
                    checkStepOutcome(step, allDeclaredStates, issues);
                }
            }
        }
    }

    private void checkStepOutcome(Step step, Set<String> allDeclaredStates, List<ValidationIssue> issues) {
        step.outcome().ifPresent(outcome -> {
            if (outcome instanceof OutcomeExpr.TransitionTo transitionTo) {
                String targetState = transitionTo.target();
                if (!allDeclaredStates.contains(targetState)) {
                    issues.add(error("CHR-034",
                            "TransitionTo('" + targetState + "') in step '" + step.name() + "' references a state that is not declared in any statemachine",
                            transitionTo.location()));
                }
            }
        });
    }

    // ── Factories ──────────────────────────────────────────────────────────────

    private static ValidationIssue error(String code, String message, SourceLocation loc) {
        return new ValidationIssue(code, ValidationSeverity.ERROR, message, loc);
    }

    private static ValidationIssue warning(String code, String message, SourceLocation loc) {
        return new ValidationIssue(code, ValidationSeverity.WARNING, message, loc);
    }
}
