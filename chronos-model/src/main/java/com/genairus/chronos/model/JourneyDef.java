package com.genairus.chronos.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A journey definition — the central construct of the Chronos language.
 *
 * <p>A journey represents a cohesive unit of user/system value: what you might
 * call a feature, use case, or user story. It drives artifact generation into
 * issue tracker epics, Gherkin features, state diagrams, and more.
 *
 * <pre>
 *   @kpi(metric: "CheckoutConversion", target: "&gt;75%")
 *   @owner("product-checkout-team")
 *   journey GuestCheckout {
 *       actor: Customer
 *       preconditions: ["Cart is not empty", "Actor is not logged in"]
 *       steps: [ ... ]
 *       variants: { PaymentDeclined: { ... } }
 *       outcomes: { success: "...", failure: "..." }
 *   }
 * </pre>
 *
 * <p>Fields with nullable values are optional in the grammar; the validator
 * enforces semantic requirements (CHR-001 through CHR-009).
 *
 * @param name          the journey name (PascalCase)
 * @param traits        trait applications (e.g. {@code @kpi}, {@code @owner}, {@code @compliance})
 * @param docComments   lines from preceding {@code ///} doc comments
 * @param actor         the declared actor name, or {@code null} if missing (CHR-001)
 * @param preconditions the pre-conditions list (may be empty)
 * @param steps         the ordered happy-path steps (may be empty)
 * @param variants      named variant branches keyed by variant name
 * @param outcomes      the terminal outcome descriptions, or {@code null} if the
 *                      {@code outcomes} block is absent (CHR-002)
 * @param location      source location of the {@code journey} keyword
 */
public record JourneyDef(
        String name,
        List<TraitApplication> traits,
        List<String> docComments,
        String actor,
        List<String> preconditions,
        List<Step> steps,
        Map<String, Variant> variants,
        JourneyOutcomes outcomes,
        SourceLocation location) implements ShapeDefinition {

    /** Returns the actor name, or empty if not declared (triggers CHR-001). */
    public Optional<String> actorName() {
        return Optional.ofNullable(actor);
    }

    /** Returns the outcomes block, or empty if absent (triggers CHR-002). */
    public Optional<JourneyOutcomes> journeyOutcomes() {
        return Optional.ofNullable(outcomes);
    }

    /**
     * Returns the value of the first {@code @kpi} trait's {@code metric} argument,
     * or empty if no {@code @kpi} trait is present (triggers CHR-009 warning).
     */
    public Optional<String> kpiMetric() {
        return traits.stream()
                .filter(t -> "kpi".equals(t.name()))
                .flatMap(t -> t.namedValue("metric").stream())
                .filter(v -> v instanceof TraitValue.StringValue)
                .map(v -> ((TraitValue.StringValue) v).value())
                .findFirst();
    }

    /** Returns {@code true} if this journey carries any {@code @compliance} trait. */
    public boolean hasComplianceTrait() {
        return traits.stream().anyMatch(t -> "compliance".equals(t.name()));
    }
}
