package com.genairus.chronos.validator;

import com.genairus.chronos.parser.ChronosModelParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Chr016FieldOverrideTest {

    @Test
    void validOverride_sameType() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity User {
                    id: String
                    email: String
                }
                
                entity PremiumUser extends User {
                    email: String
                    tier: String
                }
                """);

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        // Should not have any CHR-016 errors
        var chr016Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-016"))
                .toList();
        
        assertEquals(0, chr016Errors.size());
    }

    @Test
    void validInheritance_noOverride() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity User {
                    id: String
                    email: String
                }
                
                entity PremiumUser extends User {
                    tier: String
                }
                """);

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        // Should not have any CHR-016 errors
        var chr016Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-016"))
                .toList();
        
        assertEquals(0, chr016Errors.size());
    }

    @Test
    void invalidOverride_differentPrimitiveType() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity User {
                    id: String
                    age: Integer
                }
                
                entity PremiumUser extends User {
                    age: String
                }
                """);

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        assertTrue(result.hasErrors());
        var chr016Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-016"))
                .toList();
        
        assertEquals(1, chr016Errors.size());
        assertTrue(chr016Errors.get(0).message().contains("age"));
        assertTrue(chr016Errors.get(0).message().contains("incompatible type"));
    }

    @Test
    void invalidOverride_differentNamedType() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                shape Address {
                    street: String
                }
                
                shape Location {
                    lat: Double
                    lon: Double
                }
                
                entity User {
                    id: String
                    location: Address
                }
                
                entity PremiumUser extends User {
                    location: Location
                }
                """);

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        assertTrue(result.hasErrors());
        var chr016Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-016"))
                .toList();
        
        assertEquals(1, chr016Errors.size());
        assertTrue(chr016Errors.get(0).message().contains("location"));
        assertTrue(chr016Errors.get(0).message().contains("incompatible type"));
    }

    @Test
    void invalidOverride_primitiveToCollection() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                entity User {
                    id: String
                }

                entity PremiumUser extends User {
                    id: List<String>
                }
                """);

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        assertTrue(result.hasErrors());
        var chr016Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-016"))
                .toList();

        assertEquals(1, chr016Errors.size());
        assertTrue(chr016Errors.get(0).message().contains("id"));
    }

    @Test
    void invalidOverride_listElementType() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                entity User {
                    id: String
                    tags: List<String>
                }

                entity PremiumUser extends User {
                    tags: List<Integer>
                }
                """);

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        assertTrue(result.hasErrors());
        var chr016Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-016"))
                .toList();

        assertEquals(1, chr016Errors.size());
        assertTrue(chr016Errors.get(0).message().contains("tags"));
    }

    @Test
    void validOverride_sameListType() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                entity User {
                    id: String
                    tags: List<String>
                }

                entity PremiumUser extends User {
                    tags: List<String>
                    tier: String
                }
                """);

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        // Should not have any CHR-016 errors
        var chr016Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-016"))
                .toList();

        assertEquals(0, chr016Errors.size());
    }

    @Test
    void invalidOverride_multipleFields() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                entity User {
                    id: String
                    age: Integer
                    email: String
                }

                entity PremiumUser extends User {
                    age: String
                    email: Integer
                }
                """);

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        assertTrue(result.hasErrors());
        var chr016Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-016"))
                .toList();

        // Should have 2 errors - one for age, one for email
        assertEquals(2, chr016Errors.size());
    }

    @Test
    void noErrorWhenParentNotFound() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                entity PremiumUser extends NonExistentUser {
                    tier: String
                }
                """);

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        // Should have CHR-008 error for undefined parent, but not CHR-016
        var chr016Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-016"))
                .toList();

        assertEquals(0, chr016Errors.size());
    }
}


