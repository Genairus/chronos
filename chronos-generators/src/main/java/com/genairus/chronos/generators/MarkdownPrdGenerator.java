package com.genairus.chronos.generators;

import com.genairus.chronos.core.refs.SymbolRef;
import com.genairus.chronos.ir.model.IrInheritanceResolver;
import com.genairus.chronos.ir.model.IrModel;
import com.genairus.chronos.ir.types.ActorDef;
import com.genairus.chronos.ir.types.DenyDef;
import com.genairus.chronos.ir.types.EntityDef;
import com.genairus.chronos.ir.types.EnumDef;
import com.genairus.chronos.ir.types.ErrorDef;
import com.genairus.chronos.ir.types.FieldDef;
import com.genairus.chronos.ir.types.InvariantDef;
import com.genairus.chronos.ir.types.IrShape;
import com.genairus.chronos.ir.types.JourneyDef;
import com.genairus.chronos.ir.types.ListDef;
import com.genairus.chronos.ir.types.MapDef;
import com.genairus.chronos.ir.types.OutcomeExpr;
import com.genairus.chronos.ir.types.PolicyDef;
import com.genairus.chronos.ir.types.RelationshipDef;
import com.genairus.chronos.ir.types.ShapeStructDef;
import com.genairus.chronos.ir.types.Step;
import com.genairus.chronos.ir.types.TraitValue;
import com.genairus.chronos.ir.types.TypeRef;
import com.genairus.chronos.ir.types.Variant;

import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;

/**
 * Generates a Markdown Product Requirements Document (PRD) from a
 * {@link IrModel}.
 *
 * <p>The output is a single {@code <namespace>-prd.md} file with the following
 * structure (sections with no content are omitted from both the TOC and body):
 * <ol>
 *   <li>Title — {@code namespace — Product Requirements}</li>
 *   <li>Table of Contents — anchor links to each populated section</li>
 *   <li>Journeys — metadata block, preconditions, happy-path step table,
 *       variant subsections, and outcome statements</li>
 *   <li>Data Model — entity/value-object field tables, enum member tables,
 *       and collection type summaries</li>
 *   <li>Relationships — relationship metadata with cardinality, semantics, and inverse fields</li>
 *   <li>Actors — name and {@code @description}</li>
 *   <li>Policies — name, description, and compliance framework</li>
 *   <li>Error Catalog — error codes, severity, recoverability, and payload schemas</li>
 * </ol>
 */
public final class MarkdownPrdGenerator implements ChronosGenerator {

    private static final String DASH    = "—";
    private static final String DIVIDER = "\n---\n";

    @Override
    public GeneratorOutput generate(IrModel model) {
        var sb = new StringBuilder();
        var inheritanceResolver = new IrInheritanceResolver(model);

        appendTitle(sb, model);
        appendToc(sb, model);
        appendJourneysSection(sb, model);
        appendDataModelSection(sb, model, inheritanceResolver);
        appendGlobalInvariantsSection(sb, model);
        appendRelationshipsSection(sb, model);
        appendActorsSection(sb, model);
        appendPoliciesSection(sb, model);
        appendProhibitionsSection(sb, model);
        appendErrorCatalogSection(sb, model);

        String filename = model.namespace().replace('.', '-') + "-prd.md";
        return GeneratorOutput.of(filename, sb.toString());
    }

    // ── Title ─────────────────────────────────────────────────────────────────

    private static void appendTitle(StringBuilder sb, IrModel model) {
        sb.append("# ").append(model.namespace()).append(" — Product Requirements\n");
    }

    // ── Table of Contents ─────────────────────────────────────────────────────

