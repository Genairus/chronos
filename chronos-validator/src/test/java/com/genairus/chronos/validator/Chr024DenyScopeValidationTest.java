package com.genairus.chronos.validator;

import com.genairus.chronos.model.ChronosModel;
import com.genairus.chronos.parser.ChronosModelParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-024: Deny scope entities must be defined or imported.
 */
class Chr024DenyScopeValidationTest {

    @Test
    void denyScopeReferencesDefinedEntity_noError() {
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
        assertFalse(result.hasErrors(), "Should not have errors when scope references defined entity");
    }

    @Test
    void denyScopeReferencesMultipleDefinedEntities_noError() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity CustomerProfile {
                    name: String
                }
                
                entity PaymentInfo {
                    cardNumber: String
                }
                
                deny ExposePIIInLogs {
                    description: "PII must never appear in logs"
                    scope: [CustomerProfile, PaymentInfo]
                    severity: critical
                }
                """);

        ValidationResult result = new ChronosValidator().validate(model);
        assertFalse(result.hasErrors());
    }

    @Test
    void denyScopeReferencesUndefinedEntity_error() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity UserCredential {
                    username: String
                }
                
                deny StorePlaintextPasswords {
                    description: "The system must never store passwords in plaintext"
                    scope: [UndefinedEntity]
                    severity: critical
                }
                """);

        ValidationResult result = new ChronosValidator().validate(model);
        assertTrue(result.hasErrors());
        
        var chr024Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-024"))
                .toList();
        
        assertEquals(1, chr024Errors.size());
        assertTrue(chr024Errors.get(0).message().contains("undefined entity"));
        assertTrue(chr024Errors.get(0).message().contains("UndefinedEntity"));
    }

    @Test
    void denyScopeReferencesImportedEntity_noError() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                use com.other#Order
                
                deny NoPlaintextOrders {
                    description: "Orders must not be stored in plaintext"
                    scope: [Order]
                    severity: critical
                }
                """);

        ValidationResult result = new ChronosValidator().validate(model);
        assertFalse(result.hasErrors(), "Should not have errors when scope references imported entity");
    }

    @Test
    void denyScopePartiallyUndefined_errorForUndefinedOnly() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity CustomerProfile {
                    name: String
                }
                
                deny ExposePIIInLogs {
                    description: "PII must never appear in logs"
                    scope: [CustomerProfile, UndefinedEntity, AnotherUndefined]
                    severity: critical
                }
                """);

        ValidationResult result = new ChronosValidator().validate(model);
        
        var chr024Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-024"))
                .toList();

        assertEquals(2, chr024Errors.size(), "Should have 2 errors for the 2 undefined entities");
        
        boolean hasUndefinedEntity = chr024Errors.stream()
                .anyMatch(e -> e.message().contains("UndefinedEntity"));
        boolean hasAnotherUndefined = chr024Errors.stream()
                .anyMatch(e -> e.message().contains("AnotherUndefined"));
        
        assertTrue(hasUndefinedEntity);
        assertTrue(hasAnotherUndefined);
    }

    @Test
    void multipleDeniesWithUndefinedScopes_multipleErrors() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity Entity1 {
                    field1: String
                }
                
                deny Deny1 {
                    description: "Test deny 1"
                    scope: [UndefinedEntity1]
                    severity: critical
                }
                
                deny Deny2 {
                    description: "Test deny 2"
                    scope: [Entity1]
                    severity: high
                }
                
                deny Deny3 {
                    description: "Test deny 3"
                    scope: [UndefinedEntity2]
                    severity: medium
                }
                """);

        ValidationResult result = new ChronosValidator().validate(model);
        
        var chr024Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-024"))
                .toList();

        assertEquals(2, chr024Errors.size());
    }
}

