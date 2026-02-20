package com.genairus.chronos.validator;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-021: Global invariants must declare a scope listing all referenced entities.
 */
class Chr021GlobalInvariantScopeTest {

    private final ChronosValidator validator = new ChronosValidator();

    @Test
    void globalInvariant_withValidScope() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity Order { customerId: String }
                entity Customer { id: String }
                
                invariant OrderCustomerExists {
                    scope: [Order, Customer]
                    expression: "exists(Customer, c => c.id == Order.customerId)"
                    severity: error
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chr021Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-021"))
                .toList();
        
        assertEquals(0, chr021Errors.size());
    }

    @Test
    void globalInvariant_withSingleEntityScope() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity Order { total: Float }
                
                invariant AllOrdersPositive {
                    scope: [Order]
                    expression: "forAll(Order, o => o.total > 0)"
                    severity: error
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chr021Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-021"))
                .toList();
        
        assertEquals(0, chr021Errors.size());
    }

    @Test
    void globalInvariant_withUndefinedEntityInScope() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity Order { customerId: String }
                
                invariant OrderCustomerExists {
                    scope: [Order, Customer]
                    expression: "exists(Customer, c => c.id == Order.customerId)"
                    severity: error
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chr021Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-021"))
                .toList();
        
        assertEquals(1, chr021Errors.size());
        assertTrue(chr021Errors.get(0).message().contains("OrderCustomerExists"));
        assertTrue(chr021Errors.get(0).message().contains("Customer"));
        assertTrue(chr021Errors.get(0).message().contains("undefined entity"));
    }

    @Test
    void globalInvariant_withMultipleUndefinedEntities() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                invariant ComplexRule {
                    scope: [Order, Customer, Product]
                    expression: "count(Order) <= count(Customer) * 10"
                    severity: error
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chr021Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-021"))
                .toList();
        
        // Should have 3 errors: one for each undefined entity
        assertEquals(3, chr021Errors.size());
        assertTrue(chr021Errors.stream().anyMatch(e -> e.message().contains("Order")));
        assertTrue(chr021Errors.stream().anyMatch(e -> e.message().contains("Customer")));
        assertTrue(chr021Errors.stream().anyMatch(e -> e.message().contains("Product")));
    }

    @Test
    void globalInvariant_withMixedDefinedAndUndefined() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity Order { total: Float }
                
                invariant MixedRule {
                    scope: [Order, Customer]
                    expression: "count(Order) > 0"
                    severity: error
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chr021Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-021"))
                .toList();
        
        // Should have 1 error for Customer
        assertEquals(1, chr021Errors.size());
        assertTrue(chr021Errors.get(0).message().contains("Customer"));
        assertFalse(chr021Errors.get(0).message().contains("Order"));
    }
}