    private static void appendToc(StringBuilder sb, IrModel model) {
        sb.append("\n## Table of Contents\n\n");

        if (!model.journeys().isEmpty()) {
            sb.append("- [Journeys](#journeys)\n");
            for (var j : model.journeys()) {
                sb.append("  - [").append(j.name())
                  .append("](#").append(anchor(j.name())).append(")\n");
            }
        }

        boolean hasDataModel = !model.entities().isEmpty()
                || !model.shapeStructs().isEmpty()
                || !model.enums().isEmpty()
                || !model.lists().isEmpty()
                || !model.maps().isEmpty();
        if (hasDataModel) {
            sb.append("- [Data Model](#data-model)\n");
            if (!model.entities().isEmpty())
                sb.append("  - [Entities](#entities)\n");
            if (!model.shapeStructs().isEmpty())
                sb.append("  - [Value Objects](#value-objects)\n");
            if (!model.enums().isEmpty())
                sb.append("  - [Enumerations](#enumerations)\n");
            if (!model.lists().isEmpty() || !model.maps().isEmpty())
                sb.append("  - [Collections](#collections)\n");
        }

        if (!model.invariants().isEmpty())
            sb.append("- [Global Invariants](#global-invariants)\n");
        if (!model.relationships().isEmpty())
            sb.append("- [Relationships](#relationships)\n");
        if (!model.actors().isEmpty())
            sb.append("- [Actors](#actors)\n");
        if (!model.policies().isEmpty())
            sb.append("- [Policies](#policies)\n");
        if (!model.denies().isEmpty())
            sb.append("- [Prohibitions](#prohibitions)\n");
        if (!model.errors().isEmpty())
            sb.append("- [Error Catalog](#error-catalog)\n");
    }

    // ── Journeys ──────────────────────────────────────────────────────────────

    private static void appendJourneysSection(StringBuilder sb, IrModel model) {
        if (model.journeys().isEmpty()) return;
        sb.append(DIVIDER).append("\n## Journeys\n");
        for (var journey : model.journeys()) {
            appendJourney(sb, journey);
        }
    }

    private static void appendJourney(StringBuilder sb, JourneyDef journey) {
        sb.append("\n### ").append(journey.name()).append("\n\n");

        // Doc comments as blockquote
        for (var doc : journey.docComments()) {
            sb.append("> ").append(doc).append("\n");
        }
        if (!journey.docComments().isEmpty()) sb.append(">\n");

        // Metadata: actor | KPI | compliance
        var meta = new StringJoiner(" | ");
        journey.actorName().ifPresent(a -> meta.add("**Actor:** " + a));
        journey.traits().stream()
                .filter(t -> "kpi".equals(t.name()))
                .findFirst()
                .ifPresent(kpi -> {
                    String metric = kpi.namedValue("metric")
                            .filter(v -> v instanceof TraitValue.StringValue)
                            .map(v -> ((TraitValue.StringValue) v).value())
                            .orElse(null);
                    String target = kpi.namedValue("target")
                            .filter(v -> v instanceof TraitValue.StringValue)
                            .map(v -> ((TraitValue.StringValue) v).value())
                            .orElse(null);
                    if (metric != null) {
                        meta.add("**KPI:** " + metric + (target != null ? " → " + target : ""));
                    }
                });
        journey.traits().stream()
                .filter(t -> "compliance".equals(t.name()))
                .flatMap(t -> t.firstPositionalValue().stream())
                .filter(v -> v instanceof TraitValue.StringValue)
                .map(v -> ((TraitValue.StringValue) v).value())
                .forEach(c -> meta.add("**Compliance:** " + c));
        String metaStr = meta.toString();
        if (!metaStr.isEmpty()) {
            sb.append("> ").append(metaStr).append("\n\n");
        }

        // Preconditions
        if (!journey.preconditions().isEmpty()) {
            sb.append("**Preconditions**\n\n");
            for (var pre : journey.preconditions()) {
                sb.append("- ").append(pre).append("\n");
            }
            sb.append("\n");
        }

        // Happy-path steps
        if (!journey.steps().isEmpty()) {
            sb.append("**Happy Path**\n\n");
            appendStepTable(sb, journey.steps());
            sb.append("\n");
        }

        // Variants
        if (!journey.variants().isEmpty()) {
            sb.append("**Variants**\n");
            for (var entry : journey.variants().entrySet()) {
                appendVariant(sb, entry.getKey(), entry.getValue());
            }
            sb.append("\n");
        }

        // Journey-level outcomes
        journey.journeyOutcomes().ifPresent(o -> {
            sb.append("**Outcomes**\n\n");
            if (o.successOrNull() != null) sb.append("- \u2705 Success: ").append(o.successOrNull()).append("\n");
            if (o.failureOrNull() != null) sb.append("- \u274c Failure: ").append(o.failureOrNull()).append("\n");
            sb.append("\n");
        });
    }

