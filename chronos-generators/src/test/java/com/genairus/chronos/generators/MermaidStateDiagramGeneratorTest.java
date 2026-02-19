package com.genairus.chronos.generators;

import com.genairus.chronos.model.ChronosModel;
import com.genairus.chronos.parser.ChronosModelParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MermaidStateDiagramGeneratorTest {

    @Test
    void generateSimpleStateDiagram() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity Order {
                    status: OrderStatus
                }
                
                enum OrderStatus {
                    PENDING
                    PAID
                    SHIPPED
                }
                
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

        GeneratorOutput output = new MermaidStateDiagramGenerator().generate(model);
        
        assertEquals(1, output.files().size());
        assertTrue(output.files().containsKey("OrderLifecycle.mmd"));
        
        String diagram = output.files().get("OrderLifecycle.mmd");
        
        // Check header
        assertTrue(diagram.contains("stateDiagram-v2"));
        
        // Check note with entity and field
        assertTrue(diagram.contains("note right of PENDING : Order.status"));
        
        // Check initial state
        assertTrue(diagram.contains("[*] --> PENDING"));
        
        // Check transitions
        assertTrue(diagram.contains("PENDING --> PAID"));
        assertTrue(diagram.contains("PAID --> SHIPPED"));
        
        // Check terminal state
        assertTrue(diagram.contains("SHIPPED --> [*]"));
    }

    @Test
    void generateStateDiagramWithGuardsAndActions() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity Order {
                    status: OrderStatus
                }
                
                enum OrderStatus {
                    PENDING
                    PAID
                    SHIPPED
                    DELIVERED
                }
                
                statemachine OrderLifecycle {
                    entity: Order
                    field: status
                    states: [PENDING, PAID, SHIPPED, DELIVERED]
                    initial: PENDING
                    terminal: [DELIVERED]
                    transitions: [
                        PENDING -> PAID {
                            guard: "payment received"
                            action: "Emit PaymentReceivedEvent"
                        },
                        PAID -> SHIPPED {
                            guard: "fulfillment dispatched"
                        },
                        SHIPPED -> DELIVERED {
                            action: "Notify customer"
                        }
                    ]
                }
                """);

        GeneratorOutput output = new MermaidStateDiagramGenerator().generate(model);
        String diagram = output.files().get("OrderLifecycle.mmd");
        
        // Check transition with guard and action
        assertTrue(diagram.contains("PENDING --> PAID : payment received / Emit PaymentReceivedEvent"));
        
        // Check transition with guard only
        assertTrue(diagram.contains("PAID --> SHIPPED : fulfillment dispatched"));
        
        // Check transition with action only
        assertTrue(diagram.contains("SHIPPED --> DELIVERED : Notify customer"));
    }

    @Test
    void generateMultipleStateDiagrams() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity Order {
                    status: OrderStatus
                }
                
                entity Payment {
                    status: PaymentStatus
                }
                
                enum OrderStatus {
                    PENDING
                    COMPLETED
                }
                
                enum PaymentStatus {
                    UNPAID
                    PAID
                }
                
                statemachine OrderLifecycle {
                    entity: Order
                    field: status
                    states: [PENDING, COMPLETED]
                    initial: PENDING
                    terminal: [COMPLETED]
                    transitions: [
                        PENDING -> COMPLETED
                    ]
                }
                
                statemachine PaymentLifecycle {
                    entity: Payment
                    field: status
                    states: [UNPAID, PAID]
                    initial: UNPAID
                    terminal: [PAID]
                    transitions: [
                        UNPAID -> PAID
                    ]
                }
                """);

        GeneratorOutput output = new MermaidStateDiagramGenerator().generate(model);
        
        assertEquals(2, output.files().size());
        assertTrue(output.files().containsKey("OrderLifecycle.mmd"));
        assertTrue(output.files().containsKey("PaymentLifecycle.mmd"));
    }
}

