package com.genairus.chronos.model;

import java.util.Optional;

/**
 * A state transition within a state machine.
 *
 * <pre>
 *   PENDING -> PAID {
 *       guard: "payment.status == APPROVED"
 *       action: "Emit OrderPaidEvent"
 *   }
 * </pre>
 *
 * @param fromState the source state
 * @param toState   the target state
 * @param guard     optional guard condition expression
 * @param action    optional action to perform on transition
 * @param location  source location of the transition
 */
public record Transition(
        String fromState,
        String toState,
        Optional<String> guard,
        Optional<String> action,
        SourceLocation location) {}