    private static void appendStepTable(StringBuilder sb, List<Step> steps) {
        sb.append("| Step | Action | Expectation | Outcome | Telemetry | Risk |\n");
        sb.append("|------|--------|-------------|---------|-----------|------|\n");
        for (var step : steps) {
            String telemetry = step.telemetryEvents().isEmpty()
                    ? DASH
                    : String.join(", ", step.telemetryEvents());
            sb.append("| ").append(step.name())
              .append(" | ").append(step.action().orElse(DASH))
              .append(" | ").append(step.expectation().orElse(DASH))
              .append(" | ").append(step.outcome().map(MarkdownPrdGenerator::renderOutcomeExpr).orElse(DASH))
              .append(" | ").append(telemetry)
              .append(" | ").append(step.risk().orElse(DASH))
              .append(" |\n");
        }
    }

    private static void appendVariant(StringBuilder sb, String name, Variant variant) {
        sb.append("\n#### ").append(name).append("\n\n");
        sb.append("- **Trigger:** ").append(variant.triggerName()).append("\n");
        if (!variant.steps().isEmpty()) {
            sb.append("\n");
            appendStepTable(sb, variant.steps());
        }
        variant.outcome().ifPresent(o ->
                sb.append("- **Outcome:** ").append(renderOutcomeExpr(o)).append("\n"));
    }

    // ── Data Model ────────────────────────────────────────────────────────────

    private static void appendDataModelSection(StringBuilder sb, IrModel model, IrInheritanceResolver resolver) {
        boolean hasDataModel = !model.entities().isEmpty()
                || !model.shapeStructs().isEmpty()
                || !model.enums().isEmpty()
                || !model.lists().isEmpty()
                || !model.maps().isEmpty();
        if (!hasDataModel) return;

        sb.append(DIVIDER).append("\n## Data Model\n");

        appendFieldedShapes(sb, model.entities(), "Entities", resolver);
        appendFieldedShapes(sb, model.shapeStructs(), "Value Objects", resolver);
        appendEnumsSubsection(sb, model.enums());
        appendCollectionsSubsection(sb, model.lists(), model.maps());
    }

    private static void appendFieldedShapes(StringBuilder sb,
                                             List<? extends IrShape> shapes,
                                             String heading,
                                             IrInheritanceResolver resolver) {
        if (shapes.isEmpty()) return;
        sb.append("\n### ").append(heading).append("\n");
        for (var shape : shapes) {
            List<String>   docs;
            List<FieldDef> fields;
            if (shape instanceof EntityDef e) {
                docs   = e.docComments();
                // Use IrInheritanceResolver to get all fields including inherited ones
                fields = resolver.resolveAllFields(e);
            } else if (shape instanceof ShapeStructDef s) {
                docs   = s.docComments();
                fields = s.fields();
            } else {
                continue;
            }
            sb.append("\n#### ").append(shape.name()).append("\n\n");

            // Show parent type if entity has one
            if (shape instanceof EntityDef e && IrInheritanceResolver.parentName(e).isPresent()) {
                sb.append("*Extends: ").append(IrInheritanceResolver.parentName(e).get()).append("*\n\n");
            }

            for (var doc : docs) {
                sb.append("> ").append(doc).append("\n");
            }
            if (!docs.isEmpty()) sb.append("\n");
            if (!fields.isEmpty()) {
                sb.append("| Field | Type | Required |\n");
                sb.append("|-------|------|----------|\n");
                for (var field : fields) {
                    sb.append("| ").append(field.name())
                      .append(" | ").append(renderTypeRef(field.type()))
                      .append(" | ").append(field.isRequired() ? "\u2713" : "")
                      .append(" |\n");
                }
            }

            // Add invariants section for entities
            if (shape instanceof EntityDef e && !e.invariants().isEmpty()) {
                sb.append("\n**Invariants:**\n\n");
                for (var inv : e.invariants()) {
                    sb.append("- **").append(inv.name()).append("**");
                    sb.append(" (").append(inv.severity()).append(")");
                    sb.append("\n  - Expression: `").append(inv.expression()).append("`\n");
                    if (inv.message().isPresent()) {
                        sb.append("  - Message: ").append(inv.message().get()).append("\n");
                    }
                }
            }
        }
    }

