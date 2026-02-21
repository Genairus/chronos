package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * Sealed type for a field inside a step body (grammar rule: {@code stepField}).
 *
 * <p>Seven alternatives mirror the grammar productions:
 * {@code action}, {@code expectation}, {@code outcome}, {@code telemetry}, {@code risk},
 * {@code input}, and {@code output}.
 */
public sealed interface SyntaxStepField {

    /** {@code action: "..."} — what the actor does in this step. */
    record Action(String text, Span span) implements SyntaxStepField {}

    /** {@code expectation: "..."} — what the system must do in response. */
    record Expectation(String text, Span span) implements SyntaxStepField {}

    /** {@code outcome: TransitionTo(...)|ReturnToStep(...)} — a state-transition expression. */
    record Outcome(SyntaxOutcomeExpr expr, Span span) implements SyntaxStepField {}

    /** {@code telemetry: [id, ...]} — event identifiers emitted by this step (unresolved). */
    record Telemetry(List<String> ids, Span span) implements SyntaxStepField {}

    /** {@code risk: "..."} — free-text risk annotation. */
    record Risk(String text, Span span) implements SyntaxStepField {}

    /** {@code input: [name: Type, ...]} — typed data fields consumed by this step. */
    record Input(List<SyntaxDataField> fields, Span span) implements SyntaxStepField {}

    /** {@code output: [name: Type, ...]} — typed data fields produced by this step. */
    record Output(List<SyntaxDataField> fields, Span span) implements SyntaxStepField {}
}
