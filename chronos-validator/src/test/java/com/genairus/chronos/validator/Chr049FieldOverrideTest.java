package com.genairus.chronos.validator;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-049: A child entity may not redefine a parent field with an incompatible type.
 */
class Chr049FieldOverrideTest {

    @Test
    void validOverride_sameType() {
        var model = new ChronosCompiler().compile("""
                namespace com.example

                entity User {
                    id: String
                    email: String
                }

                entity PremiumUser extends User {
                    email: String
                    tier: String
                }
                """, "test").modelOrNull();

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        // Should not have any CHR-049 errors
        var chr049Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-049"))
                .toList();

        assertEquals(0, chr049Errors.size());
    }

    @Test
    void validInheritance_noOverride() {
        var model = new ChronosCompiler().compile("""
                namespace com.example

                entity User {
                    id: String
                    email: String
                }

                entity PremiumUser extends User {
                    tier: String
                }
                """, "test").modelOrNull();

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        // Should not have any CHR-049 errors
        var chr049Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-049"))
                .toList();

        assertEquals(0, chr049Errors.size());
    }

    @Test
    void invalidOverride_differentPrimitiveType() {
        var model = new ChronosCompiler().compile("""
                namespace com.example

                entity User {
                    id: String
                    age: Integer
                }

                entity PremiumUser extends User {
                    age: String
                }
                """, "test").modelOrNull();

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        assertTrue(result.hasErrors());
        var chr049Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-049"))
                .toList();

        assertEquals(1, chr049Errors.size());
        assertTrue(chr049Errors.get(0).message().contains("age"));
        assertTrue(chr049Errors.get(0).message().contains("incompatible type"));
    }

    @Test
    void invalidOverride_differentNamedType() {
        var model = new ChronosCompiler().compile("""
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
                """, "test").modelOrNull();

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        assertTrue(result.hasErrors());
        var chr049Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-049"))
                .toList();

        assertEquals(1, chr049Errors.size());
        assertTrue(chr049Errors.get(0).message().contains("location"));
        assertTrue(chr049Errors.get(0).message().contains("incompatible type"));
    }

    @Test
    void invalidOverride_primitiveToCollection() {
        var model = new ChronosCompiler().compile("""
                namespace com.example

                entity User {
                    id: String
                }

                entity PremiumUser extends User {
                    id: List<String>
                }
                """, "test").modelOrNull();

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        assertTrue(result.hasErrors());
        var chr049Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-049"))
                .toList();

        assertEquals(1, chr049Errors.size());
        assertTrue(chr049Errors.get(0).message().contains("id"));
    }

    @Test
    void invalidOverride_listElementType() {
        var model = new ChronosCompiler().compile("""
                namespace com.example

                entity User {
                    id: String
                    tags: List<String>
                }

                entity PremiumUser extends User {
                    tags: List<Integer>
                }
                """, "test").modelOrNull();

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        assertTrue(result.hasErrors());
        var chr049Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-049"))
                .toList();

        assertEquals(1, chr049Errors.size());
        assertTrue(chr049Errors.get(0).message().contains("tags"));
    }

    @Test
    void validOverride_sameListType() {
        var model = new ChronosCompiler().compile("""
                namespace com.example

                entity User {
                    id: String
                    tags: List<String>
                }

                entity PremiumUser extends User {
                    tags: List<String>
                    tier: String
                }
                """, "test").modelOrNull();

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        // Should not have any CHR-049 errors
        var chr049Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-049"))
                .toList();

        assertEquals(0, chr049Errors.size());
    }

    @Test
    void invalidOverride_multipleFields() {
        var model = new ChronosCompiler().compile("""
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
                """, "test").modelOrNull();

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        assertTrue(result.hasErrors());
        var chr049Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-049"))
                .toList();

        // Should have 2 errors - one for age, one for email
        assertEquals(2, chr049Errors.size());
    }

    @Test
    void noErrorWhenParentNotFound() {
        var model = new ChronosCompiler().compile("""
                namespace com.example

                entity PremiumUser extends NonExistentUser {
                    tier: String
                }
                """, "test").modelOrNull();

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        // Should have CHR-008 error for undefined parent, but not CHR-049
        var chr049Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-049"))
                .toList();

        assertEquals(0, chr049Errors.size());
    }
}
