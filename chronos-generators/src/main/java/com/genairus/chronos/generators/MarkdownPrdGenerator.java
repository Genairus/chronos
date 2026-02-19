package com.genairus.chronos.generators;

import com.genairus.chronos.model.*;

import java.util.List;
import java.util.StringJoiner;

/**
 * Generates a Markdown Product Requirements Document (PRD) from a
 * {@link ChronosModel}.
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
 *   <li>Actors — name and {@code @description}</li>
 *   <li>Policies — name, description, and compliance framework</li>
 * </ol>
 */
public final class MarkdownPrdGenerator implements ChronosGenerator {

    private static final String DASH    = "—";
    private static final String DIVIDER = "\n---\n";

    @Override
    public GeneratorOutput generate(ChronosModel model) {
        var sb = new StringBuilder();

        appendTitle(sb, model);
        appendToc(sb, model);
        appendJourneysSection(sb, model);
        appendDataModelSection(sb, model);
        appendActorsSection(sb, model);
        appendPoliciesSection(sb, model);

        String filename = model.namespace().replace('.', '-') + "-prd.md";
        return GeneratorOutput.of(filename, sb.toString());
    }

    // ── Title ─────────────────────────────────────────────────────────────────

    private static void appendTitle(StringBuilder sb, ChronosModel model) {
        sb.append("# ").append(model.namespace()).append(" — Product Requirements\n");
    }

    // ── Table of Contents ─────────────────────────────────────────────────────

    private static void appendToc(StringBuilder sb, ChronosModel model) {
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

        if (!model.actors().isEmpty())
            sb.append("- [Actors](#actors)\n");
        if (!model.policies().isEmpty())
            sb.append("- [Policies](#policies)\n");
    }

    // ── Journeys ──────────────────────────────────────────────────────────────

    private static void appendJourneysSection(StringBuilder sb, ChronosModel model) {
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
            o.successOutcome().ifPresent(s -> sb.append("- \u2705 Success: ").append(s).append("\n"));
            o.failureOutcome().ifPresent(f -> sb.append("- \u274c Failure: ").append(f).append("\n"));
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
        sb.append("- **Trigger:** ").append(variant.trigger()).append("\n");
        if (!variant.steps().isEmpty()) {
            sb.append("\n");
            appendStepTable(sb, variant.steps());
        }
        variant.outcome().ifPresent(o ->
                sb.append("- **Outcome:** ").append(renderOutcomeExpr(o)).append("\n"));
    }

    // ── Data Model ────────────────────────────────────────────────────────────

    private static void appendDataModelSection(StringBuilder sb, ChronosModel model) {
        boolean hasDataModel = !model.entities().isEmpty()
                || !model.shapeStructs().isEmpty()
                || !model.enums().isEmpty()
                || !model.lists().isEmpty()
                || !model.maps().isEmpty();
        if (!hasDataModel) return;

        sb.append(DIVIDER).append("\n## Data Model\n");

        appendFieldedShapes(sb, model.entities(), "Entities");
        appendFieldedShapes(sb, model.shapeStructs(), "Value Objects");
        appendEnumsSubsection(sb, model.enums());
        appendCollectionsSubsection(sb, model.lists(), model.maps());
    }

    private static void appendFieldedShapes(StringBuilder sb,
                                             List<? extends ShapeDefinition> shapes,
                                             String heading) {
        if (shapes.isEmpty()) return;
        sb.append("\n### ").append(heading).append("\n");
        for (var shape : shapes) {
            List<String>   docs;
            List<FieldDef> fields;
            if (shape instanceof EntityDef e) {
                docs   = e.docComments();
                fields = e.fields();
            } else if (shape instanceof ShapeStructDef s) {
                docs   = s.docComments();
                fields = s.fields();
            } else {
                continue;
            }
            sb.append("\n#### ").append(shape.name()).append("\n\n");
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
        }
    }

    private static void appendEnumsSubsection(StringBuilder sb, List<EnumDef> enums) {
        if (enums.isEmpty()) return;
        sb.append("\n### Enumerations\n");
        for (var enumDef : enums) {
            sb.append("\n#### ").append(enumDef.name()).append("\n\n");
            sb.append("| Member | Ordinal |\n");
            sb.append("|--------|----------|\n");
            for (var member : enumDef.members()) {
                String ordinal = member.ordinal().isPresent()
                        ? String.valueOf(member.ordinal().getAsInt())
                        : DASH;
                sb.append("| ").append(member.name())
                  .append(" | ").append(ordinal)
                  .append(" |\n");
            }
        }
    }

    private static void appendCollectionsSubsection(StringBuilder sb,
                                                     List<ListDef> lists,
                                                     List<MapDef> maps) {
        if (lists.isEmpty() && maps.isEmpty()) return;
        sb.append("\n### Collections\n\n");
        for (var list : lists) {
            sb.append("- **").append(list.name()).append("** — `List<")
              .append(renderTypeRef(list.memberType())).append(">`\n");
        }
        for (var map : maps) {
            sb.append("- **").append(map.name()).append("** — `Map<")
              .append(renderTypeRef(map.keyType())).append(", ")
              .append(renderTypeRef(map.valueType())).append(">`\n");
        }
    }

    // ── Actors ────────────────────────────────────────────────────────────────

    private static void appendActorsSection(StringBuilder sb, ChronosModel model) {
        if (model.actors().isEmpty()) return;
        sb.append(DIVIDER).append("\n## Actors\n\n");
        sb.append("| Actor | Description |\n");
        sb.append("|-------|-------------|\n");
        for (var actor : model.actors()) {
            sb.append("| ").append(actor.name())
              .append(" | ").append(actor.description().orElse(DASH))
              .append(" |\n");
        }
    }

    // ── Policies ──────────────────────────────────────────────────────────────

    private static void appendPoliciesSection(StringBuilder sb, ChronosModel model) {
        if (model.policies().isEmpty()) return;
        sb.append(DIVIDER).append("\n## Policies\n\n");
        sb.append("| Policy | Description | Compliance |\n");
        sb.append("|--------|-------------|------------|\n");
        for (var policy : model.policies()) {
            sb.append("| ").append(policy.name())
              .append(" | ").append(policy.description())
              .append(" | ").append(policy.complianceFramework().orElse(DASH))
              .append(" |\n");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String renderOutcomeExpr(OutcomeExpr expr) {
        return switch (expr) {
            case OutcomeExpr.TransitionTo t -> "TransitionTo(" + t.target() + ")";
            case OutcomeExpr.ReturnToStep r -> "ReturnToStep(" + r.target() + ")";
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

    /** Generates a GitHub-flavoured Markdown heading anchor from a PascalCase name. */
    private static String anchor(String text) {
        return text.toLowerCase().replaceAll("[^a-z0-9\\-]", "");
    }
}
