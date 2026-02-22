package com.genairus.chronos.validator;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-040: @authorize permission must not be in the role's deny list.
 */
class Chr040DenyOverAllowTest {

    private static final String PREAMBLE = """
            namespace com.example
            role AdminRole {
                allow: [create, read, delete]
                deny:  [admin_delete]
            }
            actor SystemAdmin
            """;

    /** Permission is allowed and not denied — valid. */
    @Test
    void permissionAllowedNotDeniedPassesValidation() {
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
        assertFalse(result.errors().stream().anyMatch(d -> "CHR-040".equals(d.code())),
                "Non-denied permission should not trigger CHR-040; got: " + result.errors());
    }

    /** Permission is explicitly denied — CHR-040 error. */
    @Test
    void permissionInDenyListTriggersChr040() {
        var model = new ChronosCompiler().compile(PREAMBLE + """
                @authorize(role: AdminRole, permission: admin_delete)
                journey AdminDelete {
                    actor: SystemAdmin
                    steps: [
                        step DeleteRecord {
                            action: "Delete record"
                            expectation: "Record deleted"
                        }
                    ]
                    outcomes: { success: "Record removed" }
                }
                """, "test").modelOrNull();

        var result = new ChronosValidator().validate(model);
        assertTrue(result.errors().stream().anyMatch(d -> "CHR-040".equals(d.code())),
                "Denied permission should trigger CHR-040; got: " + result.errors());
        assertTrue(result.errors().stream()
                .filter(d -> "CHR-040".equals(d.code()))
                .anyMatch(d -> d.message().contains("admin_delete")),
                "CHR-040 message should mention the denied permission name");
    }

    /** Role with no deny list — CHR-040 never fires. */
    @Test
    void roleWithNoDenyListNeverTriggersChr040() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                role ReadOnlyRole {
                    allow: [read]
                }
                actor Viewer
                @authorize(role: ReadOnlyRole, permission: read)
                journey BrowseCatalog {
                    actor: Viewer
                    steps: [
                        step ListItems {
                            action: "List items"
                            expectation: "Items displayed"
                        }
                    ]
                    outcomes: { success: "Catalog shown" }
                }
                """, "test").modelOrNull();

        var result = new ChronosValidator().validate(model);
        assertFalse(result.errors().stream().anyMatch(d -> "CHR-040".equals(d.code())),
                "Role with no deny list should not trigger CHR-040; got: " + result.errors());
    }
}
