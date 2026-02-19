package com.genairus.chronos.validator;

import com.genairus.chronos.parser.ChronosModelParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Chr018MultipleInheritanceTest {

    @Test
    void singleInheritance_isValid() {
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

        // Should not have any CHR-018 errors
        var chr018Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-018"))
                .toList();
        
        assertEquals(0, chr018Errors.size());
    }

    @Test
    void noInheritance_isValid() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity User {
                    id: String
                }
                
                entity Order {
                    orderId: String
                }
                """);

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        // Should not have any CHR-018 errors
        var chr018Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-018"))
                .toList();
        
        assertEquals(0, chr018Errors.size());
    }

    @Test
    void actorSingleInheritance_isValid() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                actor User
                actor AdminUser extends User
                """);

        var validator = new ChronosValidator();
        var result = validator.validate(model);

        // Should not have any CHR-018 errors
        var chr018Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-018"))
                .toList();
        
        assertEquals(0, chr018Errors.size());
    }

    // Note: We cannot test actual multiple inheritance because the grammar
    // does not support it. The grammar only allows a single parent in the
    // 'extends' clause. This test file exists to document that CHR-018
    // is implemented and to ensure it doesn't produce false positives.
    // If the grammar is ever extended to support multiple inheritance syntax,
    // additional tests should be added here to verify that CHR-018 correctly
    // rejects it.
}

