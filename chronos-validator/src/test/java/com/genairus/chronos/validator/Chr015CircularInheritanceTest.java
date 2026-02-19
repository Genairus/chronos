package com.genairus.chronos.validator;

import com.genairus.chronos.parser.ChronosModelParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Chr015CircularInheritanceTest {

    @Test
    void validInheritance_noCircle() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity User {
                    id: String
                }
                
                entity PremiumUser extends User {
                    tier: String
                }
                """);

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        assertFalse(result.hasErrors());
    }

    @Test
    void entityCircularInheritance_directSelfReference() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity User extends User {
                    id: String
                }
                """);

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        assertTrue(result.hasErrors());
        var errors = result.errors();
        assertEquals(1, errors.size());
        assertEquals("CHR-015", errors.get(0).ruleCode());
        assertTrue(errors.get(0).message().contains("circular inheritance"));
    }

    @Test
    void entityCircularInheritance_twoEntityCycle() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity A extends B {
                    id: String
                }
                
                entity B extends A {
                    name: String
                }
                """);

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        assertTrue(result.hasErrors());
        var errors = result.errors();
        // Both entities should report circular inheritance
        assertEquals(2, errors.stream().filter(e -> e.ruleCode().equals("CHR-015")).count());
    }

    @Test
    void entityCircularInheritance_threeEntityCycle() {
        var model = ChronosModelParser.parseString("test", """
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
                """);

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        assertTrue(result.hasErrors());
        var errors = result.errors();
        // All three entities should report circular inheritance
        assertEquals(3, errors.stream().filter(e -> e.ruleCode().equals("CHR-015")).count());
    }

    @Test
    void actorCircularInheritance_directSelfReference() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                actor User extends User
                """);

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        assertTrue(result.hasErrors());
        var errors = result.errors();
        assertTrue(errors.stream().anyMatch(e -> 
            e.ruleCode().equals("CHR-015") && e.message().contains("circular inheritance")));
    }

    @Test
    void actorCircularInheritance_twoActorCycle() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                actor A extends B
                actor B extends A
                """);

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        assertTrue(result.hasErrors());
        var errors = result.errors();
        // Both actors should report circular inheritance
        assertEquals(2, errors.stream().filter(e -> e.ruleCode().equals("CHR-015")).count());
    }
}

