package com.genairus.chronos.validator;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-039: Journey actor must carry @authorize(role: X) matching the journey's required role.
 */
class Chr039ActorCompatibilityTest {

    private static final String PREAMBLE = """
            namespace com.example
            role AdminRole {
                allow: [create, read]
            }
            """;

    /** Actor has matching @authorize(role: X) for the journey's required role — valid. */
    @Test
    void actorHasMatchingRolePassesValidation() {
        var model = new ChronosCompiler().compile(PREAMBLE + """
                @authorize(role: AdminRole)
                actor SystemAdmin
                @authorize(role: AdminRole, permission: create)
                journey CreateOrder {
                    actor: SystemAdmin
                    steps: [
                        step PlaceOrder {
                            action: "Place order"
                            expectation: "Order created"
                        }
                    ]
                    outcomes: { success: "Order placed" }
                }
                """, "test").modelOrNull();

        var result = new ChronosValidator().validate(model);
        assertFalse(result.errors().stream().anyMatch(d -> "CHR-039".equals(d.code())),
                "Actor with matching role should not trigger CHR-039; got: " + result.errors());
    }

    /** Actor lacks @authorize(role: X) for the journey's required role — CHR-039 error. */
    @Test
    void actorMissingRoleTriggersChr039() {
        var model = new ChronosCompiler().compile(PREAMBLE + """
                actor SystemAdmin
                @authorize(role: AdminRole, permission: create)
                journey CreateOrder {
                    actor: SystemAdmin
                    steps: [
                        step PlaceOrder {
                            action: "Place order"
                            expectation: "Order created"
                        }
                    ]
                    outcomes: { success: "Order placed" }
                }
                """, "test").modelOrNull();

        var result = new ChronosValidator().validate(model);
        assertTrue(result.errors().stream().anyMatch(d -> "CHR-039".equals(d.code())),
                "Actor without required role should trigger CHR-039; got: " + result.errors());
        assertTrue(result.errors().stream()
                .filter(d -> "CHR-039".equals(d.code()))
                .anyMatch(d -> d.message().contains("AdminRole")),
                "CHR-039 message should mention the required role name");
    }

    /** Journey without @authorize — CHR-039 does not apply. */
    @Test
    void journeyWithoutAuthorizeTraitDoesNotTriggerChr039() {
        var model = new ChronosCompiler().compile(PREAMBLE + """
                actor SystemAdmin
                journey ReadOrders {
                    actor: SystemAdmin
                    steps: [
                        step FetchOrders {
                            action: "Fetch orders"
                            expectation: "Orders listed"
                        }
                    ]
                    outcomes: { success: "Orders shown" }
                }
                """, "test").modelOrNull();

        var result = new ChronosValidator().validate(model);
        assertFalse(result.errors().stream().anyMatch(d -> "CHR-039".equals(d.code())),
                "Journey without @authorize should not trigger CHR-039; got: " + result.errors());
    }
}
