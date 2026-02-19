package com.genairus.chronos.parser;

import com.genairus.chronos.model.ChronosModel;
import com.genairus.chronos.model.StateMachineDef;
import com.genairus.chronos.model.Transition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for parsing statemachine definitions.
 */
class StateMachineParserTest {

    @Test
    void parseBasicStateMachine() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                statemachine OrderLifecycle {
                    entity: Order
                    field: status
                    states: [PENDING, PAID, SHIPPED]
                    initial: PENDING
                    terminal: [SHIPPED]
                    transitions: [
                        PENDING -> PAID,
                        PAID -> SHIPPED
                    ]
                }
                """);

        assertEquals(1, model.stateMachines().size());
        StateMachineDef sm = model.stateMachines().get(0);
        
        assertEquals("OrderLifecycle", sm.name());
        assertEquals("Order", sm.entityName());
        assertEquals("status", sm.fieldName());
        assertEquals(3, sm.states().size());
        assertTrue(sm.states().contains("PENDING"));
        assertTrue(sm.states().contains("PAID"));
        assertTrue(sm.states().contains("SHIPPED"));
        assertEquals("PENDING", sm.initialState());
        assertEquals(1, sm.terminalStates().size());
        assertTrue(sm.terminalStates().contains("SHIPPED"));
        assertEquals(2, sm.transitions().size());
    }

    @Test
    void parseStateMachineWithGuardsAndActions() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                statemachine OrderLifecycle {
                    entity: Order
                    field: status
                    states: [PENDING, PAID, CANCELLED]
                    initial: PENDING
                    terminal: [PAID, CANCELLED]
                    transitions: [
                        PENDING -> PAID {
                            guard: "payment.status == APPROVED"
                            action: "Emit OrderPaidEvent"
                        },
                        PENDING -> CANCELLED {
                            guard: "cancellation requested"
                        }
                    ]
                }
                """);

        assertEquals(1, model.stateMachines().size());
        StateMachineDef sm = model.stateMachines().get(0);
        
        assertEquals(2, sm.transitions().size());
        
        Transition t1 = sm.transitions().get(0);
        assertEquals("PENDING", t1.fromState());
        assertEquals("PAID", t1.toState());
        assertTrue(t1.guard().isPresent());
        assertEquals("payment.status == APPROVED", t1.guard().get());
        assertTrue(t1.action().isPresent());
        assertEquals("Emit OrderPaidEvent", t1.action().get());
        
        Transition t2 = sm.transitions().get(1);
        assertEquals("PENDING", t2.fromState());
        assertEquals("CANCELLED", t2.toState());
        assertTrue(t2.guard().isPresent());
        assertEquals("cancellation requested", t2.guard().get());
        assertFalse(t2.action().isPresent());
    }

    @Test
    void parseStateMachineWithMultipleTerminalStates() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                statemachine OrderLifecycle {
                    entity: Order
                    field: status
                    states: [PENDING, PAID, DELIVERED, CANCELLED, REFUNDED]
                    initial: PENDING
                    terminal: [DELIVERED, CANCELLED, REFUNDED]
                    transitions: [
                        PENDING -> PAID,
                        PAID -> DELIVERED,
                        PENDING -> CANCELLED,
                        PAID -> REFUNDED
                    ]
                }
                """);

        assertEquals(1, model.stateMachines().size());
        StateMachineDef sm = model.stateMachines().get(0);
        
        assertEquals(3, sm.terminalStates().size());
        assertTrue(sm.terminalStates().contains("DELIVERED"));
        assertTrue(sm.terminalStates().contains("CANCELLED"));
        assertTrue(sm.terminalStates().contains("REFUNDED"));
    }

    @Test
    void parseStateMachineWithTraits() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                @description("Order lifecycle state machine")
                statemachine OrderLifecycle {
                    entity: Order
                    field: status
                    states: [PENDING, PAID]
                    initial: PENDING
                    terminal: [PAID]
                    transitions: [
                        PENDING -> PAID
                    ]
                }
                """);

        assertEquals(1, model.stateMachines().size());
        StateMachineDef sm = model.stateMachines().get(0);
        
        assertEquals(1, sm.traits().size());
        assertEquals("description", sm.traits().get(0).name());
    }
}

