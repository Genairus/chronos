package com.genairus.chronos.generators;

import com.genairus.chronos.ir.model.IrModel;
import com.genairus.chronos.ir.types.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Generates a Jira-importable CSV backlog from a compiled Chronos IR model.
 *
 * <h2>Mapping</h2>
 * <ul>
 *   <li>Each {@code journey} → one Jira <strong>Epic</strong></li>
 *   <li>Each happy-path {@code step} → one Jira <strong>Story</strong> linked to the epic</li>
 *   <li>Each {@code variant} → one Story (error-path summary) linked to the epic</li>
 *   <li>Each {@code policy} → one compliance Story</li>
 *   <li>Each {@code deny} → one compliance Story</li>
 * </ul>
 *
 * <h2>Row ordering (deterministic)</h2>
 * Journeys are sorted alphabetically; within each journey, happy-path steps appear in
 * declaration order, followed by variants in alphabetical order. Policies are appended
 * alphabetically after all journeys; denies alphabetically after all policies.
 *
 * <h2>Jira import</h2>
 * In Jira, go to <em>Your project → Board → Import CSV</em> and select the generated
 * file. Map the columns in the import wizard; the {@code Epic Name} and
 * {@code Epic Link} columns require Jira Software with epic support.
 *
 * <p>Note: story-point and custom-field column names vary by Jira instance. Review and
 * re-map as needed in the import wizard.
 */
public final class JiraBacklogGenerator implements ChronosGenerator {

    static final String[] HEADER = {
            "Summary", "Issue Type", "Description",
            "Priority", "Labels",
            "Epic Name", "Epic Link", "Story Points"
    };

    @Override
    public GeneratorOutput generate(IrModel model) {
        var sb = new StringBuilder();
        appendRow(sb, HEADER);

        // 1. Journeys (alphabetically) → Epic + child Stories
        model.journeys().stream()
                .sorted(Comparator.comparing(JourneyDef::name))
                .forEach(j -> appendJourney(sb, j, model.namespace()));

        // 2. Policies (alphabetically) → compliance Stories
        model.policies().stream()
                .sorted(Comparator.comparing(PolicyDef::name))
                .forEach(p -> appendPolicy(sb, p, model.namespace()));

        // 3. Denies (alphabetically) → compliance Stories
        model.denies().stream()
                .sorted(Comparator.comparing(DenyDef::name))
                .forEach(d -> appendDeny(sb, d, model.namespace()));

        String filename = model.namespace().replace('.', '-') + "-backlog.csv";
        return GeneratorOutput.of(filename, sb.toString());
    }

    // ── Journey → Epic + Stories ─────────────────────────────────────────────

