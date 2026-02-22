package com.genairus.chronos.generators;

import com.genairus.chronos.core.refs.SymbolRef;
import com.genairus.chronos.ir.model.IrInheritanceResolver;
import com.genairus.chronos.ir.model.IrModel;
import com.genairus.chronos.ir.types.ActorDef;
import com.genairus.chronos.ir.types.DataField;
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
import com.genairus.chronos.ir.types.RoleDef;
import com.genairus.chronos.ir.types.TraitApplication;
import com.genairus.chronos.ir.types.ShapeStructDef;
import com.genairus.chronos.ir.types.StateMachineDef;
import com.genairus.chronos.ir.types.Step;
import com.genairus.chronos.ir.types.TraitValue;
import com.genairus.chronos.ir.types.TypeRef;
import com.genairus.chronos.ir.types.Variant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Generates a Markdown Product Requirements Document (PRD) from a {@link IrModel}.
 *
 * <p>The output is a single {@code <namespace>-prd.md} file with the following
 * structure (sections with no content are omitted from both the TOC and body):
 * <ol>
 *   <li>Title — {@code namespace — Product Requirements}</li>
 *   <li>Executive Summary — counts, journey KPIs, compliance frameworks</li>
 *   <li>Table of Contents — anchor links to each populated section</li>
 *   <li>Journeys — metadata block, preconditions, happy-path step table (with SLO),
 *       variant subsections, and outcome statements</li>
 *   <li>Data Model — ER diagram, entity/value-object field tables, enum member tables,
 *       and collection type summaries</li>
 *   <li>Global Invariants</li>
 *   <li>Relationships</li>
 *   <li>State Machines — transition table and Mermaid state diagram</li>
 *   <li>Actors — name, {@code @description}, and {@code extends} if applicable</li>
 *   <li>Policies — name, description, and compliance framework</li>
 *   <li>Prohibitions</li>
 *   <li>Error Catalog — error codes, severity, recoverability, and payload schemas</li>
 *   <li>Telemetry Catalog — all telemetry events by journey, step, and path</li>
 * </ol>
 */
public final class MarkdownPrdGenerator implements ChronosGenerator {

    private static final String DASH    = "—";
    private static final String DIVIDER = "\n---\n";

    // ── Cross-reference context ────────────────────────────────────────────────

    /**
     * Carries per-document cross-reference state through rendering helpers.
     *
     * @param fqJourneyId        fully-qualified journey name used to build step anchors
     * @param symbolAnchors      shape name → heading anchor (simple and FQ both stored)
     * @param enumMemberAnchors  enum member name → enum heading anchor
     */
    private record RenderCtx(
            String fqJourneyId,
            Map<String, String> symbolAnchors,
            Map<String, String> enumMemberAnchors) {

        static final RenderCtx NONE = new RenderCtx("", Map.of(), Map.of());

        /** Returns the Markdown anchor for a step in the current journey. */
        String stepAnchor(String stepName) {
            if (fqJourneyId.isEmpty()) return "";
            return anchor(fqJourneyId + "-" + stepName);
        }

        /** Returns {@code [name](#anchor)} if the name is in the symbol map, else plain {@code name}. */
        String linkName(String name) {
            String a = symbolAnchors.get(name);
            return a != null ? "[" + name + "](#" + a + ")" : name;
        }

        /** Returns {@code [memberName](#enumAnchor)} if the enum member is known, else plain text. */
        String linkEnumMember(String memberName) {
            String a = enumMemberAnchors.get(memberName);
            return a != null ? "[" + memberName + "](#" + a + ")" : memberName;
        }
    }

    // ── Telemetry row ──────────────────────────────────────────────────────────

    private record TelemetryEntry(String event, String journey, String step, String path) {}

    // ── Single-file entry point ────────────────────────────────────────────────

    @Override
    public GeneratorOutput generate(IrModel model) {
        var sb = new StringBuilder();
        var inheritanceResolver = new IrInheritanceResolver(model);
        var symbolAnchors      = buildSymbolAnchors(model);
        var enumMemberAnchors  = buildEnumMemberAnchors(model);

        appendTitle(sb, model);
        appendExecutiveSummary(sb, model);
        appendToc(sb, model);
        appendJourneysSection(sb, model, symbolAnchors, enumMemberAnchors);
        appendDataModelSection(sb, model, inheritanceResolver, symbolAnchors);
        appendGlobalInvariantsSection(sb, model);
        appendRelationshipsSection(sb, model, symbolAnchors);
        appendStateMachinesSection(sb, model);
        appendActorsSection(sb, model, symbolAnchors);
        appendAuthorizationSection(sb, model);
        appendPoliciesSection(sb, model);
        appendProhibitionsSection(sb, model);
        appendErrorCatalogSection(sb, model);
        appendTelemetryCatalogSection(sb, model.journeys(), j -> j.name());

        String filename = model.namespace().replace('.', '-') + "-prd.md";
        return GeneratorOutput.of(filename, sb.toString());
    }

    // ── Title ─────────────────────────────────────────────────────────────────

    private static void appendTitle(StringBuilder sb, IrModel model) {
        sb.append("# ").append(model.namespace()).append(" — Product Requirements\n");
    }

    // ── Executive Summary ─────────────────────────────────────────────────────

    private static void appendExecutiveSummary(StringBuilder sb, IrModel model) {
        int nJourneys  = model.journeys().size();
        int nEntities  = model.entities().size();
        int nShapes    = model.shapeStructs().size();
        int nEnums     = model.enums().size();
        int nActors    = model.actors().size();
        int nPolicies  = model.policies().size();
        int nErrors    = model.errors().size();
        int nSms       = model.stateMachines().size();

        sb.append("\n## Executive Summary\n\n");
        sb.append("This PRD covers ")
          .append(nJourneys).append(nJourneys == 1 ? " journey" : " journeys").append(", ")
          .append(nEntities).append(nEntities == 1 ? " entity" : " entities").append(", ")
          .append(nShapes).append(nShapes == 1 ? " value object" : " value objects").append(", ")
          .append(nEnums).append(nEnums == 1 ? " enumeration" : " enumerations").append(", ")
          .append(nActors).append(nActors == 1 ? " actor" : " actors").append(", ")
          .append(nPolicies).append(nPolicies == 1 ? " policy" : " policies").append(", ")
          .append(nErrors).append(nErrors == 1 ? " error type" : " error types").append(", and ")
          .append(nSms).append(nSms == 1 ? " state machine" : " state machines")
          .append(" across 1 namespace.\n\n");

        appendExecJourneyList(sb, model.journeys(), j -> j.name());
        appendExecComplianceList(sb, model.journeys(), model.policies(), model.denies());
    }

    private static void appendExecJourneyList(
            StringBuilder sb,
            List<JourneyDef> journeys,
            Function<JourneyDef, String> labelFn) {
        if (journeys.isEmpty()) return;
        sb.append("**Journeys:**\n\n");
        for (var j : journeys) {
            sb.append("- **").append(labelFn.apply(j)).append("**");
            extractKpi(j).ifPresent(kpi -> sb.append(" — ").append(kpi));
            sb.append("\n");
        }
        sb.append("\n");
    }

