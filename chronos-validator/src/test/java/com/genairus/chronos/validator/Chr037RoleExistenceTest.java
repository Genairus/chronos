package com.genairus.chronos.validator;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-037: @authorize role name must reference a declared role in the model.
 */
class Chr037RoleExistenceTest {

    private static final String PREAMBLE = """
            namespace com.example
            role AdminRole {
                allow: [create, read]
            }
            """;

    /** @authorize on a journey references a declared role — valid. */
    @Test
    void authorizeWithDeclaredRolePassesValidation() {
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
        assertFalse(result.errors().stream().anyMatch(d -> "CHR-037".equals(d.code())),
                "Declared role should not trigger CHR-037; got: " + result.errors());
    }

    /** @authorize on a journey references an undeclared role — CHR-037 error. */
    @Test
    void authorizeWithUndeclaredRoleOnJourneyTriggersChr037() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                actor SystemAdmin
                @authorize(role: NonExistentRole, permission: create)
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
        assertTrue(result.errors().stream().anyMatch(d -> "CHR-037".equals(d.code())),
                "Undeclared role on journey should trigger CHR-037; got: " + result.errors());
        assertTrue(result.errors().stream()
                .filter(d -> "CHR-037".equals(d.code()))
                .anyMatch(d -> d.message().contains("NonExistentRole")),
                "CHR-037 message should mention the undefined role name");
    }

    /** @authorize on an actor references an undeclared role — CHR-037 error. */
    @Test
    void authorizeWithUndeclaredRoleOnActorTriggersChr037() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                @authorize(role: GhostRole)
                actor SystemAdmin
                """, "test").modelOrNull();

        var result = new ChronosValidator().validate(model);
        assertTrue(result.errors().stream().anyMatch(d -> "CHR-037".equals(d.code())),
                "Undeclared role on actor should trigger CHR-037; got: " + result.errors());
        assertTrue(result.errors().stream()
                .filter(d -> "CHR-037".equals(d.code()))
                .anyMatch(d -> d.message().contains("GhostRole")),
                "CHR-037 message should mention the undefined role name");
    }
}