    private static void appendEnumsSubsection(StringBuilder sb, List<EnumDef> enums) {
        if (enums.isEmpty()) return;
        sb.append("\n### Enumerations\n");
        enums.stream().sorted(Comparator.comparing(EnumDef::name)).forEach(enumDef -> {
            sb.append("\n#### ").append(enumDef.name()).append("\n\n");
            renderDocComments(sb, enumDef.docComments());
            sb.append("| Member | Ordinal |\n");
            sb.append("|--------|----------|\n");
            for (var member : enumDef.members()) {
                String ordinal = member.ordinalOrNull() != null
                        ? String.valueOf(member.ordinalOrNull())
                        : DASH;
                sb.append("| ").append(member.name())
                  .append(" | ").append(ordinal)
                  .append(" |\n");
            }
        });
    }

    private static void appendCollectionsSubsection(StringBuilder sb,
                                                     List<ListDef> lists,
                                                     List<MapDef> maps) {
        if (lists.isEmpty() && maps.isEmpty()) return;
        sb.append("\n### Collections\n");
        lists.stream().sorted(Comparator.comparing(ListDef::name)).forEach(list -> {
            sb.append("\n#### ").append(list.name()).append("\n\n");
            renderDocComments(sb, list.docComments());
            sb.append("`List<").append(renderTypeRef(list.memberType())).append(">`\n\n");
        });
        maps.stream().sorted(Comparator.comparing(MapDef::name)).forEach(map -> {
            sb.append("\n#### ").append(map.name()).append("\n\n");
            renderDocComments(sb, map.docComments());
            sb.append("`Map<").append(renderTypeRef(map.keyType())).append(", ")
              .append(renderTypeRef(map.valueType())).append(">`\n\n");
        });
    }

    // ── Global Invariants ─────────────────────────────────────────────────────

    private static void appendGlobalInvariantsSection(StringBuilder sb, IrModel model) {
        if (model.invariants().isEmpty()) return;
        sb.append(DIVIDER).append("\n## Global Invariants\n\n");
        sb.append("Cross-entity constraints that must always hold true:\n\n");

        for (var inv : model.invariants()) {
            sb.append("### ").append(inv.name()).append("\n\n");

            // Doc comments
            for (var doc : inv.docComments()) {
                sb.append("> ").append(doc).append("\n");
            }
            if (!inv.docComments().isEmpty()) sb.append("\n");

            // Scope
            sb.append("**Scope:** ");
            sb.append(String.join(", ", inv.scope()));
            sb.append("\n\n");

            // Expression
            sb.append("**Expression:** `").append(inv.expression()).append("`\n\n");

            // Severity
            sb.append("**Severity:** ").append(inv.severity()).append("\n\n");

            // Message (if present)
            if (inv.message().isPresent()) {
                sb.append("**Message:** ").append(inv.message().get()).append("\n\n");
            }
        }
    }

    // ── Relationships ─────────────────────────────────────────────────────────

    private static void appendRelationshipsSection(StringBuilder sb, IrModel model) {
        if (model.relationships().isEmpty()) return;
        sb.append(DIVIDER).append("\n## Relationships\n");
        model.relationships().stream().sorted(Comparator.comparing(RelationshipDef::name)).forEach(rel -> {
            sb.append("\n#### ").append(rel.name()).append("\n\n");
            renderDocComments(sb, rel.docComments());
            sb.append("**From:** ").append(symRefName(rel.fromEntityRef()))
              .append(" **→** ").append(symRefName(rel.toEntityRef()))
              .append(" (").append(rel.cardinality().chronosName()).append(")\n");
            sb.append("**Semantics:** ").append(rel.effectiveSemantics().chronosName()).append("\n");
            rel.inverseField().ifPresent(f ->
                    sb.append("**Inverse Field:** ").append(f).append("\n"));
        });
    }

    // ── Actors ────────────────────────────────────────────────────────────────

    private static void appendActorsSection(StringBuilder sb, IrModel model) {
        if (model.actors().isEmpty()) return;
        sb.append(DIVIDER).append("\n## Actors\n");
        model.actors().stream().sorted(Comparator.comparing(ActorDef::name)).forEach(actor -> {
            sb.append("\n#### ").append(actor.name()).append("\n\n");
            renderDocComments(sb, actor.docComments());
            sb.append("**Description:** ").append(actor.description().orElse(DASH)).append("\n");
        });
    }