    private static void appendExecComplianceList(
            StringBuilder sb,
            List<JourneyDef> journeys,
            List<PolicyDef> policies,
            List<DenyDef> denies) {
        Set<String> frameworks = new TreeSet<>();
        journeys.forEach(j -> j.traits().stream()
                .filter(t -> "compliance".equals(t.name()))
                .flatMap(t -> t.firstPositionalValue().stream())
                .filter(v -> v instanceof TraitValue.StringValue)
                .map(v -> ((TraitValue.StringValue) v).value())
                .forEach(frameworks::add));
        policies.forEach(p -> p.complianceFramework().ifPresent(frameworks::add));
        denies.forEach(d -> d.traits().stream()
                .filter(t -> "compliance".equals(t.name()))
                .flatMap(t -> t.firstPositionalValue().stream())
                .filter(v -> v instanceof TraitValue.StringValue)
                .map(v -> ((TraitValue.StringValue) v).value())
                .forEach(frameworks::add));

        if (!frameworks.isEmpty()) {
            sb.append("**Compliance Frameworks:**\n\n");
            frameworks.forEach(f -> sb.append("- ").append(f).append("\n"));
            sb.append("\n");
        }
    }

    private static Optional<String> extractKpi(JourneyDef journey) {
        return journey.traits().stream()
                .filter(t -> "kpi".equals(t.name()))
                .findFirst()
                .map(kpi -> {
                    String metric = kpi.namedValue("metric")
                            .filter(v -> v instanceof TraitValue.StringValue)
                            .map(v -> ((TraitValue.StringValue) v).value())
                            .orElse(null);
                    String target = kpi.namedValue("target")
                            .filter(v -> v instanceof TraitValue.StringValue)
                            .map(v -> ((TraitValue.StringValue) v).value())
                            .orElse(null);
                    if (metric == null) return null;
                    return metric + (target != null ? " → " + target : "");
                });
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
        if (!model.stateMachines().isEmpty())
            sb.append("- [State Machines](#state-machines)\n");
        if (!model.actors().isEmpty())
            sb.append("- [Actors](#actors)\n");
        if (!model.roles().isEmpty())
            sb.append("- [Authorization](#authorization)\n");
        if (!model.policies().isEmpty())
            sb.append("- [Policies](#policies)\n");
        if (!model.denies().isEmpty())
            sb.append("- [Prohibitions](#prohibitions)\n");
        if (!model.errors().isEmpty())
            sb.append("- [Error Catalog](#error-catalog)\n");
        if (hasTelemetry(model.journeys()))
            sb.append("- [Telemetry Catalog](#telemetry-catalog)\n");
    }

    // ── Journeys ──────────────────────────────────────────────────────────────

    private static void appendJourneysSection(StringBuilder sb, IrModel model,
                                               Map<String, String> symbolAnchors,
                                               Map<String, String> enumMemberAnchors) {
        if (model.journeys().isEmpty()) return;
        sb.append(DIVIDER).append("\n## Journeys\n");
        for (var journey : model.journeys()) {
            appendJourney(sb, journey, symbolAnchors, enumMemberAnchors);
        }
    }

    private static void appendJourney(StringBuilder sb, JourneyDef journey,
                                       Map<String, String> symbolAnchors,
                                       Map<String, String> enumMemberAnchors) {
        sb.append("\n### ").append(journey.name()).append("\n\n");
        var ctx = new RenderCtx(journey.name(), symbolAnchors, enumMemberAnchors);
        appendJourneyContent(sb, journey, ctx);
    }

    static void appendJourneyContent(StringBuilder sb, JourneyDef journey, RenderCtx ctx) {
        // Doc comments as blockquote
        for (var doc : journey.docComments()) {
            sb.append("> ").append(doc).append("\n");
        }
        if (!journey.docComments().isEmpty()) sb.append(">\n");

        // Metadata: actor | owner | KPI | compliance
        var meta = new StringJoiner(" | ");
        journey.actorName().ifPresent(a -> meta.add("**Actor:** " + a));
        journey.traits().stream()
                .filter(t -> "owner".equals(t.name()))
                .findFirst()
                .flatMap(t -> t.firstPositionalValue())
                .filter(v -> v instanceof TraitValue.StringValue)
                .map(v -> ((TraitValue.StringValue) v).value())
                .ifPresent(owner -> meta.add("**Owner:** " + owner));
        journey.traits().stream()
                .filter(t -> "kpi".equals(t.name()))
                .findFirst()
                .ifPresent(kpi -> extractKpi(journey)
                        .ifPresent(k -> meta.add("**KPI:** " + k)));
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
            appendStepTable(sb, journey.steps(), ctx);
            sb.append("\n");
        }

        // Variants
        if (!journey.variants().isEmpty()) {
            sb.append("**Variants**\n");
            for (var entry : journey.variants().entrySet()) {
                appendVariant(sb, entry.getKey(), entry.getValue(), ctx);
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

    private static void appendStepTable(StringBuilder sb, List<Step> steps, RenderCtx ctx) {
        sb.append("| Step | Action | Expectation | Outcome | SLO | Telemetry | Risk | Input | Output |\n");
        sb.append("|------|--------|-------------|---------|-----|-----------|------|-------|--------|\n");
        for (var step : steps) {
            String telemetry = step.telemetryEvents().isEmpty()
                    ? DASH
                    : String.join(", ", step.telemetryEvents());
            String slo = stepSlo(step).orElse(DASH);
            // Embed an HTML anchor in the step cell for ReturnToStep cross-references
            String stepId = ctx.fqJourneyId().isEmpty()
                    ? step.name()
                    : "<a id=\"" + ctx.stepAnchor(step.name()) + "\"></a>" + step.name();
            String input  = renderDataFields(step.inputFields());
            String output = renderDataFields(step.outputFields());
            sb.append("| ").append(stepId)
              .append(" | ").append(step.action().orElse(DASH))
              .append(" | ").append(step.expectation().orElse(DASH))
              .append(" | ").append(step.outcome().map(o -> renderOutcomeExpr(o, ctx)).orElse(DASH))
              .append(" | ").append(slo)
              .append(" | ").append(telemetry)
              .append(" | ").append(step.risk().orElse(DASH))
              .append(" | ").append(input)
              .append(" | ").append(output)
              .append(" |\n");
        }
    }

    /** Renders a list of {@link DataField}s as {@code name: Type, …} or {@code —} if empty. */
    private static String renderDataFields(List<DataField> fields) {
        if (fields.isEmpty()) return DASH;
        var sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) sb.append(", ");
            DataField f = fields.get(i);
            sb.append(f.name()).append(": ").append(renderTypeRef(f.type()));
        }
        return sb.toString();
    }

