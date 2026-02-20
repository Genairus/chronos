package com.genairus.chronos.validator;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-022: Invariant names must be unique within their enclosing scope.
 */
class Chr022UniqueInvariantNamesTest {

    private final ChronosValidator validator = new ChronosValidator();

    @Test
    void entityInvariants_uniqueNames() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity Order {
                    total: Float
                    discount: Float
                    
                    invariant PositiveTotal {
                        expression: "total > 0"
                        severity: error
                    }
                    
                    invariant ValidDiscount {
                        expression: "discount >= 0"
                        severity: error
                    }
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chr022Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-022"))
                .toList();
        
        assertEquals(0, chr022Errors.size());
    }

    @Test
    void entityInvariants_duplicateNames() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity Order {
                    total: Float
                    discount: Float
                    
                    invariant PositiveValue {
                        expression: "total > 0"
                        severity: error
                    }
                    
                    invariant PositiveValue {
                        expression: "discount >= 0"
                        severity: error
                    }
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chr022Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-022"))
                .toList();
        
        // Should have 2 errors: one for each occurrence
        assertEquals(2, chr022Errors.size());
        assertTrue(chr022Errors.get(0).message().contains("PositiveValue"));
        assertTrue(chr022Errors.get(0).message().contains("Order"));
        assertTrue(chr022Errors.get(1).message().contains("PositiveValue"));
    }

    @Test
    void entityInvariants_sameNameInDifferentEntities() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity Order {
                    total: Float
                    
                    invariant PositiveValue {
                        expression: "total > 0"
                        severity: error
                    }
                }
                
                entity Invoice {
                    amount: Float
                    
                    invariant PositiveValue {
                        expression: "amount > 0"
                        severity: error
                    }
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chr022Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-022"))
                .toList();
        
        // Should have 0 errors: same name in different entities is allowed
        assertEquals(0, chr022Errors.size());
    }

    @Test
    void globalInvariants_uniqueNames() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity Order { total: Float }
                entity Customer { id: String }
                
                invariant Rule1 {
                    scope: [Order]
                    expression: "count(Order) > 0"
                    severity: error
                }
                
                invariant Rule2 {
                    scope: [Customer]
                    expression: "count(Customer) > 0"
                    severity: error
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chr022Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-022"))
                .toList();
        
        assertEquals(0, chr022Errors.size());
    }

    @Test
    void globalInvariants_duplicateNames() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity Order { total: Float }
                
                invariant CountRule {
                    scope: [Order]
                    expression: "count(Order) > 0"
                    severity: error
                }
                
                invariant CountRule {
                    scope: [Order]
                    expression: "count(Order) < 1000"
                    severity: warning
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chr022Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-022"))
                .toList();
        
        // Should have 2 errors: one for each occurrence
        assertEquals(2, chr022Errors.size());
        assertTrue(chr022Errors.get(0).message().contains("CountRule"));
        assertTrue(chr022Errors.get(1).message().contains("CountRule"));
    }
}

