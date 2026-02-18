package com.genairus.chronos.model;

/**
 * A state-transition expression used in step and variant {@code outcome} fields.
 *
 * <p>Two forms from the spec:
 * <pre>
 *   outcome: TransitionTo(ShippingMethodsDisplayed)
 *   outcome: ReturnToStep(ChoosePayment)
 * </pre>
 */
public sealed interface OutcomeExpr
        permits OutcomeExpr.TransitionTo,
                OutcomeExpr.ReturnToStep {

    /** Returns the target state or step name. */
    String target();

    /** Returns the source location of this expression. */
    SourceLocation location();

    /**
     * Transitions the journey to a new named state.
     * <pre>outcome: TransitionTo(OrderConfirmed)</pre>
     *
     * @param target   the name of the target state
     * @param location source location
     */
    record TransitionTo(String target, SourceLocation location) implements OutcomeExpr {}

    /**
     * Returns flow to a previously declared step.
     * <pre>outcome: ReturnToStep(ChoosePayment)</pre>
     *
     * @param target   the name of the step to return to
     * @param location source location
     */
    record ReturnToStep(String target, SourceLocation location) implements OutcomeExpr {}
}
