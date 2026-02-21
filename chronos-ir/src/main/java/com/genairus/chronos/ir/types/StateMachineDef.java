package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * An IR state machine definition with declared states, transitions, and guard conditions.
 *
 * @param name           the state machine name (PascalCase)
 * @param traits         trait applications
 * @param docComments    documentation comment lines
 * @param entityName     the entity this state machine is linked to
 * @param fieldName      the field on the entity that holds the state
 * @param states         list of all declared states
 * @param initialState   the initial state
 * @param terminalStates list of terminal states (may be empty)
 * @param transitions    list of state transitions
 * @param span           source location of the state machine name token
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
        Span span) implements IrShape {}
