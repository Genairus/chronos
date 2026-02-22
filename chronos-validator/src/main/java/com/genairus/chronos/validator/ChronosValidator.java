package com.genairus.chronos.validator;

import com.genairus.chronos.core.diagnostics.Diagnostic;
import com.genairus.chronos.core.refs.Span;
import com.genairus.chronos.core.refs.SymbolRef;
import com.genairus.chronos.ir.model.IrInheritanceResolver;
import com.genairus.chronos.ir.model.IrModel;
import com.genairus.chronos.ir.types.*;
import com.genairus.chronos.validator.expr.ExprType;
import com.genairus.chronos.validator.expr.ExprTypeChecker;
import com.genairus.chronos.validator.expr.ExpressionParser;
import com.genairus.chronos.validator.expr.InvariantExpr;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates an {@link IrModel} against semantic rules CHR-001 through CHR-041 and CHR-W001.
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
 *   CHR-035 ERROR   Output field names must be unique across all steps in a journey scope
 *   CHR-036 ERROR   Step input field must be produced as output by a preceding step in the journey
 *   CHR-037 ERROR   @authorize role name must reference a declared role in the model
 *   CHR-038 ERROR   @authorize permission must be listed in the role's allow list
 *   CHR-039 ERROR   Journey actor must carry @authorize(role: X) matching the journey's required role
 *   CHR-040 ERROR   @authorize permission must not be in the role's deny list
 *   CHR-041 ERROR   Step telemetry event must be a declared or imported event type
 *   CHR-042 ERROR   Invariant expression failed to parse
 *   CHR-043 WARNING Type mismatch in invariant expression
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
        checkChr035(model, issues);
        checkChr036(model, issues);
        checkChr037(model, issues);
        checkChr038(model, issues);
        checkChr039(model, issues);
        checkChr040(model, issues);
        checkChr041(model, issues);
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

    // ── CHR-019 / CHR-042 / CHR-043 ──────────────────────────────────────────

    /** Invariant expressions must reference only fields visible in scope. */
    private void checkChr019(IrModel model, List<Diagnostic> issues) {
        var resolver = new IrInheritanceResolver(model);

        for (EntityDef entity : model.entities()) {
            var allFields = resolver.resolveAllFields(entity);
            var fieldNames = allFields.stream().map(FieldDef::name).toList();
            var fieldTypeMap = buildFieldTypeMap(allFields);

            for (EntityInvariant inv : entity.invariants()) {
                String context = "Entity invariant '" + inv.name() + "' in entity '" + entity.name() + "'";
                InvariantExpr ast = parseInvariantExpr(inv.expression(), context, inv.span(), issues);
                checkFieldRefsChr019(ast, fieldNames, Set.of(), context, inv.span(), issues);
                new ExprTypeChecker().check(ast, fieldTypeMap, Set.of(), inv.name(), inv.span(), issues);
            }
        }

        for (InvariantDef inv : model.invariants()) {
            var scopeFieldNames = new ArrayList<String>();
            var scopeFieldTypeMap = new HashMap<String, ExprType>();

            for (String entityName : inv.scope()) {
                var entity = model.entities().stream()
                    .filter(e -> e.name().equals(entityName))
                    .findFirst();

                if (entity.isPresent()) {
                    var allFields = resolver.resolveAllFields(entity.get());
                    for (FieldDef field : allFields) {
                        String qualifiedName = entityName + "." + field.name();
                        scopeFieldNames.add(qualifiedName);
                        scopeFieldTypeMap.put(qualifiedName, toExprType(field.type()));
                    }
                }
            }

            String context = "Global invariant '" + inv.name() + "'";
            InvariantExpr ast = parseInvariantExpr(inv.expression(), context, inv.span(), issues);
            checkFieldRefsChr019(ast, scopeFieldNames, Set.of(), context, inv.span(), issues);
            new ExprTypeChecker().check(ast, scopeFieldTypeMap, Set.of(), inv.name(), inv.span(), issues);
        }
    }

    /**
     * Parses the expression string into an AST. Emits CHR-042 if parsing fails.
     * Always returns a non-null node — returns {@link InvariantExpr.ParseError} on failure.
     */
    private InvariantExpr parseInvariantExpr(String expression, String context,
                                              Span span, List<Diagnostic> issues) {
        InvariantExpr ast = new ExpressionParser().parse(expression);
        if (ast instanceof InvariantExpr.ParseError err) {
            issues.add(error("CHR-042",
                    context + ": expression failed to parse: " + err.message(), span));
        }
        return ast;
    }

    /**
     * Walks the AST and emits CHR-019 for any {@link InvariantExpr.FieldRef} that is
     * not in {@code allowedFields} and is not shadowed by a lambda parameter.
     */
    private void checkFieldRefsChr019(InvariantExpr expr, List<String> allowedFields,
                                       Set<String> lambdaScope, String context,
                                       Span span, List<Diagnostic> issues) {
        switch (expr) {
            case InvariantExpr.FieldRef f -> {
                String name = f.name();
                // Determine the leading component (before first dot)
                String prefix = name.contains(".") ? name.substring(0, name.indexOf('.')) : name;
                if (lambdaScope.contains(prefix)) break; // lambda param — skip

                boolean isValid = name.contains(".")
                        ? allowedFields.contains(name)
                        : allowedFields.stream().anyMatch(a -> a.equals(name) || a.endsWith("." + name));

                if (!isValid) {
                    issues.add(error("CHR-019",
                            context + " references undefined field '" + name + "'", span));
                }
            }
            case InvariantExpr.Lambda lam -> {
                var newScope = extendLambdaScope(lambdaScope, lam.param());
                checkFieldRefsChr019(lam.body(), allowedFields, newScope, context, span, issues);
            }
            case InvariantExpr.BinaryOp b -> {
                checkFieldRefsChr019(b.left(),  allowedFields, lambdaScope, context, span, issues);
                checkFieldRefsChr019(b.right(), allowedFields, lambdaScope, context, span, issues);
            }
            case InvariantExpr.UnaryOp u ->
                checkFieldRefsChr019(u.operand(), allowedFields, lambdaScope, context, span, issues);
            case InvariantExpr.AggregateCall agg -> {
                checkFieldRefsChr019(agg.target(), allowedFields, lambdaScope, context, span, issues);
                var newScope = extendLambdaScope(lambdaScope, agg.lambda().param());
                checkFieldRefsChr019(agg.lambda().body(), allowedFields, newScope, context, span, issues);
            }
            default -> { /* literals, EnumRef, ParseError — nothing to check */ }
        }
    }

    private static Set<String> extendLambdaScope(Set<String> existing, String param) {
        if (existing.isEmpty()) return Set.of(param);
        var extended = new HashSet<>(existing);
        extended.add(param);
        return extended;
    }

    private Map<String, ExprType> buildFieldTypeMap(List<FieldDef> fields) {
        var map = new HashMap<String, ExprType>();
        for (FieldDef f : fields) {
            map.put(f.name(), toExprType(f.type()));
        }
        return map;
    }

    private ExprType toExprType(TypeRef ref) {
        return switch (ref) {
            case TypeRef.PrimitiveType p -> switch (p.kind()) {
                case STRING    -> ExprType.STRING;
                case INTEGER, LONG -> ExprType.INTEGER;
                case FLOAT     -> ExprType.FLOAT;
                case BOOLEAN   -> ExprType.BOOLEAN;
                case TIMESTAMP -> ExprType.TIMESTAMP;
                case BLOB, DOCUMENT -> ExprType.OPAQUE;
            };
            default -> ExprType.UNKNOWN;
        };
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

    // ── CHR-035 ────────────────────────────────────────────────────────────────

    /**
     * Output field names must be unique across all steps in a journey scope.
     *
     * <p>If two steps in the same journey (or variant) declare output fields with
     * the same name the downstream consumer cannot determine which value is intended.
     */
    private void checkChr035(IrModel model, List<Diagnostic> issues) {
        for (JourneyDef journey : model.journeys()) {
            checkOutputDuplicatesChr035(journey.name(), journey.steps(), issues);
            for (Variant variant : journey.variants().values()) {
                checkOutputDuplicatesChr035(
                        journey.name() + "/" + variant.name(), variant.steps(), issues);
            }
        }
    }

    private void checkOutputDuplicatesChr035(String context, List<Step> steps,
                                              List<Diagnostic> issues) {
        var seen     = new LinkedHashMap<String, Span>();
        var reported = new HashSet<String>();
        for (Step step : steps) {
            for (DataField field : step.outputFields()) {
                String name = field.name();
                if (seen.containsKey(name)) {
                    if (reported.add(name)) {
                        issues.add(error("CHR-035",
                                "Duplicate output field name '" + name
                                + "' in journey '" + context + "'",
                                seen.get(name)));
                    }
                    issues.add(error("CHR-035",
                            "Duplicate output field name '" + name
                            + "' in journey '" + context + "'",
                            field.span()));
                } else {
                    seen.put(name, field.span());
                }
            }
        }
    }

    // ── CHR-036 ────────────────────────────────────────────────────────────────

    /**
     * A step's input field must be produced as output by a preceding step in the
     * same journey (or variant) sequence.
     *
     * <p>Each step can only consume data that earlier steps have explicitly exposed
     * via an {@code output} block. The check is purely positional: the accumulating
     * set of available field names grows as steps are visited in order.
     */
    private void checkChr036(IrModel model, List<Diagnostic> issues) {
        for (JourneyDef journey : model.journeys()) {
            checkInputAvailabilityChr036(journey.name(), journey.steps(), issues);
            for (Variant variant : journey.variants().values()) {
                checkInputAvailabilityChr036(
                        journey.name() + "/" + variant.name(), variant.steps(), issues);
            }
        }
    }

    private void checkInputAvailabilityChr036(String context, List<Step> steps,
                                               List<Diagnostic> issues) {
        var available = new HashSet<String>();
        for (Step step : steps) {
            for (DataField inputField : step.inputFields()) {
                if (!available.contains(inputField.name())) {
                    issues.add(error("CHR-036",
                            "Step '" + step.name() + "' in '" + context
                            + "' declares input field '" + inputField.name()
                            + "' which is not produced by any upstream step",
                            inputField.span()));
                }
            }
            // Accumulate outputs after checking inputs so a step cannot consume
            // its own outputs in the same step.
            for (DataField outputField : step.outputFields()) {
                available.add(outputField.name());
            }
        }
    }

    // ── CHR-037 ────────────────────────────────────────────────────────────────

    /** @authorize role name must reference a declared role in the model. */
    private void checkChr037(IrModel model, List<Diagnostic> issues) {
        Set<String> roleNames = model.roles().stream()
                .map(RoleDef::name)
                .collect(Collectors.toSet());

        for (JourneyDef journey : model.journeys()) {
            for (TraitApplication t : authorizeTraits(journey.traits())) {
                extractAuthorizeRole(t).ifPresent(role -> {
                    if (!roleNames.contains(role))
                        issues.add(error("CHR-037",
                                "Journey '" + journey.name() + "' @authorize references undefined role '" + role + "'",
                                journey.span()));
                });
            }
            checkStepAuthRolesChr037("journey '" + journey.name() + "'", journey.steps(), roleNames, issues);
            for (Variant v : journey.variants().values()) {
                checkStepAuthRolesChr037("variant '" + v.name() + "'", v.steps(), roleNames, issues);
            }
        }
        for (ActorDef actor : model.actors()) {
            for (TraitApplication t : authorizeTraits(actor.traits())) {
                extractAuthorizeRole(t).ifPresent(role -> {
                    if (!roleNames.contains(role))
                        issues.add(error("CHR-037",
                                "Actor '" + actor.name() + "' @authorize references undefined role '" + role + "'",
                                actor.span()));
                });
            }
        }
    }

    private void checkStepAuthRolesChr037(String context, List<Step> steps,
                                           Set<String> roleNames, List<Diagnostic> issues) {
        for (Step step : steps) {
            for (TraitApplication t : authorizeTraits(step.traits())) {
                extractAuthorizeRole(t).ifPresent(role -> {
                    if (!roleNames.contains(role))
                        issues.add(error("CHR-037",
                                "Step '" + step.name() + "' in " + context
                                + " @authorize references undefined role '" + role + "'",
                                step.span()));
                });
            }
        }
    }

    // ── CHR-038 ────────────────────────────────────────────────────────────────

    /** @authorize permission must be listed in the role's allow list. */
    private void checkChr038(IrModel model, List<Diagnostic> issues) {
        Map<String, RoleDef> rolesByName = model.roles().stream()
                .collect(Collectors.toMap(RoleDef::name, r -> r, (a, b) -> a));

        for (JourneyDef journey : model.journeys()) {
            for (TraitApplication t : authorizeTraits(journey.traits())) {
                checkAuthPermChr038(t, "Journey '" + journey.name() + "'", journey.span(), rolesByName, issues);
            }
            checkStepsAuthPermChr038("journey '" + journey.name() + "'", journey.steps(), rolesByName, issues);
            for (Variant v : journey.variants().values()) {
                checkStepsAuthPermChr038("variant '" + v.name() + "'", v.steps(), rolesByName, issues);
            }
        }
    }

    private void checkAuthPermChr038(TraitApplication t, String ctx, Span span,
                                      Map<String, RoleDef> rolesByName, List<Diagnostic> issues) {
        extractAuthorizeRole(t).ifPresent(role -> extractAuthorizePermission(t).ifPresent(perm -> {
            RoleDef roleDef = rolesByName.get(role);
            if (roleDef != null && !roleDef.allowedPermissions().contains(perm)) {
                issues.add(error("CHR-038",
                        ctx + " @authorize permission '" + perm
                        + "' is not in the allow list of role '" + role + "'",
                        span));
            }
        }));
    }

    private void checkStepsAuthPermChr038(String context, List<Step> steps,
                                           Map<String, RoleDef> rolesByName, List<Diagnostic> issues) {
        for (Step step : steps) {
            for (TraitApplication t : authorizeTraits(step.traits())) {
                checkAuthPermChr038(t, "Step '" + step.name() + "' in " + context, step.span(), rolesByName, issues);
            }
        }
    }

    // ── CHR-039 ────────────────────────────────────────────────────────────────

    /** Journey actor must carry @authorize(role: X) matching the journey's required role. */
    private void checkChr039(IrModel model, List<Diagnostic> issues) {
        Map<String, ActorDef> actorsByName = model.actors().stream()
                .collect(Collectors.toMap(ActorDef::name, a -> a, (a, b) -> a));

        for (JourneyDef journey : model.journeys()) {
            List<TraitApplication> journeyAuthTraits = authorizeTraits(journey.traits());
            if (journeyAuthTraits.isEmpty()) continue;

            Optional<String> actorNameOpt = journey.actorName();
            if (actorNameOpt.isEmpty()) continue; // CHR-001 catches missing actor

            ActorDef actor = actorsByName.get(actorNameOpt.get());
            if (actor == null) continue; // not locally defined — skip

            Set<String> actorRoles = authorizeTraits(actor.traits()).stream()
                    .flatMap(t -> extractAuthorizeRole(t).stream())
                    .collect(Collectors.toSet());

            for (TraitApplication t : journeyAuthTraits) {
                extractAuthorizeRole(t).ifPresent(requiredRole -> {
                    if (!actorRoles.contains(requiredRole)) {
                        issues.add(error("CHR-039",
                                "Journey '" + journey.name() + "' requires role '" + requiredRole
                                + "' but actor '" + actor.name()
                                + "' does not carry @authorize(role: " + requiredRole + ")",
                                journey.span()));
                    }
                });
            }
        }
    }

    // ── CHR-040 ────────────────────────────────────────────────────────────────

    /** @authorize permission must not be in the role's deny list. */
    private void checkChr040(IrModel model, List<Diagnostic> issues) {
        Map<String, RoleDef> rolesByName = model.roles().stream()
                .collect(Collectors.toMap(RoleDef::name, r -> r, (a, b) -> a));

        for (JourneyDef journey : model.journeys()) {
            for (TraitApplication t : authorizeTraits(journey.traits())) {
                checkAuthDenyChr040(t, "Journey '" + journey.name() + "'", journey.span(), rolesByName, issues);
            }
            checkStepsAuthDenyChr040("journey '" + journey.name() + "'", journey.steps(), rolesByName, issues);
            for (Variant v : journey.variants().values()) {
                checkStepsAuthDenyChr040("variant '" + v.name() + "'", v.steps(), rolesByName, issues);
            }
        }
    }

    private void checkAuthDenyChr040(TraitApplication t, String ctx, Span span,
                                      Map<String, RoleDef> rolesByName, List<Diagnostic> issues) {
        extractAuthorizeRole(t).ifPresent(role -> extractAuthorizePermission(t).ifPresent(perm -> {
            RoleDef roleDef = rolesByName.get(role);
            if (roleDef != null && roleDef.deniedPermissions().contains(perm)) {
                issues.add(error("CHR-040",
                        ctx + " @authorize permission '" + perm
                        + "' is explicitly denied by role '" + role + "'",
                        span));
            }
        }));
    }

    private void checkStepsAuthDenyChr040(String context, List<Step> steps,
                                           Map<String, RoleDef> rolesByName, List<Diagnostic> issues) {
        for (Step step : steps) {
            for (TraitApplication t : authorizeTraits(step.traits())) {
                checkAuthDenyChr040(t, "Step '" + step.name() + "' in " + context, step.span(), rolesByName, issues);
            }
        }
    }

    // ── CHR-041 ────────────────────────────────────────────────────────────────

    /** Step telemetry event must reference a declared or imported event type. */
    private void checkChr041(IrModel model, List<Diagnostic> issues) {
        var knownEvents = new HashSet<String>();
        model.events().forEach(e -> knownEvents.add(e.name()));
        model.imports().forEach(imp -> knownEvents.add(imp.name()));

        for (JourneyDef journey : model.journeys()) {
            checkTelemetryChr041(journey.name(), journey.steps(), knownEvents, issues);
            for (Variant v : journey.variants().values()) {
                checkTelemetryChr041(journey.name() + "/" + v.name(),
                        v.steps(), knownEvents, issues);
            }
        }
    }

    private void checkTelemetryChr041(String context, List<Step> steps,
                                       Set<String> knownEvents, List<Diagnostic> issues) {
        for (Step step : steps) {
            for (String eventId : step.telemetryEvents()) {
                if (!knownEvents.contains(eventId)) {
                    issues.add(error("CHR-041",
                            "Telemetry event '" + eventId + "' in step '" + step.name()
                                    + "' (journey '" + context + "') is not a declared event",
                            step.span()));
                }
            }
        }
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
                // Re-use the already-parsed AST if CHR-019 ran first; for simplicity,
                // we parse again here — it's cheap and keeps the method self-contained.
                InvariantExpr ast = new ExpressionParser().parse(inv.expression());
                checkNullGuardChrW001(ast, optionalFields, inv.name(), inv.span(), issues);
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

            InvariantExpr ast = new ExpressionParser().parse(inv.expression());
            checkNullGuardChrW001(ast, optionalFields, inv.name(), inv.span(), issues);
        }
    }

    /**
     * Emits CHR-W001 for each optional field that is referenced in the expression
     * but lacks a null guard (i.e. no {@code field == null} / {@code field != null}
     * anywhere in the AST).
     */
    private void checkNullGuardChrW001(InvariantExpr ast, Set<String> optionalFields,
                                        String invariantName, Span span,
                                        List<Diagnostic> issues) {
        if (optionalFields.isEmpty()) return;

        // Collect outer (non-lambda-param) field refs and null-guard subjects
        var outerFieldRefs = new HashSet<String>();
        collectOuterFieldRefs(ast, Set.of(), outerFieldRefs);

        var nullGuardSubjects = new HashSet<String>();
        collectNullGuards(ast, nullGuardSubjects);

        for (String optField : optionalFields) {
            if (outerFieldRefs.contains(optField) && !nullGuardSubjects.contains(optField)) {
                issues.add(warning("CHR-W001",
                        "Invariant '" + invariantName + "' references optional field '" +
                        optField + "' without a null guard",
                        span));
            }
        }
    }

    /**
     * Collects all {@link InvariantExpr.FieldRef} names that are NOT shadowed by a
     * lambda parameter into {@code result}.
     */
    private void collectOuterFieldRefs(InvariantExpr expr, Set<String> lambdaScope,
                                        Set<String> result) {
        switch (expr) {
            case InvariantExpr.FieldRef f -> {
                String prefix = f.name().contains(".")
                        ? f.name().substring(0, f.name().indexOf('.')) : f.name();
                if (!lambdaScope.contains(prefix)) result.add(f.name());
            }
            case InvariantExpr.Lambda lam -> {
                var newScope = extendLambdaScope(lambdaScope, lam.param());
                collectOuterFieldRefs(lam.body(), newScope, result);
            }
            case InvariantExpr.BinaryOp b -> {
                collectOuterFieldRefs(b.left(),  lambdaScope, result);
                collectOuterFieldRefs(b.right(), lambdaScope, result);
            }
            case InvariantExpr.UnaryOp u ->
                collectOuterFieldRefs(u.operand(), lambdaScope, result);
            case InvariantExpr.AggregateCall agg -> {
                collectOuterFieldRefs(agg.target(), lambdaScope, result);
                var newScope = extendLambdaScope(lambdaScope, agg.lambda().param());
                collectOuterFieldRefs(agg.lambda().body(), newScope, result);
            }
            default -> { /* literals, EnumRef, ParseError — no field refs */ }
        }
    }

    /**
     * Collects the subjects of null-guard comparisons anywhere in the AST.
     * A null guard is: {@code fieldRef == null}, {@code fieldRef != null},
     * {@code null == fieldRef}, or {@code null != fieldRef}.
     */
    private void collectNullGuards(InvariantExpr expr, Set<String> guards) {
        switch (expr) {
            case InvariantExpr.BinaryOp b when (b.op() == InvariantExpr.BinOp.EQ
                    || b.op() == InvariantExpr.BinOp.NEQ) -> {
                extractNullGuardSubject(b.left(), b.right(), guards);
                extractNullGuardSubject(b.right(), b.left(), guards);
                collectNullGuards(b.left(),  guards);
                collectNullGuards(b.right(), guards);
            }
            case InvariantExpr.BinaryOp b -> {
                collectNullGuards(b.left(),  guards);
                collectNullGuards(b.right(), guards);
            }
            case InvariantExpr.UnaryOp u  -> collectNullGuards(u.operand(), guards);
            case InvariantExpr.Lambda lam -> collectNullGuards(lam.body(), guards);
            case InvariantExpr.AggregateCall agg -> {
                collectNullGuards(agg.target(), guards);
                collectNullGuards(agg.lambda().body(), guards);
            }
            default -> { /* leaf nodes */ }
        }
    }

    private void extractNullGuardSubject(InvariantExpr fieldSide, InvariantExpr nullSide,
                                          Set<String> guards) {
        if (fieldSide instanceof InvariantExpr.FieldRef f
                && nullSide instanceof InvariantExpr.NullLit) {
            guards.add(f.name());
        }
    }

    // ── Authorization helpers ──────────────────────────────────────────────────

    /** Returns all {@code @authorize} traits from the given list. */
    private static List<TraitApplication> authorizeTraits(List<TraitApplication> traits) {
        return traits.stream()
                .filter(t -> "authorize".equals(t.name()))
                .toList();
    }

    /**
     * Extracts the {@code role} argument value from an {@code @authorize} trait.
     * Expects the value to be a {@link TraitValue.ReferenceValue} (unquoted identifier).
     */
    private static Optional<String> extractAuthorizeRole(TraitApplication t) {
        return t.namedValue("role")
                .filter(v -> v instanceof TraitValue.ReferenceValue)
                .map(v -> ((TraitValue.ReferenceValue) v).ref());
    }

    /**
     * Extracts the {@code permission} argument value from an {@code @authorize} trait.
     * Expects the value to be a {@link TraitValue.ReferenceValue} (unquoted identifier).
     */
    private static Optional<String> extractAuthorizePermission(TraitApplication t) {
        return t.namedValue("permission")
                .filter(v -> v instanceof TraitValue.ReferenceValue)
                .map(v -> ((TraitValue.ReferenceValue) v).ref());
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
