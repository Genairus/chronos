package com.genairus.chronos.validator;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-048: Composition targets cannot be referenced by more than one composing entity.
 */
class Chr048CompositionSingleOwnerTest {

    @Test
    void validComposition_singleOwner() {
        var model = new ChronosCompiler().compile("""
                namespace com.example

                entity Order { id: String }
                entity OrderItem { id: String }

                relationship OrderItems {
                    from: Order
                    to: OrderItem
                    cardinality: one_to_many
                    semantics: composition
                }
                """, "test").modelOrNull();

        var result = new ChronosValidator().validate(model);
        assertFalse(result.hasErrors(), "Should be valid when composition has single owner");
    }

    @Test
    void validComposition_multipleAssociations() {
        var model = new ChronosCompiler().compile("""
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
                """, "test").modelOrNull();

        var result = new ChronosValidator().validate(model);
        assertFalse(result.hasErrors(), "Should be valid when multiple associations reference same target");
    }

    @Test
    void invalidComposition_multipleOwners() {
        var model = new ChronosCompiler().compile("""
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
                """, "test").modelOrNull();

        var result = new ChronosValidator().validate(model);
        assertTrue(result.hasErrors());
        assertEquals(1, result.errors().size());

        var error = result.errors().get(0);
        assertEquals("CHR-048", error.code());
        assertTrue(error.message().contains("LineItem"));
        assertTrue(error.message().contains("OrderItems"));
    }

    @Test
    void invalidComposition_threeOwners() {
        var model = new ChronosCompiler().compile("""
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
                """, "test").modelOrNull();

        var result = new ChronosValidator().validate(model);
        assertTrue(result.hasErrors());
        assertEquals(2, result.errors().size());

        assertTrue(result.errors().stream().allMatch(e -> e.code().equals("CHR-048")));
        assertTrue(result.errors().stream().allMatch(e -> e.message().contains("LineItem")));
    }

    @Test
    void validComposition_defaultSemanticsIsAssociation() {
        var model = new ChronosCompiler().compile("""
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
                """, "test").modelOrNull();

        var result = new ChronosValidator().validate(model);
        assertFalse(result.hasErrors(), "Should be valid when default semantics (association) is used");
    }
}
