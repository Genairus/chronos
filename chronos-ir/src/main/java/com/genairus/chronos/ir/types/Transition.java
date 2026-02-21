package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;

import java.util.Optional;

/**
 * A state-machine transition in the IR type system.
 *
 * @param fromState  source state name
 * @param toState    target state name
 * @param guard      optional guard condition expression
 * @param action     optional action expression
 * @param span       source location
 */
public record Transition(
        String fromState,
        String toState,
        Optional<String> guard,
        Optional<String> action,
        Span span) {}