    private static void appendVariant(StringBuilder sb, String name, Variant variant, RenderCtx ctx) {
        sb.append("\n#### ").append(name).append("\n\n");
        sb.append("**Trigger:** ").append(ctx.linkName(variant.triggerName())).append("\n");
        if (!variant.steps().isEmpty()) {
            sb.append("\n");
            appendStepTable(sb, variant.steps(), ctx);
        }
        variant.outcome().ifPresent(o ->
                sb.append("\n**Outcome:** ").append(renderOutcomeExpr(o, ctx)).append("\n"));
    }

    // ── Data Model ────────────────────────────────────────────────────────────

    private static void appendDataModelSection(StringBuilder sb, IrModel model,
                                                IrInheritanceResolver resolver,
                                                Map<String, String> symbolAnchors) {
        boolean hasDataModel = !model.entities().isEmpty()
                || !model.shapeStructs().isEmpty()
                || !model.enums().isEmpty()
                || !model.lists().isEmpty()
                || !model.maps().isEmpty();
        if (!hasDataModel) return;

        sb.append(DIVIDER).append("\n## Data Model\n");

        // ER diagram at the top of the data model section
        if (!model.relationships().isEmpty()) {
            appendErDiagram(sb, model.relationships());
        }

        appendFieldedShapes(sb, model.entities(), "Entities", resolver, symbolAnchors);
        appendFieldedShapes(sb, model.shapeStructs(), "Value Objects", resolver, symbolAnchors);
        appendEnumsSubsection(sb, model.enums());
        appendCollectionsSubsection(sb, model.lists(), model.maps());
    }

