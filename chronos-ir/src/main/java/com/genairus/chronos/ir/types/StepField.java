package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * Sealed type for a field inside an IR step body.
 *
 * <p>Seven alternatives mirror the grammar productions:
 * {@code action}, {@code expectation}, {@code outcome}, {@code telemetry}, {@code risk},
 * {@code input}, and {@code output}.
 */
public sealed interface StepField
        permits StepField.Action,
                StepField.Expectation,
                StepField.Outcome,
                StepField.Telemetry,
                StepField.Risk,
                StepField.Input,
                StepField.Output {

    /** {@code action: "..."} — what the actor does in this step. */
    record Action(String text, Span span) implements StepField {}

    /** {@code expectation: "..."} — what the system must do in response. */
    record Expectation(String text, Span span) implements StepField {}

    /** {@code outcome: TransitionTo(...)|ReturnToStep(...)} — a state-transition expression. */
    record Outcome(OutcomeExpr expr, Span span) implements StepField {}

    /** {@code telemetry: [id, ...]} — event identifiers emitted by this step. */
    record Telemetry(List<String> ids, Span span) implements StepField {}

    /** {@code risk: "..."} — free-text risk annotation. */
    record Risk(String text, Span span) implements StepField {}

    /** {@code input: [name: Type, ...]} — typed data fields consumed by this step. */
    record Input(List<DataField> fields, Span span) implements StepField {}

    /** {@code output: [name: Type, ...]} — typed data fields produced by this step. */
    record Output(List<DataField> fields, Span span) implements StepField {}
}
