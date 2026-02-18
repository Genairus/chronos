package com.genairus.chronos.model;

import java.util.List;
import java.util.Optional;

/**
 * A single step inside a journey or variant — one actor–system interaction.
 *
 * <pre>
 *   @slo(latency: "2s", p99: true)
 *   step PlaceOrder {
 *       action:      "Clicks 'Place Order'"
 *       expectation: "Order is created with status PAID"
 *       outcome:     TransitionTo(OrderConfirmed)
 *       telemetry:   [OrderPlacedEvent, PaymentProcessedEvent]
 *   }
 * </pre>
 *
 * @param name     the step name (PascalCase)
 * @param traits   step-level trait applications (e.g. {@code @slo}, {@code @audit})
 * @param fields   the declared step fields in source order
 * @param location source location of the {@code step} keyword
 */
public record Step(
        String name,
        List<TraitApplication> traits,
        List<StepField> fields,
        SourceLocation location) {

    /** Returns the {@code action} field text, or empty if absent (CHR-003). */
    public Optional<String> action() {
        return fields.stream()
                .filter(f -> f instanceof StepField.ActionField)
                .map(f -> ((StepField.ActionField) f).text())
                .findFirst();
    }

    /** Returns the {@code expectation} field text, or empty if absent (CHR-003). */
    public Optional<String> expectation() {
        return fields.stream()
                .filter(f -> f instanceof StepField.ExpectationField)
                .map(f -> ((StepField.ExpectationField) f).text())
                .findFirst();
    }

    /** Returns the {@code outcome} expression, or empty if not declared. */
    public Optional<OutcomeExpr> outcome() {
        return fields.stream()
                .filter(f -> f instanceof StepField.OutcomeField)
                .map(f -> ((StepField.OutcomeField) f).expr())
                .findFirst();
    }

    /** Returns the {@code telemetry} event list, or empty list if not declared. */
    public List<String> telemetryEvents() {
        return fields.stream()
                .filter(f -> f instanceof StepField.TelemetryField)
                .flatMap(f -> ((StepField.TelemetryField) f).events().stream())
                .toList();
    }

    /** Returns the {@code risk} annotation text, or empty if absent. */
    public Optional<String> risk() {
        return fields.stream()
                .filter(f -> f instanceof StepField.RiskField)
                .map(f -> ((StepField.RiskField) f).text())
                .findFirst();
    }
}
