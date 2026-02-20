package com.genairus.chronos.generators;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for invariant documentation and test scaffolding generation.
 */
class InvariantGeneratorTest {

    @Test
    void markdownGenerator_entityWithInvariants_includesInvariantSection() {
        var model = new ChronosCompiler().compile("""
            namespace com.example

            entity Order {
                total: Float

                invariant PositiveTotal {
                    expression: "total > 0"
                    severity: error
                    message: "Order total must be positive"
                }

                invariant ReasonableTotal {
                    expression: "total < 1000000"
                    severity: warning
                }
            }
            """, "test").modelOrNull();

        var generator = new MarkdownPrdGenerator();
        var output = generator.generate(model);

        assertEquals(1, output.files().size());
        String content = output.files().get("com-example-prd.md");

        // Check that invariants section is present
        assertTrue(content.contains("**Invariants:**"), "Should have invariants section");
        assertTrue(content.contains("**PositiveTotal**"), "Should include first invariant name");
        assertTrue(content.contains("(error)"), "Should include severity");
        assertTrue(content.contains("`total > 0`"), "Should include expression");
        assertTrue(content.contains("Order total must be positive"), "Should include message");

        assertTrue(content.contains("**ReasonableTotal**"), "Should include second invariant name");
        assertTrue(content.contains("(warning)"), "Should include warning severity");
        assertTrue(content.contains("`total < 1000000`"), "Should include second expression");
    }

    @Test
    void markdownGenerator_globalInvariants_createsGlobalInvariantsSection() {
        var model = new ChronosCompiler().compile("""
            namespace com.example

            entity Order {
                customerId: String
            }

            entity Customer {
                id: String
            }

            /// Ensures every order references an existing customer
            invariant OrderCustomerExists {
                scope: [Order, Customer]
                expression: "exists(Customer, c => c.id == Order.customerId)"
                severity: error
                message: "Every order must reference an existing customer"
            }
            """, "test").modelOrNull();

        var generator = new MarkdownPrdGenerator();
        var output = generator.generate(model);

        String content = output.files().get("com-example-prd.md");

        // Check that global invariants section is present
        assertTrue(content.contains("## Global Invariants"), "Should have global invariants section");
        assertTrue(content.contains("### OrderCustomerExists"), "Should include invariant name as heading");
        assertTrue(content.contains("**Scope:** Order, Customer"), "Should include scope");
        assertTrue(content.contains("**Expression:** `exists(Customer, c => c.id == Order.customerId)`"), "Should include expression");
        assertTrue(content.contains("**Severity:** error"), "Should include severity");
        assertTrue(content.contains("**Message:** Every order must reference an existing customer"), "Should include message");
        assertTrue(content.contains("> Ensures every order references an existing customer"), "Should include doc comment");
    }

    @Test
    void testScaffoldGenerator_entityInvariants_generatesTestStubs() {
        var model = new ChronosCompiler().compile("""
            namespace com.example

            entity Order {
                total: Float

                invariant PositiveTotal {
                    expression: "total > 0"
                    severity: error
                }
            }
            """, "test").modelOrNull();

        var generator = new TestScaffoldGenerator();
        var output = generator.generate(model);

        assertEquals(1, output.files().size());
        String content = output.files().get("ComExampleInvariantTests.java");

        // Check package and imports
        assertTrue(content.contains("package com.example.tests;"), "Should have correct package");
        assertTrue(content.contains("import org.junit.jupiter.api.Test;"), "Should import Test annotation");
        assertTrue(content.contains("import static org.junit.jupiter.api.Assertions.*;"), "Should import assertions");

        // Check class declaration
        assertTrue(content.contains("public class ComExampleInvariantTests"), "Should have correct class name");

        // Check test method
        assertTrue(content.contains("@Test"), "Should have Test annotation");
        assertTrue(content.contains("void testOrder_PositiveTotal()"), "Should have correct test method name");
        assertTrue(content.contains("// Expression: total > 0"), "Should include expression in comment");
        assertTrue(content.contains("// Severity: error"), "Should include severity in comment");
        assertTrue(content.contains("var order = createOrder();"), "Should include example code");
        assertTrue(content.contains("fail(\"Test not yet implemented\")"), "Should have fail placeholder");
    }

    @Test
    void testScaffoldGenerator_globalInvariants_generatesTestStubs() {
        var model = new ChronosCompiler().compile("""
            namespace com.example

            entity Order {
                customerId: String
            }

            entity Customer {
                id: String
            }

            invariant OrderCustomerExists {
                scope: [Order, Customer]
                expression: "exists(Customer, c => c.id == Order.customerId)"
                severity: error
                message: "Every order must reference an existing customer"
            }
            """, "test").modelOrNull();

        var generator = new TestScaffoldGenerator();
        var output = generator.generate(model);

        String content = output.files().get("ComExampleInvariantTests.java");

        // Check global invariant test
        assertTrue(content.contains("// ── Global Invariants ──"), "Should have global invariants section");
        assertTrue(content.contains("void testGlobal_OrderCustomerExists()"), "Should have correct test method name");
        assertTrue(content.contains("// Scope: Order, Customer"), "Should include scope in comment");
        assertTrue(content.contains("// Expression: exists(Customer, c => c.id == Order.customerId)"), "Should include expression");
        assertTrue(content.contains("// Message: Every order must reference an existing customer"), "Should include message");
        assertTrue(content.contains("var order = createOrder();"), "Should include example for Order");
        assertTrue(content.contains("var customer = createCustomer();"), "Should include example for Customer");
    }

    @Test
    void testScaffoldGenerator_denyBlocks_generatesNegativeTestStubs() {
        var model = new ChronosCompiler().compile("""
            namespace com.example

            entity UserCredential {
                username: String
                password: String
            }

            @compliance("PCI-DSS")
            deny StorePlaintextPasswords {
                description: "The system must never store passwords in plaintext"
                scope: [UserCredential]
                severity: critical
            }

            deny ExposePIIInLogs {
                description: "PII must never appear in logs"
                scope: [UserCredential]
                severity: high
            }
            """, "test").modelOrNull();

        var generator = new TestScaffoldGenerator();
        var output = generator.generate(model);

        String content = output.files().get("ComExampleInvariantTests.java");

        // Check deny section header
        assertTrue(content.contains("// ── Deny Blocks (Negative Requirements)"), "Should have deny section");

        // Check first deny test
        assertTrue(content.contains("void testDeny_StorePlaintextPasswords()"), "Should have test method for first deny");
        assertTrue(content.contains("// Description: The system must never store passwords in plaintext"), "Should include description");
        assertTrue(content.contains("// Scope: UserCredential"), "Should include scope");
        assertTrue(content.contains("// Severity: critical"), "Should include severity");
        assertTrue(content.contains("// Compliance: compliance"), "Should include compliance trait");
        assertTrue(content.contains("// This is a NEGATIVE test"), "Should indicate negative test");
        assertTrue(content.contains("var userCredential = createUserCredential();"), "Should include example for UserCredential");
        assertTrue(content.contains("assertFalse(/* verify prohibited condition does NOT occur */);"), "Should include assertFalse example");
        assertTrue(content.contains("assertThrows(/* verify operation is prevented */);"), "Should include assertThrows example");

        // Check second deny test
        assertTrue(content.contains("void testDeny_ExposePIIInLogs()"), "Should have test method for second deny");
        assertTrue(content.contains("// Description: PII must never appear in logs"), "Should include second description");
        assertTrue(content.contains("// Severity: high"), "Should include high severity");
    }
}