    private static void appendFieldedShapes(StringBuilder sb,
                                             List<? extends IrShape> shapes,
                                             String heading,
                                             IrInheritanceResolver resolver,
                                             Map<String, String> symbolAnchors) {
        if (shapes.isEmpty()) return;
        sb.append("\n### ").append(heading).append("\n");
        for (var shape : shapes) {
            List<String>   docs;
            List<FieldDef> fields;
            if (shape instanceof EntityDef e) {
                docs   = e.docComments();
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
                String parentName = IrInheritanceResolver.parentName(e).get();
                String parentAnchor = symbolAnchors.getOrDefault(parentName, anchor(parentName));
                sb.append("*Extends: [").append(parentName).append("](#").append(parentAnchor).append(")*\n\n");
            }

            for (var doc : docs) {
                sb.append("> ").append(doc).append("\n");
            }
            if (!docs.isEmpty()) sb.append("\n");

            if (!fields.isEmpty()) {
                appendFieldTable(sb, fields);
            }

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

    // ── ER Diagram ────────────────────────────────────────────────────────────

    private static void appendErDiagram(StringBuilder sb, List<RelationshipDef> relationships) {
        sb.append("\n```mermaid\n");
        sb.append("erDiagram\n");
        for (var rel : relationships) {
            String connector = switch (rel.cardinality()) {
                case ONE_TO_ONE   -> "||--||";
                case ONE_TO_MANY  -> "||--o{";
                case MANY_TO_MANY -> "}o--o{";
            };
            sb.append("    ").append(symRefName(rel.fromEntityRef()))
              .append(" ").append(connector).append(" ")
              .append(symRefName(rel.toEntityRef()))
              .append(" : ").append(rel.name()).append("\n");
        }
        sb.append("```\n");
    }

    // ── Global Invariants ─────────────────────────────────────────────────────

    private static void appendGlobalInvariantsSection(StringBuilder sb, IrModel model) {
        if (model.invariants().isEmpty()) return;
        sb.append(DIVIDER).append("\n## Global Invariants\n\n");
        sb.append("Cross-entity constraints that must always hold true:\n\n");

        for (var inv : model.invariants()) {
            sb.append("### ").append(inv.name()).append("\n\n");
            for (var doc : inv.docComments()) {
                sb.append("> ").append(doc).append("\n");
            }
            if (!inv.docComments().isEmpty()) sb.append("\n");
            sb.append("**Scope:** ").append(String.join(", ", inv.scope())).append("\n\n");
            sb.append("**Expression:** `").append(inv.expression()).append("`\n\n");
            sb.append("**Severity:** ").append(inv.severity()).append("\n\n");
            if (inv.message().isPresent()) {
                sb.append("**Message:** ").append(inv.message().get()).append("\n\n");
            }
        }
    }

    // ── Relationships ─────────────────────────────────────────────────────────

    private static void appendRelationshipsSection(StringBuilder sb, IrModel model,
                                                    Map<String, String> symbolAnchors) {
        if (model.relationships().isEmpty()) return;
        sb.append(DIVIDER).append("\n## Relationships\n");
        model.relationships().stream().sorted(Comparator.comparing(RelationshipDef::name)).forEach(rel -> {
            sb.append("\n#### ").append(rel.name()).append("\n\n");
            renderDocComments(sb, rel.docComments());
            rel.description().ifPresent(d -> sb.append("**Description:** ").append(d).append("\n"));
            String fromName = symRefName(rel.fromEntityRef());
            String toName   = symRefName(rel.toEntityRef());
            sb.append("**From:** ").append(linkName(fromName, symbolAnchors))
              .append(" **→** ").append(linkName(toName, symbolAnchors))
              .append(" (").append(rel.cardinality().chronosName()).append(")\n");
            sb.append("**Semantics:** ").append(rel.effectiveSemantics().chronosName()).append("\n");
            rel.inverseField().ifPresent(f ->
                    sb.append("**Inverse Field:** ").append(f).append("\n"));
        });
    }

    // ── State Machines ────────────────────────────────────────────────────────

    private static void appendStateMachinesSection(StringBuilder sb, IrModel model) {
        if (model.stateMachines().isEmpty()) return;
        sb.append(DIVIDER).append("\n## State Machines\n");
        model.stateMachines().stream()
                .sorted(Comparator.comparing(StateMachineDef::name))
                .forEach(sm -> appendStateMachine(sb, sm));
    }

    private static void appendStateMachine(StringBuilder sb, StateMachineDef sm) {
        sb.append("\n### ").append(sm.name()).append("\n\n");
        renderDocComments(sb, sm.docComments());

        // Metadata line
        StringBuilder meta = new StringBuilder();
        meta.append("**Entity:** ").append(sm.entityName())
            .append(" | **Field:** ").append(sm.fieldName())
            .append(" | **Initial:** ").append(sm.initialState());
        if (!sm.terminalStates().isEmpty()) {
            meta.append(" | **Terminal:** ").append(String.join(", ", sm.terminalStates()));
        }
        sb.append(meta).append("\n\n");

        // Transition table
        sb.append("| From | To | Guard | Action |\n");
        sb.append("|------|----|-------|--------|\n");
        for (var t : sm.transitions()) {
            sb.append("| ").append(t.fromState())
              .append(" | ").append(t.toState())
              .append(" | ").append(t.guard().map(g -> "`" + g + "`").orElse(DASH))
              .append(" | ").append(t.action().orElse(DASH))
              .append(" |\n");
        }
        sb.append("\n");

        // Mermaid state diagram
        sb.append("```mermaid\n");
        sb.append("stateDiagram-v2\n");
        sb.append("    [*] --> ").append(sm.initialState()).append("\n");
        for (var t : sm.transitions()) {
            sb.append("    ").append(t.fromState()).append(" --> ").append(t.toState()).append("\n");
        }
        for (var terminal : sm.terminalStates()) {
            sb.append("    ").append(terminal).append(" --> [*]\n");
        }
        sb.append("```\n");
    }

    // ── Actors ────────────────────────────────────────────────────────────────

    private static void appendActorsSection(StringBuilder sb, IrModel model,
                                             Map<String, String> symbolAnchors) {
        if (model.actors().isEmpty()) return;
        sb.append(DIVIDER).append("\n## Actors\n");
        model.actors().stream().sorted(Comparator.comparing(ActorDef::name)).forEach(actor -> {
            sb.append("\n#### ").append(actor.name()).append("\n\n");
            renderDocComments(sb, actor.docComments());
            sb.append("**Description:** ").append(actor.description().orElse(DASH)).append("\n");
            actor.parentRef().ifPresent(ref -> {
                String parentName   = symRefName(ref);
                String parentAnchor = symbolAnchors.getOrDefault(parentName, anchor(parentName));
                sb.append("**Extends:** [").append(parentName).append("](#")
                  .append(parentAnchor).append(")\n");
            });
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

    // ── Telemetry Catalog ─────────────────────────────────────────────────────

    private static <T extends JourneyDef> boolean hasTelemetry(List<T> journeys) {
        return journeys.stream().anyMatch(j ->
                j.steps().stream().anyMatch(s -> !s.telemetryEvents().isEmpty())
                || j.variants().values().stream().anyMatch(v ->
                        v.steps().stream().anyMatch(s -> !s.telemetryEvents().isEmpty())));
    }

    private static void appendTelemetryCatalogSection(
            StringBuilder sb,
            List<JourneyDef> journeys,
            Function<JourneyDef, String> labelFn) {
        var rows = collectTelemetry(journeys, labelFn);
        if (rows.isEmpty()) return;
        sb.append(DIVIDER).append("\n## Telemetry Catalog\n\n");
        sb.append("| Event | Journey | Step | Path |\n");
        sb.append("|-------|---------|------|------|\n");
        for (var row : rows) {
            sb.append("| ").append(row.event())
              .append(" | ").append(row.journey())
              .append(" | ").append(row.step())
              .append(" | ").append(row.path())
              .append(" |\n");
        }
    }

    private static List<TelemetryEntry> collectTelemetry(
            List<JourneyDef> journeys,
            Function<JourneyDef, String> labelFn) {
        var rows = new ArrayList<TelemetryEntry>();
        for (var j : journeys) {
            String journeyLabel = labelFn.apply(j);
            for (var step : j.steps()) {
                for (var event : step.telemetryEvents()) {
                    rows.add(new TelemetryEntry(event, journeyLabel, step.name(), "Happy"));
                }
            }
            for (var varEntry : j.variants().entrySet()) {
                String varLabel = "Variant: " + varEntry.getKey();
                for (var step : varEntry.getValue().steps()) {
                    for (var event : step.telemetryEvents()) {
                        rows.add(new TelemetryEntry(event, journeyLabel, step.name(), varLabel));
                    }
                }
            }
        }
        return rows.stream()
                .distinct()
                .sorted(Comparator.comparing(TelemetryEntry::event)
                        .thenComparing(TelemetryEntry::journey))
                .toList();
    }

    // ── Combined multi-file PRD ───────────────────────────────────────────────

    /**
     * Generates a single combined PRD covering all models in a multi-file compilation unit.
     *
     * <p>Shapes from all models are merged into per-type lists and sorted by their
     * fully-qualified name ({@code namespace.SimpleName}) so output is deterministic
     * regardless of source ordering.  Each shape heading includes the namespace prefix
     * (e.g. {@code #### shop.domain.Order}) to avoid collisions when multiple namespaces
     * define the same simple name.
     *
     * @param models  the compiled {@link IrModel}s — order does not matter
     * @param docName base name for the output file, or {@code null} for {@code "chronos-prd"}
     * @return a {@link GeneratorOutput} containing one file: {@code <docName>.md}
     */
    public GeneratorOutput generateCombined(List<IrModel> models, String docName) {
        var sb = new StringBuilder();

        var journeys      = nsSort(models, IrModel::journeys);
        var entities      = nsSort(models, IrModel::entities);
        var structs       = nsSort(models, IrModel::shapeStructs);
        var enums         = nsSort(models, IrModel::enums);
        var lists         = nsSort(models, IrModel::lists);
        var maps          = nsSort(models, IrModel::maps);
        var invariants    = nsSort(models, IrModel::invariants);
        var relationships = nsSort(models, IrModel::relationships);
        var stateMachines = nsSort(models, IrModel::stateMachines);
        var actors        = nsSort(models, IrModel::actors);
        var roles         = nsSort(models, IrModel::roles);
        var policies      = nsSort(models, IrModel::policies);
        var denies        = nsSort(models, IrModel::denies);
        var errors        = nsSort(models, IrModel::errors);

        // Symbol anchor maps for cross-references
        var symbolAnchors     = buildCombinedSymbolAnchors(models);
        var enumMemberAnchors = buildCombinedEnumMemberAnchors(models);

        // One IrInheritanceResolver per namespace for entity field resolution.
        var shapesByNs = new LinkedHashMap<String, List<IrShape>>();
        for (IrModel m : models) {
            shapesByNs.computeIfAbsent(m.namespace(), k -> new ArrayList<>()).addAll(m.shapes());
        }
        Map<String, IrInheritanceResolver> resolvers = shapesByNs.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new IrInheritanceResolver(new IrModel(e.getKey(), List.of(), e.getValue()))));

        // Title
        sb.append("# Chronos Product Requirements Document\n");

        // Namespaces section
        var namespaces = models.stream().map(IrModel::namespace).distinct().sorted().toList();
        sb.append("\n## Namespaces\n\n");
        namespaces.forEach(ns -> sb.append("- `").append(ns).append("`\n"));

        appendCombinedExecutiveSummary(sb, models, journeys, policies, denies);
        appendCombinedToc(sb, journeys, entities, structs, enums, lists, maps,
                invariants, relationships, stateMachines, actors, roles, policies, denies, errors);
        appendCombinedJourneysSection(sb, journeys, symbolAnchors, enumMemberAnchors);
        appendCombinedDataModelSection(sb, entities, structs, enums, lists, maps, resolvers,
                relationships, symbolAnchors);
        appendCombinedGlobalInvariantsSection(sb, invariants);
        appendCombinedRelationshipsSection(sb, relationships, symbolAnchors);
        appendCombinedStateMachinesSection(sb, stateMachines);
        appendCombinedActorsSection(sb, actors, symbolAnchors);
        appendCombinedAuthorizationSection(sb, roles, journeys);
        appendCombinedPoliciesSection(sb, policies);
        appendCombinedProhibitionsSection(sb, denies);
        appendCombinedErrorCatalogSection(sb, errors);
        appendCombinedTelemetryCatalogSection(sb, journeys);

        String filename = (docName == null ? "chronos-prd" : docName) + ".md";
        return GeneratorOutput.of(filename, sb.toString());
    }

    /** Collects shapes of type T from all models, sorted by fully-qualified name (ns.name). */
    private static <T extends IrShape> List<Map.Entry<String, T>> nsSort(
            List<IrModel> models, Function<IrModel, List<T>> accessor) {
        return models.stream()
                .flatMap(m -> accessor.apply(m).stream()
                        .map(s -> Map.entry(m.namespace(), s)))
                .sorted(Comparator.comparing(e -> e.getKey() + "." + e.getValue().name()))
                .toList();
    }

    // ── Combined Executive Summary ────────────────────────────────────────────

    private static void appendCombinedExecutiveSummary(
            StringBuilder sb,
            List<IrModel> models,
            List<Map.Entry<String, JourneyDef>> journeys,
            List<Map.Entry<String, PolicyDef>> policies,
            List<Map.Entry<String, DenyDef>> denies) {

        int nJourneys  = journeys.size();
        int nEntities  = (int) models.stream().flatMap(m -> m.entities().stream()).count();
        int nShapes    = (int) models.stream().flatMap(m -> m.shapeStructs().stream()).count();
        int nEnums     = (int) models.stream().flatMap(m -> m.enums().stream()).count();
        int nActors    = (int) models.stream().flatMap(m -> m.actors().stream()).count();
        int nPolicies  = (int) models.stream().flatMap(m -> m.policies().stream()).count();
        int nErrors    = (int) models.stream().flatMap(m -> m.errors().stream()).count();
        int nSms       = (int) models.stream().flatMap(m -> m.stateMachines().stream()).count();
        int nNamespaces = (int) models.stream().map(IrModel::namespace).distinct().count();

        sb.append("\n## Executive Summary\n\n");
        sb.append("This PRD covers ")
          .append(nJourneys).append(nJourneys == 1 ? " journey" : " journeys").append(", ")
          .append(nEntities).append(nEntities == 1 ? " entity" : " entities").append(", ")
          .append(nShapes).append(nShapes == 1 ? " value object" : " value objects").append(", ")
          .append(nEnums).append(nEnums == 1 ? " enumeration" : " enumerations").append(", ")
          .append(nActors).append(nActors == 1 ? " actor" : " actors").append(", ")
          .append(nPolicies).append(nPolicies == 1 ? " policy" : " policies").append(", ")
          .append(nErrors).append(nErrors == 1 ? " error type" : " error types").append(", and ")
          .append(nSms).append(nSms == 1 ? " state machine" : " state machines")
          .append(" across ")
          .append(nNamespaces).append(nNamespaces == 1 ? " namespace" : " namespaces")
          .append(".\n\n");

        // Journey list with KPIs using FQ labels
        if (!journeys.isEmpty()) {
            sb.append("**Journeys:**\n\n");
            for (var e : journeys) {
                String fqLabel = e.getKey() + "." + e.getValue().name();
                sb.append("- **").append(fqLabel).append("**");
                extractKpi(e.getValue()).ifPresent(kpi -> sb.append(" — ").append(kpi));
                sb.append("\n");
            }
            sb.append("\n");
        }

        // Compliance frameworks
        Set<String> frameworks = new TreeSet<>();
        journeys.forEach(e -> e.getValue().traits().stream()
                .filter(t -> "compliance".equals(t.name()))
                .flatMap(t -> t.firstPositionalValue().stream())
                .filter(v -> v instanceof TraitValue.StringValue)
                .map(v -> ((TraitValue.StringValue) v).value())
                .forEach(frameworks::add));
        policies.forEach(e -> e.getValue().complianceFramework().ifPresent(frameworks::add));
        denies.forEach(e -> e.getValue().traits().stream()
                .filter(t -> "compliance".equals(t.name()))
                .flatMap(t -> t.firstPositionalValue().stream())
                .filter(v -> v instanceof TraitValue.StringValue)
                .map(v -> ((TraitValue.StringValue) v).value())
                .forEach(frameworks::add));
        if (!frameworks.isEmpty()) {
            sb.append("**Compliance Frameworks:**\n\n");
            frameworks.forEach(f -> sb.append("- ").append(f).append("\n"));
            sb.append("\n");
        }
    }

    // ── Combined TOC ──────────────────────────────────────────────────────────

    private static void appendCombinedToc(
            StringBuilder sb,
            List<Map.Entry<String, JourneyDef>> journeys,
            List<Map.Entry<String, EntityDef>> entities,
            List<Map.Entry<String, ShapeStructDef>> structs,
            List<Map.Entry<String, EnumDef>> enums,
            List<Map.Entry<String, ListDef>> lists,
            List<Map.Entry<String, MapDef>> maps,
            List<Map.Entry<String, InvariantDef>> invariants,
            List<Map.Entry<String, RelationshipDef>> relationships,
            List<Map.Entry<String, StateMachineDef>> stateMachines,
            List<Map.Entry<String, ActorDef>> actors,
            List<Map.Entry<String, RoleDef>> roles,
            List<Map.Entry<String, PolicyDef>> policies,
            List<Map.Entry<String, DenyDef>> denies,
            List<Map.Entry<String, ErrorDef>> errors) {

        sb.append("\n## Table of Contents\n\n");

        if (!journeys.isEmpty()) {
            sb.append("- [Journeys](#journeys)\n");
            for (var e : journeys) {
                String fq = e.getKey() + "." + e.getValue().name();
                sb.append("  - [").append(fq).append("](#").append(anchor(fq)).append(")\n");
            }
        }
        boolean hasDataModel = !entities.isEmpty() || !structs.isEmpty()
                || !enums.isEmpty() || !lists.isEmpty() || !maps.isEmpty();
        if (hasDataModel) {
            sb.append("- [Data Model](#data-model)\n");
            if (!entities.isEmpty()) sb.append("  - [Entities](#entities)\n");
            if (!structs.isEmpty())  sb.append("  - [Value Objects](#value-objects)\n");
            if (!enums.isEmpty())    sb.append("  - [Enumerations](#enumerations)\n");
            if (!lists.isEmpty() || !maps.isEmpty())
                sb.append("  - [Collections](#collections)\n");
        }
        if (!invariants.isEmpty())    sb.append("- [Global Invariants](#global-invariants)\n");
        if (!relationships.isEmpty()) sb.append("- [Relationships](#relationships)\n");
        if (!stateMachines.isEmpty()) sb.append("- [State Machines](#state-machines)\n");
        if (!actors.isEmpty())        sb.append("- [Actors](#actors)\n");
        if (!roles.isEmpty())         sb.append("- [Authorization](#authorization)\n");
        if (!policies.isEmpty())      sb.append("- [Policies](#policies)\n");
        if (!denies.isEmpty())        sb.append("- [Prohibitions](#prohibitions)\n");
        if (!errors.isEmpty())        sb.append("- [Error Catalog](#error-catalog)\n");

        boolean hasTelemetry = journeys.stream().anyMatch(e -> hasTelemetry(List.of(e.getValue())));
        if (hasTelemetry) sb.append("- [Telemetry Catalog](#telemetry-catalog)\n");
    }

    // ── Combined Journeys ─────────────────────────────────────────────────────

    private static void appendCombinedJourneysSection(
            StringBuilder sb,
            List<Map.Entry<String, JourneyDef>> journeys,
            Map<String, String> symbolAnchors,
            Map<String, String> enumMemberAnchors) {
        if (journeys.isEmpty()) return;
        sb.append(DIVIDER).append("\n## Journeys\n");
        for (var e : journeys) {
            String fqId = e.getKey() + "." + e.getValue().name();
            sb.append("\n### ").append(fqId).append("\n\n");
            var ctx = new RenderCtx(fqId, symbolAnchors, enumMemberAnchors);
            appendJourneyContent(sb, e.getValue(), ctx);
        }
    }

    // ── Combined Data Model ───────────────────────────────────────────────────

    private static void appendCombinedDataModelSection(
            StringBuilder sb,
            List<Map.Entry<String, EntityDef>> entities,
            List<Map.Entry<String, ShapeStructDef>> structs,
            List<Map.Entry<String, EnumDef>> enums,
            List<Map.Entry<String, ListDef>> lists,
            List<Map.Entry<String, MapDef>> maps,
            Map<String, IrInheritanceResolver> resolvers,
            List<Map.Entry<String, RelationshipDef>> relationships,
            Map<String, String> symbolAnchors) {

        boolean hasDataModel = !entities.isEmpty() || !structs.isEmpty()
                || !enums.isEmpty() || !lists.isEmpty() || !maps.isEmpty();
        if (!hasDataModel) return;
        sb.append(DIVIDER).append("\n## Data Model\n");

        // ER diagram at the top
        if (!relationships.isEmpty()) {
            appendErDiagram(sb, relationships.stream().map(Map.Entry::getValue).toList());
        }

        if (!entities.isEmpty()) {
            sb.append("\n### Entities\n");
            for (var e : entities) {
                String ns = e.getKey();
                EntityDef entity = e.getValue();
                sb.append("\n#### ").append(ns).append(".").append(entity.name()).append("\n\n");
                if (IrInheritanceResolver.parentName(entity).isPresent()) {
                    String parentName = IrInheritanceResolver.parentName(entity).get();
                    String parentAnchor = symbolAnchors.getOrDefault(parentName, anchor(parentName));
                    sb.append("*Extends: [").append(parentName).append("](#")
                      .append(parentAnchor).append(")*\n\n");
                }
                renderDocComments(sb, entity.docComments());
                IrInheritanceResolver resolver = resolvers.get(ns);
                List<FieldDef> fields = resolver != null
                        ? resolver.resolveAllFields(entity) : entity.fields();
                if (!fields.isEmpty()) {
                    appendFieldTable(sb, fields);
                }
                if (!entity.invariants().isEmpty()) {
                    sb.append("\n**Invariants:**\n\n");
                    for (var inv : entity.invariants()) {
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

        if (!structs.isEmpty()) {
            sb.append("\n### Value Objects\n");
            for (var e : structs) {
                ShapeStructDef s = e.getValue();
                sb.append("\n#### ").append(e.getKey()).append(".").append(s.name()).append("\n\n");
                renderDocComments(sb, s.docComments());
                if (!s.fields().isEmpty()) {
                    appendFieldTable(sb, s.fields());
                }
            }
        }

        if (!enums.isEmpty()) {
            sb.append("\n### Enumerations\n");
            for (var e : enums) {
                EnumDef enumDef = e.getValue();
                sb.append("\n#### ").append(e.getKey()).append(".").append(enumDef.name()).append("\n\n");
                renderDocComments(sb, enumDef.docComments());
                sb.append("| Member | Ordinal |\n");
                sb.append("|--------|----------|\n");
                for (var member : enumDef.members()) {
                    String ordinal = member.ordinalOrNull() != null
                            ? String.valueOf(member.ordinalOrNull()) : DASH;
                    sb.append("| ").append(member.name())
                      .append(" | ").append(ordinal)
                      .append(" |\n");
                }
            }
        }

        if (!lists.isEmpty() || !maps.isEmpty()) {
            sb.append("\n### Collections\n");
            for (var e : lists) {
                ListDef list = e.getValue();
                sb.append("\n#### ").append(e.getKey()).append(".").append(list.name()).append("\n\n");
                renderDocComments(sb, list.docComments());
                sb.append("`List<").append(renderTypeRef(list.memberType())).append(">`\n\n");
            }
            for (var e : maps) {
                MapDef map = e.getValue();
                sb.append("\n#### ").append(e.getKey()).append(".").append(map.name()).append("\n\n");
                renderDocComments(sb, map.docComments());
                sb.append("`Map<").append(renderTypeRef(map.keyType())).append(", ")
                  .append(renderTypeRef(map.valueType())).append(">`\n\n");
            }
        }
    }

    // ── Combined Global Invariants ────────────────────────────────────────────

    private static void appendCombinedGlobalInvariantsSection(
            StringBuilder sb,
            List<Map.Entry<String, InvariantDef>> invariants) {
        if (invariants.isEmpty()) return;
        sb.append(DIVIDER).append("\n## Global Invariants\n\n");
        sb.append("Cross-entity constraints that must always hold true:\n\n");
        for (var e : invariants) {
            InvariantDef inv = e.getValue();
            sb.append("### ").append(e.getKey()).append(".").append(inv.name()).append("\n\n");
            renderDocComments(sb, inv.docComments());
            sb.append("**Scope:** ").append(String.join(", ", inv.scope())).append("\n\n");
            sb.append("**Expression:** `").append(inv.expression()).append("`\n\n");
            sb.append("**Severity:** ").append(inv.severity()).append("\n\n");
            if (inv.message().isPresent()) {
                sb.append("**Message:** ").append(inv.message().get()).append("\n\n");
            }
        }
    }

    // ── Combined Relationships ────────────────────────────────────────────────

    private static void appendCombinedRelationshipsSection(
            StringBuilder sb,
            List<Map.Entry<String, RelationshipDef>> relationships,
            Map<String, String> symbolAnchors) {
        if (relationships.isEmpty()) return;
        sb.append(DIVIDER).append("\n## Relationships\n");
        for (var e : relationships) {
            RelationshipDef rel = e.getValue();
            sb.append("\n#### ").append(e.getKey()).append(".").append(rel.name()).append("\n\n");
            renderDocComments(sb, rel.docComments());
            rel.description().ifPresent(d -> sb.append("**Description:** ").append(d).append("\n"));
            String fromName = symRefName(rel.fromEntityRef());
            String toName   = symRefName(rel.toEntityRef());
            sb.append("**From:** ").append(linkName(fromName, symbolAnchors))
              .append(" **→** ").append(linkName(toName, symbolAnchors))
              .append(" (").append(rel.cardinality().chronosName()).append(")\n");
            sb.append("**Semantics:** ").append(rel.effectiveSemantics().chronosName()).append("\n");
            rel.inverseField().ifPresent(f ->
                    sb.append("**Inverse Field:** ").append(f).append("\n"));
        }
    }

    // ── Combined State Machines ───────────────────────────────────────────────

    private static void appendCombinedStateMachinesSection(
            StringBuilder sb,
            List<Map.Entry<String, StateMachineDef>> stateMachines) {
        if (stateMachines.isEmpty()) return;
        sb.append(DIVIDER).append("\n## State Machines\n");
        for (var e : stateMachines) {
            StateMachineDef sm = e.getValue();
            sb.append("\n### ").append(e.getKey()).append(".").append(sm.name()).append("\n\n");
            renderDocComments(sb, sm.docComments());

            StringBuilder meta = new StringBuilder();
            meta.append("**Entity:** ").append(sm.entityName())
                .append(" | **Field:** ").append(sm.fieldName())
                .append(" | **Initial:** ").append(sm.initialState());
            if (!sm.terminalStates().isEmpty()) {
                meta.append(" | **Terminal:** ").append(String.join(", ", sm.terminalStates()));
            }
            sb.append(meta).append("\n\n");

            sb.append("| From | To | Guard | Action |\n");
            sb.append("|------|----|-------|--------|\n");
            for (var t : sm.transitions()) {
                sb.append("| ").append(t.fromState())
                  .append(" | ").append(t.toState())
                  .append(" | ").append(t.guard().map(g -> "`" + g + "`").orElse(DASH))
                  .append(" | ").append(t.action().orElse(DASH))
                  .append(" |\n");
            }
            sb.append("\n");

            sb.append("```mermaid\n");
            sb.append("stateDiagram-v2\n");
            sb.append("    [*] --> ").append(sm.initialState()).append("\n");
            for (var t : sm.transitions()) {
                sb.append("    ").append(t.fromState()).append(" --> ").append(t.toState()).append("\n");
            }
            for (var terminal : sm.terminalStates()) {
                sb.append("    ").append(terminal).append(" --> [*]\n");
            }
            sb.append("```\n");
        }
    }

    // ── Combined Actors ───────────────────────────────────────────────────────

    private static void appendCombinedActorsSection(
            StringBuilder sb,
            List<Map.Entry<String, ActorDef>> actors,
            Map<String, String> symbolAnchors) {
        if (actors.isEmpty()) return;
        sb.append(DIVIDER).append("\n## Actors\n");
        for (var e : actors) {
            ActorDef actor = e.getValue();
            sb.append("\n#### ").append(e.getKey()).append(".").append(actor.name()).append("\n\n");
            renderDocComments(sb, actor.docComments());
            sb.append("**Description:** ").append(actor.description().orElse(DASH)).append("\n");
            actor.parentRef().ifPresent(ref -> {
                String parentName   = symRefName(ref);
                String parentAnchor = symbolAnchors.getOrDefault(parentName, anchor(parentName));
                sb.append("**Extends:** [").append(parentName).append("](#")
                  .append(parentAnchor).append(")\n");
            });
        }
    }

    // ── Authorization (single-file) ───────────────────────────────────────────

    private static void appendAuthorizationSection(StringBuilder sb, IrModel model) {
        if (model.roles().isEmpty()) return;
        sb.append(DIVIDER).append("\n## Authorization\n\n");

        sb.append("### Roles\n\n");
        sb.append("| Role | Allow | Deny |\n");
        sb.append("|------|-------|------|\n");
        for (var role : model.roles()) {
            String allow = role.allowedPermissions().isEmpty()
                    ? DASH : String.join(", ", role.allowedPermissions());
            String deny  = role.deniedPermissions().isEmpty()
                    ? DASH : String.join(", ", role.deniedPermissions());
            sb.append("| ").append(role.name())
              .append(" | ").append(allow)
              .append(" | ").append(deny)
              .append(" |\n");
        }
        sb.append("\n");

        boolean hasJourneyAuth = model.journeys().stream()
                .anyMatch(j -> j.traits().stream().anyMatch(t -> "authorize".equals(t.name())));
        if (hasJourneyAuth) {
            sb.append("### Journey Authorization Matrix\n\n");
            sb.append("| Journey | Actor | Role | Permission |\n");
            sb.append("|---------|-------|------|------------|\n");
            for (var journey : model.journeys()) {
                for (var t : journey.traits()) {
                    if (!"authorize".equals(t.name())) continue;
                    String role  = authTraitRef(t, "role");
                    String perm  = authTraitRef(t, "permission");
                    String actor = journey.actorName().orElse(DASH);
                    sb.append("| ").append(journey.name())
                      .append(" | ").append(actor)
                      .append(" | ").append(role != null ? role : DASH)
                      .append(" | ").append(perm != null ? perm : DASH)
                      .append(" |\n");
                }
            }
        }
    }

    // ── Combined Authorization ────────────────────────────────────────────────

    private static void appendCombinedAuthorizationSection(
            StringBuilder sb,
            List<Map.Entry<String, RoleDef>> roles,
            List<Map.Entry<String, JourneyDef>> journeys) {
        if (roles.isEmpty()) return;
        sb.append(DIVIDER).append("\n## Authorization\n\n");

        sb.append("### Roles\n\n");
        sb.append("| Role | Allow | Deny |\n");
        sb.append("|------|-------|------|\n");
        for (var e : roles) {
            RoleDef role = e.getValue();
            String allow = role.allowedPermissions().isEmpty()
                    ? DASH : String.join(", ", role.allowedPermissions());
            String deny  = role.deniedPermissions().isEmpty()
                    ? DASH : String.join(", ", role.deniedPermissions());
            sb.append("| ").append(e.getKey()).append(".").append(role.name())
              .append(" | ").append(allow)
              .append(" | ").append(deny)
              .append(" |\n");
        }
        sb.append("\n");

        boolean hasJourneyAuth = journeys.stream()
                .anyMatch(e -> e.getValue().traits().stream().anyMatch(t -> "authorize".equals(t.name())));
        if (hasJourneyAuth) {
            sb.append("### Journey Authorization Matrix\n\n");
            sb.append("| Journey | Actor | Role | Permission |\n");
            sb.append("|---------|-------|------|------------|\n");
            for (var e : journeys) {
                JourneyDef journey = e.getValue();
                for (var t : journey.traits()) {
                    if (!"authorize".equals(t.name())) continue;
                    String role  = authTraitRef(t, "role");
                    String perm  = authTraitRef(t, "permission");
                    String actor = journey.actorName().orElse(DASH);
                    sb.append("| ").append(e.getKey()).append(".").append(journey.name())
                      .append(" | ").append(actor)
                      .append(" | ").append(role != null ? role : DASH)
                      .append(" | ").append(perm != null ? perm : DASH)
                      .append(" |\n");
                }
            }
        }
    }

    /** Extracts a named ReferenceValue argument from a trait (e.g. role or permission). */
    private static String authTraitRef(TraitApplication t, String key) {
        return t.namedValue(key)
                .filter(v -> v instanceof TraitValue.ReferenceValue)
                .map(v -> ((TraitValue.ReferenceValue) v).ref())
                .orElse(null);
    }

    // ── Combined Policies ─────────────────────────────────────────────────────

    private static void appendCombinedPoliciesSection(
            StringBuilder sb,
            List<Map.Entry<String, PolicyDef>> policies) {
        if (policies.isEmpty()) return;
        sb.append(DIVIDER).append("\n## Policies\n");
        for (var e : policies) {
            PolicyDef policy = e.getValue();
            sb.append("\n#### ").append(e.getKey()).append(".").append(policy.name()).append("\n\n");
            renderDocComments(sb, policy.docComments());
            sb.append("**Description:** ").append(policy.description()).append("\n");
            policy.complianceFramework().ifPresent(c ->
                    sb.append("**Compliance:** ").append(c).append("\n"));
        }
    }

    // ── Combined Prohibitions ─────────────────────────────────────────────────

    private static void appendCombinedProhibitionsSection(
            StringBuilder sb,
            List<Map.Entry<String, DenyDef>> denies) {
        if (denies.isEmpty()) return;
        sb.append(DIVIDER).append("\n## Prohibitions\n");
        for (var e : denies) {
            DenyDef deny = e.getValue();
            sb.append("\n#### ").append(e.getKey()).append(".").append(deny.name()).append("\n\n");
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
        }
    }

    // ── Combined Error Catalog ────────────────────────────────────────────────

    private static void appendCombinedErrorCatalogSection(
            StringBuilder sb,
            List<Map.Entry<String, ErrorDef>> errors) {
        if (errors.isEmpty()) return;
        sb.append(DIVIDER).append("\n## Error Catalog\n");
        for (var e : errors) {
            ErrorDef error = e.getValue();
            sb.append("\n#### ").append(e.getKey()).append(".").append(error.name()).append("\n\n");
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
        }
    }

    // ── Combined Telemetry Catalog ────────────────────────────────────────────

    private static void appendCombinedTelemetryCatalogSection(
            StringBuilder sb,
            List<Map.Entry<String, JourneyDef>> journeys) {
        var jList = journeys.stream().map(Map.Entry::getValue).toList();
        var rows = collectTelemetry(
                jList,
                j -> journeys.stream()
                        .filter(e -> e.getValue() == j)
                        .findFirst()
                        .map(e -> e.getKey() + "." + j.name())
                        .orElse(j.name()));
        if (rows.isEmpty()) return;
        sb.append(DIVIDER).append("\n## Telemetry Catalog\n\n");
        sb.append("| Event | Journey | Step | Path |\n");
        sb.append("|-------|---------|------|------|\n");
        for (var row : rows) {
            sb.append("| ").append(row.event())
              .append(" | ").append(row.journey())
              .append(" | ").append(row.step())
              .append(" | ").append(row.path())
              .append(" |\n");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Builds a simple-name → anchor map for shapes in a single-namespace model. */
    private static Map<String, String> buildSymbolAnchors(IrModel model) {
        var map = new HashMap<String, String>();
        model.shapes().forEach(s -> map.put(s.name(), anchor(s.name())));
        return map;
    }

    /** Builds a combined name → anchor map for all shapes across multiple models.
     *  Both simple names and FQ names are stored; simple names map to the FQ anchor. */
    private static Map<String, String> buildCombinedSymbolAnchors(List<IrModel> models) {
        var map = new HashMap<String, String>();
        for (var m : models) {
            for (var s : m.shapes()) {
                String fq = m.namespace() + "." + s.name();
                map.put(s.name(), anchor(fq));
                map.put(fq, anchor(fq));
            }
        }
        return map;
    }

    /** Builds an enum member name → enum heading anchor map for cross-referencing state IDs. */
    private static Map<String, String> buildEnumMemberAnchors(IrModel model) {
        var map = new HashMap<String, String>();
        for (var e : model.enums()) {
            String enumAnchor = anchor(e.name());
            for (var m : e.members()) map.put(m.name(), enumAnchor);
        }
        return map;
    }

    /** Combined variant: maps enum member → FQ enum anchor across all models. */
    private static Map<String, String> buildCombinedEnumMemberAnchors(List<IrModel> models) {
        var map = new HashMap<String, String>();
        for (var m : models) {
            for (var e : m.enums()) {
                String enumAnchor = anchor(m.namespace() + "." + e.name());
                for (var member : e.members()) map.put(member.name(), enumAnchor);
            }
        }
        return map;
    }

    /** Appends field table rows, omitting the Required column when no fields are required. */
    private static void appendFieldTable(StringBuilder sb, List<FieldDef> fields) {
        boolean hasRequired = fields.stream().anyMatch(FieldDef::isRequired);
        if (hasRequired) {
            sb.append("| Field | Type | Required |\n");
            sb.append("|-------|------|----------|\n");
            for (var field : fields) {
                sb.append("| ").append(field.name())
                  .append(" | ").append(renderTypeRef(field.type()))
                  .append(" | ").append(field.isRequired() ? "\u2713" : "")
                  .append(" |\n");
            }
        } else {
            sb.append("| Field | Type |\n");
            sb.append("|-------|------|\n");
            for (var field : fields) {
                sb.append("| ").append(field.name())
                  .append(" | ").append(renderTypeRef(field.type()))
                  .append(" |\n");
            }
        }
    }

    private static void renderDocComments(StringBuilder sb, List<String> docs) {
        if (docs == null || docs.isEmpty()) return;
        for (var doc : docs) {
            sb.append("> ").append(doc).append("\n");
        }
        sb.append("\n");
    }

    private static String renderOutcomeExpr(OutcomeExpr expr, RenderCtx ctx) {
        return switch (expr) {
            case OutcomeExpr.TransitionTo t -> "TransitionTo(" + ctx.linkEnumMember(t.stateId()) + ")";
            case OutcomeExpr.ReturnToStep r -> {
                String stepAnchor = ctx.stepAnchor(r.stepId());
                String linked = stepAnchor.isEmpty()
                        ? r.stepId()
                        : "[" + r.stepId() + "](#" + stepAnchor + ")";
                yield "ReturnToStep(" + linked + ")";
            }
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

    /** Returns a Markdown link if the name is in the anchor map, otherwise plain text. */
    private static String linkName(String name, Map<String, String> symbolAnchors) {
        String a = symbolAnchors.get(name);
        return a != null ? "[" + name + "](#" + a + ")" : name;
    }

    /** Extracts the SLO value from a {@code @slo(ms: N)} trait on a step, if present. */
    private static Optional<String> stepSlo(Step step) {
        return step.traits().stream()
                .filter(t -> "slo".equals(t.name()))
                .findFirst()
                .flatMap(t -> t.namedValue("ms"))
                .filter(v -> v instanceof TraitValue.NumberValue)
                .map(v -> "\u2264 " + (long) ((TraitValue.NumberValue) v).value() + " ms");
    }

    /** Generates a GitHub-flavoured Markdown heading anchor from a name. */
    private static String anchor(String text) {
        return text.toLowerCase().replaceAll("[^a-z0-9\\-]", "");
    }
}
