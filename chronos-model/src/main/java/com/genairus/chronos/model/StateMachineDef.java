package com.genairus.chronos.model;

import java.util.List;

/**
 * A state machine definition with declared states, transitions, and guard conditions.
 *
 * <pre>
 *   statemachine OrderLifecycle {
 *       entity: Order
 *       field: status
 *       states: [PENDING, PAID, SHIPPED, DELIVERED, CANCELLED]
 *       initial: PENDING
 *       terminal: [DELIVERED, CANCELLED]
 *       transitions: [
 *           PENDING -> PAID {
 *               guard: "payment.status == APPROVED"
 *               action: "Emit OrderPaidEvent"
 *           },
 *           ...
 *       ]
 *   }
 * </pre>
 *
 * @param name         the state machine name (PascalCase)
 * @param traits       trait applications (e.g., @description)
 * @param docComments  documentation comments
 * @param entityName   the entity this state machine is linked to
 * @param fieldName    the field on the entity that holds the state (typically an enum)
 * @param states       list of all declared states
 * @param initialState the initial state
 * @param terminalStates list of terminal states (may be empty)
 * @param transitions  list of state transitions
 * @param location     source location of the state machine name token
 */
public record StateMachineDef(
        String name,
        List<TraitApplication> traits,
        List<String> docComments,
        String entityName,
        String fieldName,
        List<String> states,
        String initialState,
        List<String> terminalStates,
        List<Transition> transitions,
        SourceLocation location) implements ShapeDefinition {}