    // ── Policies ──────────────────────────────────────────────────────────────

    private static void appendPoliciesSection(StringBuilder sb, IrModel model) {
        if (model.policies().isEmpty()) return;
        sb.append(DIVIDER).append("\n## Policies\n");
        model.policies().stream().sorted(Comparator.comparing(PolicyDef::name)).forEach(policy -> {
            sb.append("\n#### ").append(policy.name()).append("\n\n");
            renderDocComments(sb, policy.docComments());
            sb.append("**Description:** ").append(policy.description()).append("\n");
            policy.complianceFramework().ifPresent(c ->
                    sb.append("**Compliance:** ").append(c).append("\n"));
        });
    }

    // ── Prohibitions ──────────────────────────────────────────────────────────

    private static void appendProhibitionsSection(StringBuilder sb, IrModel model) {
        if (model.denies().isEmpty()) return;
        sb.append(DIVIDER).append("\n## Prohibitions\n");
        model.denies().stream().sorted(Comparator.comparing(DenyDef::name)).forEach(deny -> {
            sb.append("\n#### ").append(deny.name()).append("\n\n");
            renderDocComments(sb, deny.docComments());
            sb.append("**Description:** ").append(deny.description()).append("\n");
            sb.append("**Scope:** ").append(String.join(", ", deny.scope())).append("\n");
            sb.append("**Severity:** ").append(deny.severity()).append("\n");
            deny.traits().stream()
                    .filter(t -> "compliance".equals(t.name()))
                    .flatMap(t -> t.firstPositionalValue().stream())
                    .filter(v -> v instanceof TraitValue.StringValue)
                    .map(v -> ((TraitValue.StringValue) v).value())
                    .findFirst()
                    .ifPresent(c -> sb.append("**Compliance:** ").append(c).append("\n"));
        });
    }

    // ── Error Catalog ─────────────────────────────────────────────────────────

    private static void appendErrorCatalogSection(StringBuilder sb, IrModel model) {
        if (model.errors().isEmpty()) return;
        sb.append(DIVIDER).append("\n## Error Catalog\n");
        model.errors().stream().sorted(Comparator.comparing(ErrorDef::name)).forEach(error -> {
            sb.append("\n#### ").append(error.name()).append("\n\n");
            renderDocComments(sb, error.docComments());
            sb.append("**Code:** ").append(error.code())
              .append(" | **Severity:** ").append(error.severity())
              .append(" | **Recoverable:** ").append(error.recoverable() ? "Yes" : "No")
              .append("\n");
            sb.append("**Message:** ").append(error.message()).append("\n");
            if (!error.payload().isEmpty()) {
                String payload = error.payload().stream()
                        .map(f -> f.name() + ": " + renderTypeRef(f.type()))
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
                sb.append("**Payload:** ").append(payload).append("\n");
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void renderDocComments(StringBuilder sb, List<String> docs) {
        if (docs == null || docs.isEmpty()) return;
        for (var doc : docs) {
            sb.append("> ").append(doc).append("\n");
        }
        sb.append("\n");
    }

    private static String renderOutcomeExpr(OutcomeExpr expr) {
        return switch (expr) {
            case OutcomeExpr.TransitionTo t -> "TransitionTo(" + t.stateId() + ")";
            case OutcomeExpr.ReturnToStep r -> "ReturnToStep(" + r.stepId() + ")";
        };
    }

    private static String renderTypeRef(TypeRef type) {
        return switch (type) {
            case TypeRef.PrimitiveType p -> p.kind().chronosName();
            case TypeRef.ListType l      -> "List<" + renderTypeRef(l.elementType()) + ">";
            case TypeRef.MapType m       -> "Map<" + renderTypeRef(m.keyType()) + ", "
                                                   + renderTypeRef(m.valueType()) + ">";
            case TypeRef.NamedTypeRef n  -> n.qualifiedId();
        };
    }

    /** Extracts the simple name from a resolved or unresolved {@link SymbolRef}. */
    private static String symRefName(SymbolRef ref) {
        return ref.isResolved() ? ref.id().name() : ref.name().name();
    }

    /** Generates a GitHub-flavoured Markdown heading anchor from a PascalCase name. */
    private static String anchor(String text) {
        return text.toLowerCase().replaceAll("[^a-z0-9\\-]", "");
    }
}
