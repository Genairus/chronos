package com.genairus.chronos.validator;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-041: Step telemetry event must be a declared or imported event type.
 */
class Chr041UnknownTelemetryEventTest {

    private static final String JOURNEY_TEMPLATE = """
            namespace com.example
            actor Customer
            %s
            journey PlaceOrder {
                actor: Customer
                steps: [
                    step Checkout {
                        action: "Submit order"
                        expectation: "Order confirmed"
                        telemetry: [OrderSubmitted]
                    }
                ]
                outcomes: { success: "Order placed" }
            }
            """;

    /** Telemetry references a locally declared event — valid, no CHR-041. */
    @Test
    void telemetryWithDeclaredEventPassesValidation() {
        var model = new ChronosCompiler().compile(
                JOURNEY_TEMPLATE.formatted("event OrderSubmitted {}"),
                "test").modelOrNull();

        var result = new ChronosValidator().validate(model);
        assertFalse(result.diagnostics().stream().anyMatch(d -> "CHR-041".equals(d.code())),
                "Declared event should not trigger CHR-041; got: " + result.diagnostics());
    }

    /** Telemetry references an undeclared event — CHR-041 error emitted. */
    @Test
    void telemetryWithUndeclaredEventTriggersChr041() {
        // No event declaration — OrderSubmitted is not declared
        var model = new ChronosCompiler().compile(
                JOURNEY_TEMPLATE.formatted(""),
                "test").modelOrNull();

        var result = new ChronosValidator().validate(model);
        assertTrue(result.errors().stream().anyMatch(d -> "CHR-041".equals(d.code())),
                "Undeclared telemetry event should trigger CHR-041; got: " + result.diagnostics());
        assertTrue(result.errors().stream()
                .filter(d -> "CHR-041".equals(d.code()))
                .anyMatch(d -> d.message().contains("OrderSubmitted")),
                "CHR-041 message should contain the event name");
    }

    /** Telemetry references an event name present in model imports — valid, no CHR-041. */
    @Test
    void telemetryWithImportedEventNamePassesValidation() {
        // Import brings the name "OrderSubmitted" into scope via use
        var model = new ChronosCompiler().compile("""
                namespace com.example
                use com.example.events#OrderSubmitted
                actor Customer
                journey PlaceOrder {
                    actor: Customer
                    steps: [
                        step Checkout {
                            action: "Submit order"
                            expectation: "Order confirmed"
                            telemetry: [OrderSubmitted]
                        }
                    ]
                    outcomes: { success: "Order placed" }
                }
                """, "test").modelOrNull();

        var result = new ChronosValidator().validate(model);
        assertFalse(result.errors().stream().anyMatch(d -> "CHR-041".equals(d.code())),
                "Imported event name should not trigger CHR-041; got: " + result.diagnostics());
    }
}
