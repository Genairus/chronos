package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

/**
 * Sealed type for a state-transition expression (grammar rule: {@code outcomeExpr}).
 *
 * <p>Two alternatives: {@code TransitionTo(StateId)} and {@code ReturnToStep(StepId)}.
 * The target identifier is a raw string — not yet resolved to an actual state or step.
 */
public sealed interface SyntaxOutcomeExpr {

    /**
     * {@code TransitionTo(stateId)} — transition the entity's state machine to the named state.
     * {@code stateId} is an unresolved raw identifier.
     */
    record TransitionTo(String stateId, Span span) implements SyntaxOutcomeExpr {}

    /**
     * {@code ReturnToStep(stepId)} — resume the journey at the named step.
     * {@code stepId} is an unresolved raw identifier.
     */
    record ReturnToStep(String stepId, Span span) implements SyntaxOutcomeExpr {}
}
