package com.genairus.chronos.validator;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-W001: Invariant references optional field without null guard.
 */
class ChrW001OptionalFieldWarningTest {

    private final ChronosValidator validator = new ChronosValidator();

    @Test
    void entityInvariant_requiredField_noWarning() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity Order {
                    @required
                    total: Float
                    
                    invariant PositiveTotal {
                        expression: "total > 0"
                        severity: error
                    }
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chrW001Warnings = result.warnings().stream()
                .filter(w -> w.code().equals("CHR-W001"))
                .toList();
        
        assertEquals(0, chrW001Warnings.size());
    }

    @Test
    void entityInvariant_optionalFieldWithoutNullGuard_warning() {
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

        var result = validator.validate(model);
        var chrW001Warnings = result.warnings().stream()
                .filter(w -> w.code().equals("CHR-W001"))
                .toList();
        
        assertEquals(1, chrW001Warnings.size());
        assertTrue(chrW001Warnings.get(0).message().contains("PositiveTotal"));
        assertTrue(chrW001Warnings.get(0).message().contains("total"));
        assertTrue(chrW001Warnings.get(0).message().contains("null guard"));
    }

    @Test
    void entityInvariant_optionalFieldWithNullGuard_noWarning() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity Order {
                    total: Float
                    
                    invariant PositiveTotal {
                        expression: "total != null && total > 0"
                        severity: error
                    }
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chrW001Warnings = result.warnings().stream()
                .filter(w -> w.code().equals("CHR-W001"))
                .toList();
        
        assertEquals(0, chrW001Warnings.size());
    }

    @Test
    void entityInvariant_optionalFieldWithNullGuardReversed_noWarning() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity Order {
                    total: Float
                    
                    invariant PositiveTotal {
                        expression: "null != total && total > 0"
                        severity: error
                    }
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chrW001Warnings = result.warnings().stream()
                .filter(w -> w.code().equals("CHR-W001"))
                .toList();
        
        assertEquals(0, chrW001Warnings.size());
    }

    @Test
    void entityInvariant_multipleOptionalFields_multipleWarnings() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity Order {
                    total: Float
                    discount: Float
                    
                    invariant TotalGreaterThanDiscount {
                        expression: "total > discount"
                        severity: error
                    }
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chrW001Warnings = result.warnings().stream()
                .filter(w -> w.code().equals("CHR-W001"))
                .toList();
        
        assertEquals(2, chrW001Warnings.size());
        assertTrue(chrW001Warnings.stream().anyMatch(w -> w.message().contains("total")));
        assertTrue(chrW001Warnings.stream().anyMatch(w -> w.message().contains("discount")));
    }

    @Test
    void globalInvariant_optionalFieldWithoutNullGuard_warning() {
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
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chrW001Warnings = result.warnings().stream()
                .filter(w -> w.code().equals("CHR-W001"))
                .toList();

        // Only Order.customerId should warn - c.id is a lambda parameter reference
        assertEquals(1, chrW001Warnings.size());
        assertTrue(chrW001Warnings.get(0).message().contains("Order.customerId"));
    }

    @Test
    void globalInvariant_optionalFieldWithNullGuard_noWarning() {
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
                    expression: "Order.customerId != null && exists(Customer, c => c.id == Order.customerId)"
                    severity: error
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chrW001Warnings = result.warnings().stream()
                .filter(w -> w.code().equals("CHR-W001"))
                .toList();

        // No warnings - Order.customerId has a null guard, and c.id is a lambda parameter reference
        assertEquals(0, chrW001Warnings.size());
    }

    @Test
    void entityInvariant_inheritedOptionalField_warning() {
        var model = new ChronosCompiler().compile("""
                namespace com.example

                entity BaseOrder {
                    total: Float
                }

                entity PremiumOrder extends BaseOrder {
                    invariant PositiveTotal {
                        expression: "total > 0"
                        severity: error
                    }
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chrW001Warnings = result.warnings().stream()
                .filter(w -> w.code().equals("CHR-W001"))
                .toList();

        assertEquals(1, chrW001Warnings.size());
        assertTrue(chrW001Warnings.get(0).message().contains("total"));
    }

    @Test
    void entityInvariant_noOptionalFieldsReferenced_noWarning() {
        var model = new ChronosCompiler().compile("""
                namespace com.example

                entity Order {
                    @required
                    total: Float
                    discount: Float

                    invariant PositiveTotal {
                        expression: "total > 0"
                        severity: error
                    }
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chrW001Warnings = result.warnings().stream()
                .filter(w -> w.code().equals("CHR-W001"))
                .toList();

        // No warning because only 'total' is referenced and it's required
        assertEquals(0, chrW001Warnings.size());
    }
}

