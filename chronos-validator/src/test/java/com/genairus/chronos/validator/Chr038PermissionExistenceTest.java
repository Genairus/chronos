package com.genairus.chronos.validator;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-038: @authorize permission must be listed in the role's allow list.
 */
class Chr038PermissionExistenceTest {

    private static final String PREAMBLE = """
            namespace com.example
            role AdminRole {
                allow: [create, read]
            }
            actor SystemAdmin
            """;

    /** Permission exists in the role's allow list — valid. */
    @Test
    void permissionInAllowListPassesValidation() {
        var model = new ChronosCompiler().compile(PREAMBLE + """
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
        assertFalse(result.errors().stream().anyMatch(d -> "CHR-038".equals(d.code())),
                "Permission in allow list should not trigger CHR-038; got: " + result.errors());
    }

    /** Permission is not in the role's allow list — CHR-038 error. */
    @Test
    void permissionNotInAllowListTriggersChr038() {
        var model = new ChronosCompiler().compile(PREAMBLE + """
                @authorize(role: AdminRole, permission: delete)
                journey DeleteOrder {
                    actor: SystemAdmin
                    steps: [
                        step RemoveOrder {
                            action: "Remove order"
                            expectation: "Order removed"
                        }
                    ]
                    outcomes: { success: "Order deleted" }
                }
                """, "test").modelOrNull();

        var result = new ChronosValidator().validate(model);
        assertTrue(result.errors().stream().anyMatch(d -> "CHR-038".equals(d.code())),
                "Permission not in allow list should trigger CHR-038; got: " + result.errors());
        assertTrue(result.errors().stream()
                .filter(d -> "CHR-038".equals(d.code()))
                .anyMatch(d -> d.message().contains("delete")),
                "CHR-038 message should mention the invalid permission name");
    }

    /** No permission argument — CHR-038 does not fire (permission is optional). */
    @Test
    void authorizeWithoutPermissionArgDoesNotTriggerChr038() {
        var model = new ChronosCompiler().compile(PREAMBLE + """
                @authorize(role: AdminRole)
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
        assertFalse(result.errors().stream().anyMatch(d -> "CHR-038".equals(d.code())),
                "@authorize without permission should not trigger CHR-038; got: " + result.errors());
    }
}
