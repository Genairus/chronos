package com.genairus.chronos.parser;

import com.genairus.chronos.model.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for parsing relationship definitions (Phase 1.1).
 */
class RelationshipParserTest {

    @Test
    void parseSimpleRelationship_oneToMany() {
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

        assertEquals(1, model.relationships().size());
        RelationshipDef rel = model.relationships().get(0);
        
        assertEquals("OrderItems", rel.name());
        assertEquals("Order", rel.fromEntity());
        assertEquals("OrderItem", rel.toEntity());
        assertEquals(Cardinality.ONE_TO_MANY, rel.cardinality());
        assertTrue(rel.semantics().isEmpty());
        assertEquals(RelationshipSemantics.ASSOCIATION, rel.effectiveSemantics());
        assertTrue(rel.inverseField().isEmpty());
    }

    @Test
    void parseRelationship_withSemantics() {
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

        RelationshipDef rel = model.relationships().get(0);
        assertTrue(rel.semantics().isPresent());
        assertEquals(RelationshipSemantics.COMPOSITION, rel.semantics().get());
        assertEquals(RelationshipSemantics.COMPOSITION, rel.effectiveSemantics());
    }

    @Test
    void parseRelationship_withInverseField() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                entity Order { id: String }
                entity OrderItem { id: String }

                relationship OrderItems {
                    from: Order
                    to: OrderItem
                    cardinality: one_to_many
                    semantics: composition
                    inverse: order
                }
                """);

        RelationshipDef rel = model.relationships().get(0);
        assertTrue(rel.inverseField().isPresent());
        assertEquals("order", rel.inverseField().get());
    }

    @Test
    void parseRelationship_withTraitsAndDocComments() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                entity Order { id: String }
                entity OrderItem { id: String }

                /// Represents the line items in an order
                @description("Order contains line items")
                relationship OrderItems {
                    from: Order
                    to: OrderItem
                    cardinality: one_to_many
                    semantics: composition
                }
                """);

        RelationshipDef rel = model.relationships().get(0);
        assertEquals(1, rel.docComments().size());
        assertEquals("Represents the line items in an order", rel.docComments().get(0));
        assertEquals(1, rel.traits().size());
        assertEquals("description", rel.traits().get(0).name());
    }

    @Test
    void parseRelationship_oneToOne() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                entity User { id: String }
                entity Profile { id: String }

                relationship UserProfile {
                    from: User
                    to: Profile
                    cardinality: one_to_one
                }
                """);

        RelationshipDef rel = model.relationships().get(0);
        assertEquals(Cardinality.ONE_TO_ONE, rel.cardinality());
    }

    @Test
    void parseRelationship_manyToMany() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                entity Student { id: String }
                entity Course { id: String }

                relationship Enrollment {
                    from: Student
                    to: Course
                    cardinality: many_to_many
                }
                """);

        RelationshipDef rel = model.relationships().get(0);
        assertEquals(Cardinality.MANY_TO_MANY, rel.cardinality());
    }
}