    private static void appendJourney(StringBuilder sb, JourneyDef journey, String ns) {
        // Epic row
        appendRow(sb,
                journey.name(),
                "Epic",
                epicDescription(journey),
                "Medium",
                ns,
                journey.name(),  // Epic Name (required for Epics)
                "",              // Epic Link (empty — Epics are not linked to other Epics)
                String.valueOf(journey.steps().size()));

        // Happy-path steps in declaration order → Stories
        for (var step : journey.steps()) {
            appendStepRow(sb, step, journey.name(), ns, false);
        }

        // Variants sorted by name → one Story each
        journey.variants().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> appendVariantRow(sb, e.getValue(), journey.name(), ns));
    }

    // ── Step → Story ─────────────────────────────────────────────────────────

    private static void appendStepRow(StringBuilder sb, Step step,
                                       String epicName, String ns, boolean isVariantStep) {
        String summary = step.name() + ": " + step.action().orElse("(no action)");
        String labels = isVariantStep ? ns + " variant" : ns;
        appendRow(sb,
                summary,
                "Story",
                stepDescription(step),
                "Medium",
                labels,
                "",       // Epic Name (empty for Stories)
                epicName, // Epic Link → parent Epic's Summary
                "1");
    }

    // ── Variant → Story ───────────────────────────────────────────────────────

    private static void appendVariantRow(StringBuilder sb, Variant variant,
                                          String epicName, String ns) {
        String summary = "[" + variant.name() + "] error path: " + variant.triggerName();
        appendRow(sb,
                summary,
                "Story",
                variantDescription(variant),
                "High",
                ns + " variant",
                "",       // Epic Name
                epicName, // Epic Link
                String.valueOf(variant.steps().size()));
    }

    // ── Policy → compliance Story ─────────────────────────────────────────────

    private static void appendPolicy(StringBuilder sb, PolicyDef policy, String ns) {
        String framework = policy.complianceFramework().orElse("");
        String labels = framework.isEmpty()
                ? ns + " compliance"
                : ns + " compliance " + framework;
        String priority = framework.isEmpty() ? "Medium" : "High";

        var desc = new ArrayList<String>();
        if (!policy.docComments().isEmpty()) {
            desc.add(String.join(" ", policy.docComments()));
        }
        desc.add(policy.description());
        if (!framework.isEmpty()) {
            desc.add("Compliance framework: " + framework);
        }

        appendRow(sb,
                "[Policy] " + policy.name(),
                "Story",
                String.join("\n", desc),
                priority,
                labels,
                "", // Epic Name
                "", // Epic Link
                "0");
    }

    // ── Deny → compliance Story ───────────────────────────────────────────────

    private static void appendDeny(StringBuilder sb, DenyDef deny, String ns) {
        String framework = traitFirstString(deny.traits(), "compliance");
        String labels = framework.isEmpty()
                ? ns + " compliance"
                : ns + " compliance " + framework;
        String priority = switch (deny.severity().toLowerCase(java.util.Locale.ROOT)) {
            case "critical" -> "Highest";
            case "high"     -> "High";
            default         -> "Medium";
        };

        var desc = new ArrayList<String>();
        if (!deny.docComments().isEmpty()) {
            desc.add(String.join(" ", deny.docComments()));
        }
        desc.add(deny.description());
        if (!deny.scope().isEmpty()) {
            desc.add("Scope: " + String.join(", ", deny.scope()));
        }
        if (!framework.isEmpty()) {
            desc.add("Compliance framework: " + framework);
        }

        appendRow(sb,
                "[Compliance] " + deny.name(),
                "Story",
                String.join("\n", desc),
                priority,
                labels,
                "", // Epic Name
                "", // Epic Link
                "0");
    }

    // ── Description builders ──────────────────────────────────────────────────

    private static String epicDescription(JourneyDef journey) {
        var parts = new ArrayList<String>();
        if (!journey.docComments().isEmpty()) {
            parts.add(String.join(" ", journey.docComments()));
        }
        journey.actorName().ifPresent(a -> parts.add("Actor: " + a));
        if (!journey.preconditions().isEmpty()) {
            parts.add("Preconditions: " + String.join("; ", journey.preconditions()));
        }
        journey.journeyOutcomes().ifPresent(o -> {
            if (o.successOrNull() != null) parts.add("Success: " + o.successOrNull());
            if (o.failureOrNull() != null) parts.add("Failure: " + o.failureOrNull());
        });
        kpiString(journey.traits()).ifPresent(kpi -> parts.add("KPI: " + kpi));
        String comp = traitFirstString(journey.traits(), "compliance");
        if (!comp.isEmpty()) parts.add("Compliance: " + comp);
        return String.join("\n", parts);
    }

    private static String stepDescription(Step step) {
        var parts = new ArrayList<String>();
        step.expectation().ifPresent(e -> parts.add("Expectation: " + e));
        step.outcome().ifPresent(o -> parts.add("Outcome: " + formatOutcome(o)));
        step.risk().ifPresent(r -> parts.add("Risk: " + r));
        if (!step.telemetryEvents().isEmpty()) {
            parts.add("Telemetry: " + String.join(", ", step.telemetryEvents()));
        }
        return String.join("\n", parts);
    }

    private static String variantDescription(Variant variant) {
        var parts = new ArrayList<String>();
        parts.add("Error path triggered by: " + variant.triggerName());
        if (!variant.steps().isEmpty()) {
            parts.add("Steps:");
            for (var step : variant.steps()) {
                parts.add("  - " + step.name() + ": " + step.action().orElse("(no action)"));
            }
        }
        variant.outcome().ifPresent(o -> parts.add("Resolution: " + formatOutcome(o)));
        return String.join("\n", parts);
    }

    // ── Trait helpers ─────────────────────────────────────────────────────────

    private static String traitFirstString(List<TraitApplication> traits, String traitName) {
        return traits.stream()
                .filter(t -> traitName.equals(t.name()))
                .flatMap(t -> t.firstPositionalValue().stream())
                .filter(v -> v instanceof TraitValue.StringValue)
                .map(v -> ((TraitValue.StringValue) v).value())
                .findFirst()
                .orElse("");
    }

    private static Optional<String> kpiString(List<TraitApplication> traits) {
        return traits.stream()
                .filter(t -> "kpi".equals(t.name()))
                .map(t -> {
                    String metric = t.namedValue("metric")
                            .filter(v -> v instanceof TraitValue.StringValue)
                            .map(v -> ((TraitValue.StringValue) v).value())
                            .orElse("");
                    String target = t.namedValue("target")
                            .filter(v -> v instanceof TraitValue.StringValue)
                            .map(v -> ((TraitValue.StringValue) v).value())
                            .orElse("");
                    if (metric.isEmpty() && target.isEmpty()) return null;
                    return (metric.isEmpty() ? "" : "metric=" + metric)
                            + (metric.isEmpty() || target.isEmpty() ? "" : " ")
                            + (target.isEmpty() ? "" : "target=" + target);
                })
                .filter(java.util.Objects::nonNull)
                .findFirst();
    }

    private static String formatOutcome(OutcomeExpr expr) {
        return switch (expr) {
            case OutcomeExpr.TransitionTo t -> "TransitionTo(" + t.stateId() + ")";
            case OutcomeExpr.ReturnToStep r -> "ReturnToStep(" + r.stepId() + ")";
        };
    }

    // ── CSV helpers ───────────────────────────────────────────────────────────

    private static void appendRow(StringBuilder sb, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(csvQuote(cells[i]));
        }
        sb.append('\n');
    }

    /**
     * Quotes a CSV cell per RFC 4180: always wraps in double-quotes and escapes
     * internal double-quotes by doubling them.
     *
     * @param value the cell value (may be empty, never {@code null})
     * @return the quoted cell string
     */
    static String csvQuote(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
