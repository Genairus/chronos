package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * Sealed type for a field inside a step body (grammar rule: {@code stepField}).
 *
 * <p>Five alternatives mirror the grammar productions:
 * {@code action}, {@code expectation}, {@code outcome}, {@code telemetry}, and {@code risk}.
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
}
