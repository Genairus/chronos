package com.genairus.chronos.validator;

import com.genairus.chronos.model.ChronosModel;
import com.genairus.chronos.parser.ChronosModelParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-014: Inverse field name (if specified) must exist on the target entity.
 */
class Chr014InverseFieldTest {

    @Test
    void validRelationship_withInverseField() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                entity Order {
                    id: String
                    customer: String
                }
                entity Customer {
                    id: String
                    orders: List<String>
                }

                relationship OrderCustomer {
                    from: Order
                    to: Customer
                    cardinality: one_to_many
                    inverse: orders
                }
                """);

        var result = new ChronosValidator().validate(model);
        assertFalse(result.hasErrors(), "Should be valid when inverse field exists on target entity");
    }

    @Test
    void validRelationship_withoutInverseField() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                entity Order { id: String }
                entity Customer { id: String }

                relationship OrderCustomer {
                    from: Order
                    to: Customer
                    cardinality: one_to_many
                }
                """);

        var result = new ChronosValidator().validate(model);
        assertFalse(result.hasErrors(), "Should be valid when no inverse field is specified");
    }

    @Test
    void invalidRelationship_inverseFieldDoesNotExist() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                entity Order {
                    id: String
                    customer: String
                }
                entity Customer {
                    id: String
                    name: String
                }

                relationship OrderCustomer {
                    from: Order
                    to: Customer
                    cardinality: one_to_many
                    inverse: orders
                }
                """);

        var result = new ChronosValidator().validate(model);
        assertTrue(result.hasErrors());
        assertEquals(1, result.errors().size());

        var error = result.errors().get(0);
        assertEquals("CHR-014", error.ruleCode());
        assertTrue(error.message().contains("orders"));
        assertTrue(error.message().contains("Customer"));
        assertTrue(error.message().contains("OrderCustomer"));
    }

    @Test
    void validRelationship_inverseFieldExistsOnTargetEntity() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity User { 
                    id: String
                    profile: String
                }
                entity Profile { 
                    id: String
                    user: String
                }
                
                relationship UserProfile {
                    from: User
                    to: Profile
                    cardinality: one_to_one
                    inverse: user
                }
                """);

        var result = new ChronosValidator().validate(model);
        assertFalse(result.hasErrors(), "Should be valid when inverse field exists");
    }

    @Test
    void invalidRelationship_targetEntityDoesNotExist() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                entity Order {
                    id: String
                }

                relationship OrderCustomer {
                    from: Order
                    to: Customer
                    cardinality: one_to_many
                    inverse: orders
                }
                """);

        var result = new ChronosValidator().validate(model);
        assertTrue(result.hasErrors());

        // Should have CHR-011 error (undefined target entity)
        // but NOT CHR-014 error (since we skip validation when target doesn't exist)
        var chr014Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-014"))
                .toList();

        assertEquals(0, chr014Errors.size(),
                "Should not report CHR-014 when target entity doesn't exist (already caught by CHR-011)");

        var chr011Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-011"))
                .toList();

        assertEquals(1, chr011Errors.size(), "Should report CHR-011 for undefined target entity");
    }
}

