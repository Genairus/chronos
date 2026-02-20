package com.genairus.chronos.validator;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-019: Invariant expressions must reference only fields visible in scope.
 */
class Chr019FieldReferenceValidationTest {

    private final ChronosValidator validator = new ChronosValidator();

    @Test
    void entityInvariant_validFieldReference() {
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
        var chr019Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-019"))
                .toList();
        
        assertEquals(0, chr019Errors.size());
    }

    @Test
    void entityInvariant_multipleValidFieldReferences() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity Order {
                    total: Float
                    discount: Float
                    
                    invariant ValidDiscount {
                        expression: "discount >= 0 && discount <= total"
                        severity: error
                    }
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chr019Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-019"))
                .toList();
        
        assertEquals(0, chr019Errors.size());
    }

    @Test
    void entityInvariant_invalidFieldReference() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity Order {
                    total: Float
                    
                    invariant CheckQuantity {
                        expression: "quantity > 0"
                        severity: error
                    }
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chr019Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-019"))
                .toList();
        
        assertEquals(1, chr019Errors.size());
        assertTrue(chr019Errors.get(0).message().contains("quantity"));
        assertTrue(chr019Errors.get(0).message().contains("CheckQuantity"));
    }

    @Test
    void entityInvariant_withInheritedFields() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity BaseOrder {
                    id: String
                    total: Float
                }
                
                entity PremiumOrder extends BaseOrder {
                    discount: Float
                    
                    invariant ValidDiscount {
                        expression: "discount <= total"
                        severity: error
                    }
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chr019Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-019"))
                .toList();
        
        // Should be valid because 'total' is inherited from BaseOrder
        assertEquals(0, chr019Errors.size());
    }

    @Test
    void globalInvariant_validFieldReferences() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity Order {
                    customerId: String
                    total: Float
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
        var chr019Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-019"))
                .toList();
        
        assertEquals(0, chr019Errors.size());
    }

    @Test
    void globalInvariant_invalidFieldReference() {
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
                    expression: "exists(Customer, c => c.id == Order.invalidField)"
                    severity: error
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);

        var chr019Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-019"))
                .toList();

        // Should have error for 'invalidField' which doesn't exist in Order
        assertTrue(chr019Errors.size() > 0);
        assertTrue(chr019Errors.stream().anyMatch(e -> e.message().contains("invalidField")));
    }
}

