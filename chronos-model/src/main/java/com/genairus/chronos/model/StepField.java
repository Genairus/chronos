package com.genairus.chronos.model;

import java.util.List;

/**
 * One field inside a {@link Step} body.
 *
 * <p>A step may contain any combination of these five field types.
 * The validator (CHR-003) enforces that {@code action} and {@code expectation}
 * are both present.
 */
public sealed interface StepField
        permits StepField.ActionField,
                StepField.ExpectationField,
                StepField.OutcomeField,
                StepField.TelemetryField,
                StepField.RiskField {

    /** Source location of this field. */
    SourceLocation location();

    /**
     * What the actor does.
     * <pre>action: "Submits shipping address form"</pre>
     */
    record ActionField(String text, SourceLocation location) implements StepField {}

    /**
     * What the system must do in response — becomes an acceptance criterion.
     * <pre>expectation: "System validates address and calculates shipping"</pre>
     */
    record ExpectationField(String text, SourceLocation location) implements StepField {}

    /**
     * The state transition triggered by completing this step.
     * <pre>outcome: TransitionTo(ShippingMethodsDisplayed)</pre>
     */
    record OutcomeField(OutcomeExpr expr, SourceLocation location) implements StepField {}

    /**
     * Named telemetry events emitted when this step executes.
     * <pre>telemetry: [PaymentMethodSelectedEvent, OrderPlacedEvent]</pre>
     */
    record TelemetryField(List<String> events, SourceLocation location) implements StepField {}

    /**
     * A free-text architectural, performance, or business risk annotation.
     * <pre>risk: "High latency from third-party address validation service"</pre>
     */
    record RiskField(String text, SourceLocation location) implements StepField {}
}
