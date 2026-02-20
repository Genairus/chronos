package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * A state machine declaration (grammar rule: {@code statemachineDef}).
 *
 * <p>Example:
 * <pre>{@code
 * statemachine OrderLifecycle {
 *     entity: Order
 *     field: status
 *     states: [PENDING, PAID, SHIPPED, DELIVERED, CANCELLED]
 *     initial: PENDING
 *     terminal: [DELIVERED, CANCELLED]
 *     transitions: [ PENDING -> PAID { guard: "payment approved" } ]
 * }
 * }</pre>
 *
 * <p>All list fields default to an empty list when absent; string fields to empty string.
 * {@code entityName} and {@code fieldName} reference raw identifiers — not yet resolved.
 */
public record SyntaxStateMachineDecl(
        String name,
        List<String> docComments,
        String entityName,
        String fieldName,
        List<String> states,
        String initialState,
        List<String> terminalStates,
        List<SyntaxTransition> transitions,
        List<SyntaxTrait> traits,
        Span span
) implements SyntaxDecl {}
