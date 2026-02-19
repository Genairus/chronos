package com.genairus.chronos.validator;

import com.genairus.chronos.model.ChronosModel;
import com.genairus.chronos.parser.ChronosModelParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-023: Every deny must include a description.
 */
class Chr023DenyDescriptionTest {

    @Test
    void denyWithDescription_noError() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity UserCredential {
                    username: String
                }
                
                deny StorePlaintextPasswords {
                    description: "The system must never store passwords in plaintext"
                    scope: [UserCredential]
                    severity: critical
                }
                """);

        ValidationResult result = new ChronosValidator().validate(model);
        assertFalse(result.hasErrors(), "Should not have errors when deny has description");
    }

    @Test
    void denyWithEmptyDescription_error() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity UserCredential {
                    username: String
                }
                
                deny StorePlaintextPasswords {
                    description: ""
                    scope: [UserCredential]
                    severity: critical
                }
                """);

        ValidationResult result = new ChronosValidator().validate(model);
        assertTrue(result.hasErrors());
        
        var chr023Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-023"))
                .toList();
        
        assertEquals(1, chr023Errors.size());
        assertTrue(chr023Errors.get(0).message().contains("must include a description"));
        assertTrue(chr023Errors.get(0).message().contains("StorePlaintextPasswords"));
    }

    @Test
    void multipleDeniesWithMissingDescriptions_multipleErrors() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity Entity1 {
                    field1: String
                }
                
                entity Entity2 {
                    field2: String
                }
                
                deny Deny1 {
                    description: ""
                    scope: [Entity1]
                    severity: critical
                }
                
                deny Deny2 {
                    description: "Valid description"
                    scope: [Entity2]
                    severity: high
                }
                
                deny Deny3 {
                    description: ""
                    scope: [Entity1]
                    severity: medium
                }
                """);

        ValidationResult result = new ChronosValidator().validate(model);
        
        var chr023Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-023"))
                .toList();

        assertEquals(2, chr023Errors.size(), "Should have 2 CHR-023 errors for Deny1 and Deny3");
    }
}

