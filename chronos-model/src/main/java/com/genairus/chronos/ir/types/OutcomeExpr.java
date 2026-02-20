package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;

/** An outcome expression in an IR step or variant. */
public sealed interface OutcomeExpr
        permits OutcomeExpr.TransitionTo,
                OutcomeExpr.ReturnToStep {

    record TransitionTo(String stateId, Span span) implements OutcomeExpr {}
    record ReturnToStep(String stepId,  Span span) implements OutcomeExpr {}
}
