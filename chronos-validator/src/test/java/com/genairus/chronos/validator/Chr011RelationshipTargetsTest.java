package com.genairus.chronos.validator;

import com.genairus.chronos.model.ChronosModel;
import com.genairus.chronos.parser.ChronosModelParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-011: Relationship targets must reference defined or imported entities.
 */
class Chr011RelationshipTargetsTest {

    @Test
    void validRelationship_bothEntitiesDefined() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                entity Order { id: String }
                entity OrderItem { id: String }

                relationship OrderItems {
                    from: Order
                    to: OrderItem
                    cardinality: one_to_many
                }
                """);

        var result = new ChronosValidator().validate(model);
        assertFalse(result.hasErrors(), "Should be valid when both entities are defined");
    }

    @Test
    void invalidRelationship_fromEntityUndefined() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                entity OrderItem { id: String }

                relationship OrderItems {
                    from: Order
                    to: OrderItem
                    cardinality: one_to_many
                }
                """);

        var result = new ChronosValidator().validate(model);
        assertTrue(result.hasErrors());
        assertEquals(1, result.errors().size());

        var error = result.errors().get(0);
        assertEquals("CHR-011", error.ruleCode());
        assertTrue(error.message().contains("Order"));
        assertTrue(error.message().contains("from"));
    }

    @Test
    void invalidRelationship_toEntityUndefined() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                entity Order { id: String }

                relationship OrderItems {
                    from: Order
                    to: OrderItem
                    cardinality: one_to_many
                }
                """);

        var result = new ChronosValidator().validate(model);
        assertTrue(result.hasErrors());
        assertEquals(1, result.errors().size());

        var error = result.errors().get(0);
        assertEquals("CHR-011", error.ruleCode());
        assertTrue(error.message().contains("OrderItem"));
        assertTrue(error.message().contains("to"));
    }

    @Test
    void invalidRelationship_bothEntitiesUndefined() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                relationship OrderItems {
                    from: Order
                    to: OrderItem
                    cardinality: one_to_many
                }
                """);

        var result = new ChronosValidator().validate(model);
        assertTrue(result.hasErrors());
        assertEquals(2, result.errors().size());

        assertTrue(result.errors().stream().allMatch(e -> e.ruleCode().equals("CHR-011")));
    }

    @Test
    void validRelationship_importedEntity() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                use com.other#Order

                entity OrderItem { id: String }

                relationship OrderItems {
                    from: Order
                    to: OrderItem
                    cardinality: one_to_many
                }
                """);

        var result = new ChronosValidator().validate(model);
        assertFalse(result.hasErrors(), "Should be valid when entity is imported");
    }

    @Test
    void validRelationship_bothEntitiesImported() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                use com.other#Order
                use com.other#OrderItem

                relationship OrderItems {
                    from: Order
                    to: OrderItem
                    cardinality: one_to_many
                }
                """);

        var result = new ChronosValidator().validate(model);
        assertFalse(result.hasErrors(), "Should be valid when both entities are imported");
    }
}

