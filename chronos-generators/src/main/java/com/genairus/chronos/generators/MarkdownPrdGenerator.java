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
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        appendJourneyContent(sb, journey);
    }

    private static void appendJourneyContent(StringBuilder sb, JourneyDef journey) {
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
        var actors        = nsSort(models, IrModel::actors);
        var policies      = nsSort(models, IrModel::policies);
        var denies        = nsSort(models, IrModel::denies);
        var errors        = nsSort(models, IrModel::errors);

        // One IrInheritanceResolver per namespace for entity field resolution
        Map<String, IrInheritanceResolver> resolvers = models.stream()
                .collect(Collectors.toMap(IrModel::namespace, IrInheritanceResolver::new));

        // Title
        sb.append("# Chronos Product Requirements Document\n");

        // Namespaces section
        var namespaces = models.stream().map(IrModel::namespace).distinct().sorted().toList();
        sb.append("\n## Namespaces\n\n");
        namespaces.forEach(ns -> sb.append("- `").append(ns).append("`\n"));

        appendCombinedToc(sb, journeys, entities, structs, enums, lists, maps,
                invariants, relationships, actors, policies, denies, errors);
        appendCombinedJourneysSection(sb, journeys);
        appendCombinedDataModelSection(sb, entities, structs, enums, lists, maps, resolvers);
        appendCombinedGlobalInvariantsSection(sb, invariants);
        appendCombinedRelationshipsSection(sb, relationships);
        appendCombinedActorsSection(sb, actors);
        appendCombinedPoliciesSection(sb, policies);
        appendCombinedProhibitionsSection(sb, denies);
        appendCombinedErrorCatalogSection(sb, errors);

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
            List<Map.Entry<String, ActorDef>> actors,
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
        if (!actors.isEmpty())        sb.append("- [Actors](#actors)\n");
        if (!policies.isEmpty())      sb.append("- [Policies](#policies)\n");
        if (!denies.isEmpty())        sb.append("- [Prohibitions](#prohibitions)\n");
        if (!errors.isEmpty())        sb.append("- [Error Catalog](#error-catalog)\n");
    }

    private static void appendCombinedJourneysSection(
            StringBuilder sb,
            List<Map.Entry<String, JourneyDef>> journeys) {
        if (journeys.isEmpty()) return;
        sb.append(DIVIDER).append("\n## Journeys\n");
        for (var e : journeys) {
            sb.append("\n### ").append(e.getKey()).append(".").append(e.getValue().name()).append("\n\n");
            appendJourneyContent(sb, e.getValue());
        }
    }

    private static void appendCombinedDataModelSection(
            StringBuilder sb,
            List<Map.Entry<String, EntityDef>> entities,
            List<Map.Entry<String, ShapeStructDef>> structs,
            List<Map.Entry<String, EnumDef>> enums,
            List<Map.Entry<String, ListDef>> lists,
            List<Map.Entry<String, MapDef>> maps,
            Map<String, IrInheritanceResolver> resolvers) {

        boolean hasDataModel = !entities.isEmpty() || !structs.isEmpty()
                || !enums.isEmpty() || !lists.isEmpty() || !maps.isEmpty();
        if (!hasDataModel) return;
        sb.append(DIVIDER).append("\n## Data Model\n");

        if (!entities.isEmpty()) {
            sb.append("\n### Entities\n");
            for (var e : entities) {
                String ns = e.getKey();
                EntityDef entity = e.getValue();
                sb.append("\n#### ").append(ns).append(".").append(entity.name()).append("\n\n");
                if (IrInheritanceResolver.parentName(entity).isPresent()) {
                    sb.append("*Extends: ").append(IrInheritanceResolver.parentName(entity).get()).append("*\n\n");
                }
                renderDocComments(sb, entity.docComments());
                IrInheritanceResolver resolver = resolvers.get(ns);
                List<FieldDef> fields = resolver != null
                        ? resolver.resolveAllFields(entity) : entity.fields();
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
                    sb.append("| Field | Type | Required |\n");
                    sb.append("|-------|------|----------|\n");
                    for (var field : s.fields()) {
                        sb.append("| ").append(field.name())
                          .append(" | ").append(renderTypeRef(field.type()))
                          .append(" | ").append(field.isRequired() ? "\u2713" : "")
                          .append(" |\n");
                    }
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

    private static void appendCombinedRelationshipsSection(
            StringBuilder sb,
            List<Map.Entry<String, RelationshipDef>> relationships) {
        if (relationships.isEmpty()) return;
        sb.append(DIVIDER).append("\n## Relationships\n");
        for (var e : relationships) {
            RelationshipDef rel = e.getValue();
            sb.append("\n#### ").append(e.getKey()).append(".").append(rel.name()).append("\n\n");
            renderDocComments(sb, rel.docComments());
            sb.append("**From:** ").append(symRefName(rel.fromEntityRef()))
              .append(" **→** ").append(symRefName(rel.toEntityRef()))
              .append(" (").append(rel.cardinality().chronosName()).append(")\n");
            sb.append("**Semantics:** ").append(rel.effectiveSemantics().chronosName()).append("\n");
            rel.inverseField().ifPresent(f ->
                    sb.append("**Inverse Field:** ").append(f).append("\n"));
        }
    }

    private static void appendCombinedActorsSection(
            StringBuilder sb,
            List<Map.Entry<String, ActorDef>> actors) {
        if (actors.isEmpty()) return;
        sb.append(DIVIDER).append("\n## Actors\n");
        for (var e : actors) {
            ActorDef actor = e.getValue();
            sb.append("\n#### ").append(e.getKey()).append(".").append(actor.name()).append("\n\n");
            renderDocComments(sb, actor.docComments());
            sb.append("**Description:** ").append(actor.description().orElse(DASH)).append("\n");
        }
    }

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
