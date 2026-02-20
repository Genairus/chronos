package com.genairus.chronos.validator;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Chr015CircularInheritanceTest {

    @Test
    void validInheritance_noCircle() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity User {
                    id: String
                }
                
                entity PremiumUser extends User {
                    tier: String
                }
                """, "test").modelOrNull();

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        assertFalse(result.hasErrors());
    }

    @Test
    void entityCircularInheritance_directSelfReference() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity User extends User {
                    id: String
                }
                """, "test").modelOrNull();

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        assertTrue(result.hasErrors());
        var errors = result.errors();
        assertEquals(1, errors.size());
        assertEquals("CHR-015", errors.get(0).code());
        assertTrue(errors.get(0).message().contains("circular inheritance"));
    }

    @Test
    void entityCircularInheritance_twoEntityCycle() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity A extends B {
                    id: String
                }
                
                entity B extends A {
                    name: String
                }
                """, "test").modelOrNull();

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        assertTrue(result.hasErrors());
        var errors = result.errors();
        // Both entities should report circular inheritance
        assertEquals(2, errors.stream().filter(e -> e.code().equals("CHR-015")).count());
    }

    @Test
    void entityCircularInheritance_threeEntityCycle() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity A extends B {
                    id: String
                }
                
                entity B extends C {
                    name: String
                }
                
                entity C extends A {
                    email: String
                }
                """, "test").modelOrNull();

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        assertTrue(result.hasErrors());
        var errors = result.errors();
        // All three entities should report circular inheritance
        assertEquals(3, errors.stream().filter(e -> e.code().equals("CHR-015")).count());
    }

    @Test
    void actorCircularInheritance_directSelfReference() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                actor User extends User
                """, "test").modelOrNull();

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        assertTrue(result.hasErrors());
        var errors = result.errors();
        assertTrue(errors.stream().anyMatch(e -> 
            e.code().equals("CHR-015") && e.message().contains("circular inheritance")));
    }

    @Test
    void actorCircularInheritance_twoActorCycle() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                actor A extends B
                actor B extends A
                """, "test").modelOrNull();

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        assertTrue(result.hasErrors());
        var errors = result.errors();
        // Both actors should report circular inheritance
        assertEquals(2, errors.stream().filter(e -> e.code().equals("CHR-015")).count());
    }
}

