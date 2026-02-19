package com.genairus.chronos.validator;

import com.genairus.chronos.model.ChronosModel;
import com.genairus.chronos.parser.ChronosModelParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-012: Composition targets cannot be referenced by more than one composing entity.
 */
class Chr012CompositionSingleOwnerTest {

    @Test
    void validComposition_singleOwner() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity Order { id: String }
                entity OrderItem { id: String }
                
                relationship OrderItems {
                    from: Order
                    to: OrderItem
                    cardinality: one_to_many
                    semantics: composition
                }
                """);

        var result = new ChronosValidator().validate(model);
        assertFalse(result.hasErrors(), "Should be valid when composition has single owner");
    }

    @Test
    void validComposition_multipleAssociations() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity Order { id: String }
                entity Customer { id: String }
                entity OrderItem { id: String }
                
                relationship OrderItems {
                    from: Order
                    to: OrderItem
                    cardinality: one_to_many
                    semantics: association
                }
                
                relationship CustomerItems {
                    from: Customer
                    to: OrderItem
                    cardinality: one_to_many
                    semantics: association
                }
                """);

        var result = new ChronosValidator().validate(model);
        assertFalse(result.hasErrors(), "Should be valid when multiple associations reference same target");
    }

    @Test
    void invalidComposition_multipleOwners() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity Order { id: String }
                entity Invoice { id: String }
                entity LineItem { id: String }
                
                relationship OrderItems {
                    from: Order
                    to: LineItem
                    cardinality: one_to_many
                    semantics: composition
                }
                
                relationship InvoiceItems {
                    from: Invoice
                    to: LineItem
                    cardinality: one_to_many
                    semantics: composition
                }
                """);

        var result = new ChronosValidator().validate(model);
        assertTrue(result.hasErrors());
        assertEquals(1, result.errors().size());
        
        var error = result.errors().get(0);
        assertEquals("CHR-012", error.ruleCode());
        assertTrue(error.message().contains("LineItem"));
        assertTrue(error.message().contains("OrderItems"));
    }

    @Test
    void invalidComposition_threeOwners() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity Order { id: String }
                entity Invoice { id: String }
                entity Cart { id: String }
                entity LineItem { id: String }
                
                relationship OrderItems {
                    from: Order
                    to: LineItem
                    cardinality: one_to_many
                    semantics: composition
                }
                
                relationship InvoiceItems {
                    from: Invoice
                    to: LineItem
                    cardinality: one_to_many
                    semantics: composition
                }
                
                relationship CartItems {
                    from: Cart
                    to: LineItem
                    cardinality: one_to_many
                    semantics: composition
                }
                """);

        var result = new ChronosValidator().validate(model);
        assertTrue(result.hasErrors());
        assertEquals(2, result.errors().size());
        
        assertTrue(result.errors().stream().allMatch(e -> e.ruleCode().equals("CHR-012")));
        assertTrue(result.errors().stream().allMatch(e -> e.message().contains("LineItem")));
    }

    @Test
    void validComposition_defaultSemanticsIsAssociation() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity Order { id: String }
                entity Customer { id: String }
                entity OrderItem { id: String }
                
                relationship OrderItems {
                    from: Order
                    to: OrderItem
                    cardinality: one_to_many
                }
                
                relationship CustomerItems {
                    from: Customer
                    to: OrderItem
                    cardinality: one_to_many
                }
                """);

        var result = new ChronosValidator().validate(model);
        assertFalse(result.hasErrors(), "Should be valid when default semantics (association) is used");
    }
}

