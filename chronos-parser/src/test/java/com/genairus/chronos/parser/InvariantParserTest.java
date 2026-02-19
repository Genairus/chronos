package com.genairus.chronos.parser;

import com.genairus.chronos.model.ChronosModel;
import com.genairus.chronos.model.EntityDef;
import com.genairus.chronos.model.EntityInvariant;
import com.genairus.chronos.model.InvariantDef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InvariantParserTest {

    private ChronosModel parse(String source) {
        return ChronosModelParser.parseString("test", source);
    }

    // ── Entity-scoped invariants ──────────────────────────────────────────────

    @Test
    void entityInvariant_minimal() {
        var model = parse("""
                namespace com.example
                
                entity Order {
                    total: Float
                    
                    invariant PositiveTotal {
                        expression: "total > 0"
                        severity: error
                    }
                }
                """);

        var entity = model.entities().get(0);
        assertEquals(1, entity.invariants().size());
        
        var inv = entity.invariants().get(0);
        assertEquals("PositiveTotal", inv.name());
        assertEquals("total > 0", inv.expression());
        assertEquals("error", inv.severity());
        assertTrue(inv.message().isEmpty());
    }

    @Test
    void entityInvariant_withMessage() {
        var model = parse("""
                namespace com.example
                
                entity Order {
                    total: Float
                    
                    invariant PositiveTotal {
                        expression: "total > 0"
                        severity: warning
                        message: "Order total must be positive"
                    }
                }
                """);

        var entity = model.entities().get(0);
        var inv = entity.invariants().get(0);
        assertEquals("PositiveTotal", inv.name());
        assertEquals("total > 0", inv.expression());
        assertEquals("warning", inv.severity());
        assertEquals("Order total must be positive", inv.message().get());
    }

    @Test
    void entityInvariant_multipleInvariants() {
        var model = parse("""
                namespace com.example
                
                entity Order {
                    total: Float
                    discount: Float
                    
                    invariant PositiveTotal {
                        expression: "total > 0"
                        severity: error
                    }
                    
                    invariant ValidDiscount {
                        expression: "discount >= 0 && discount <= total"
                        severity: error
                        message: "Discount must be between 0 and total"
                    }
                }
                """);

        var entity = model.entities().get(0);
        assertEquals(2, entity.invariants().size());
        
        assertEquals("PositiveTotal", entity.invariants().get(0).name());
        assertEquals("ValidDiscount", entity.invariants().get(1).name());
    }

    @Test
    void entityInvariant_mixedWithFields() {
        var model = parse("""
                namespace com.example
                
                entity Order {
                    id: String
                    
                    invariant HasId {
                        expression: "id != null"
                        severity: error
                    }
                    
                    total: Float
                    
                    invariant PositiveTotal {
                        expression: "total > 0"
                        severity: error
                    }
                }
                """);

        var entity = model.entities().get(0);
        assertEquals(2, entity.fields().size());
        assertEquals(2, entity.invariants().size());
        
        assertEquals("id", entity.fields().get(0).name());
        assertEquals("total", entity.fields().get(1).name());
        assertEquals("HasId", entity.invariants().get(0).name());
        assertEquals("PositiveTotal", entity.invariants().get(1).name());
    }

    // ── Global invariants ─────────────────────────────────────────────────────

    @Test
    void globalInvariant_minimal() {
        var model = parse("""
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
                """);

        assertEquals(1, model.invariants().size());
        
        var inv = model.invariants().get(0);
        assertEquals("OrderCustomerExists", inv.name());
        assertEquals(2, inv.scope().size());
        assertTrue(inv.scope().contains("Order"));
        assertTrue(inv.scope().contains("Customer"));
        assertEquals("exists(Customer, c => c.id == Order.customerId)", inv.expression());
        assertEquals("error", inv.severity());
        assertTrue(inv.message().isEmpty());
    }

    @Test
    void globalInvariant_withMessage() {
        var model = parse("""
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
                """);

        var inv = model.invariants().get(0);
        assertEquals("Every order must reference an existing customer", inv.message().get());
    }

    @Test
    void globalInvariant_singleEntityScope() {
        var model = parse("""
                namespace com.example

                entity Order {
                    total: Float
                }

                invariant AllOrdersPositive {
                    scope: [Order]
                    expression: "forAll(Order, o => o.total > 0)"
                    severity: warning
                }
                """);

        var inv = model.invariants().get(0);
        assertEquals(1, inv.scope().size());
        assertEquals("Order", inv.scope().get(0));
    }

    @Test
    void globalInvariant_multipleScopes() {
        var model = parse("""
                namespace com.example

                entity Order { customerId: String }
                entity Customer { id: String }
                entity Product { id: String }

                invariant ComplexRule {
                    scope: [Order, Customer, Product]
                    expression: "count(Order) <= count(Customer) * 10"
                    severity: info
                    message: "Orders should not exceed 10x customer count"
                }
                """);

        var inv = model.invariants().get(0);
        assertEquals(3, inv.scope().size());
        assertEquals("Order", inv.scope().get(0));
        assertEquals("Customer", inv.scope().get(1));
        assertEquals("Product", inv.scope().get(2));
    }

    @Test
    void globalInvariant_withTraitsAndDocComments() {
        var model = parse("""
                namespace com.example

                entity Order { total: Float }

                /// This is a business rule
                /// that ensures data quality
                @compliance("SOX")
                @description("Revenue validation")
                invariant RevenueRule {
                    scope: [Order]
                    expression: "sum(Order, o => o.total) > 0"
                    severity: error
                }
                """);

        var inv = model.invariants().get(0);
        assertEquals("RevenueRule", inv.name());
        assertEquals(2, inv.traits().size());
        assertEquals("compliance", inv.traits().get(0).name());
        assertEquals("description", inv.traits().get(1).name());
        assertEquals(2, inv.docComments().size());
        assertTrue(inv.docComments().get(0).contains("business rule"));
    }

    @Test
    void mixedEntityAndGlobalInvariants() {
        var model = parse("""
                namespace com.example

                entity Order {
                    total: Float
                    customerId: String

                    invariant PositiveTotal {
                        expression: "total > 0"
                        severity: error
                    }
                }

                entity Customer {
                    id: String
                }

                invariant OrderCustomerExists {
                    scope: [Order, Customer]
                    expression: "exists(Customer, c => c.id == Order.customerId)"
                    severity: error
                }
                """);

        assertEquals(2, model.entities().size());
        assertEquals(1, model.entities().get(0).invariants().size());
        assertEquals(0, model.entities().get(1).invariants().size());
        assertEquals(1, model.invariants().size());
    }

    @Test
    void invariantFieldsCanBeInAnyOrder() {
        var model = parse("""
                namespace com.example

                entity Order { total: Float }

                invariant Rule1 {
                    message: "Must be positive"
                    severity: error
                    expression: "total > 0"
                    scope: [Order]
                }
                """);

        var inv = model.invariants().get(0);
        assertEquals("Rule1", inv.name());
        assertEquals("total > 0", inv.expression());
        assertEquals("error", inv.severity());
        assertEquals("Must be positive", inv.message().get());
    }
}
