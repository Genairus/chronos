package com.genairus.chronos.validator;

import com.genairus.chronos.model.ChronosModel;
import com.genairus.chronos.parser.ChronosModelParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-025: Deny severity must be one of: critical, high, medium, low.
 */
class Chr025DenySeverityValidationTest {

    @Test
    void denySeverityCritical_noError() {
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
        assertFalse(result.hasErrors());
    }

    @Test
    void denySeverityHigh_noError() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity UserCredential {
                    username: String
                }
                
                deny UseWeakEncryption {
                    description: "Weak encryption must not be used"
                    scope: [UserCredential]
                    severity: high
                }
                """);

        ValidationResult result = new ChronosValidator().validate(model);
        assertFalse(result.hasErrors());
    }

    @Test
    void denySeverityMedium_noError() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity UserCredential {
                    username: String
                }
                
                deny LogSensitiveData {
                    description: "Sensitive data must not be logged"
                    scope: [UserCredential]
                    severity: medium
                }
                """);

        ValidationResult result = new ChronosValidator().validate(model);
        assertFalse(result.hasErrors());
    }

    @Test
    void denySeverityLow_noError() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity UserCredential {
                    username: String
                }
                
                deny UseDeprecatedAPI {
                    description: "Deprecated APIs should not be used"
                    scope: [UserCredential]
                    severity: low
                }
                """);

        ValidationResult result = new ChronosValidator().validate(model);
        assertFalse(result.hasErrors());
    }

    @Test
    void denySeverityInvalid_error() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity UserCredential {
                    username: String
                }
                
                deny StorePlaintextPasswords {
                    description: "The system must never store passwords in plaintext"
                    scope: [UserCredential]
                    severity: invalid
                }
                """);

        ValidationResult result = new ChronosValidator().validate(model);
        assertTrue(result.hasErrors());
        
        var chr025Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-025"))
                .toList();
        
        assertEquals(1, chr025Errors.size());
        assertTrue(chr025Errors.get(0).message().contains("invalid severity"));
        assertTrue(chr025Errors.get(0).message().contains("invalid"));
        assertTrue(chr025Errors.get(0).message().contains("critical, high, medium, low"));
    }

    @Test
    void denySeverityError_error() {
        // "error" is valid for invariants (CHR-020) but not for denies (CHR-025)
        // Since "error" is now a keyword, it cannot be used as an ID in deny severity
        // This should fail at parse time, not validation time
        assertThrows(com.genairus.chronos.parser.ChronosParseException.class, () -> {
            ChronosModelParser.parseString("test", """
                    namespace com.example

                    entity UserCredential {
                        username: String
                    }

                    deny StorePlaintextPasswords {
                        description: "The system must never store passwords in plaintext"
                        scope: [UserCredential]
                        severity: error
                    }
                    """);
        });
    }

    @Test
    void multipleDeniesWithInvalidSeverities_multipleErrors() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity Entity1 {
                    field1: String
                }
                
                deny Deny1 {
                    description: "Test deny 1"
                    scope: [Entity1]
                    severity: invalid1
                }
                
                deny Deny2 {
                    description: "Test deny 2"
                    scope: [Entity1]
                    severity: critical
                }
                
                deny Deny3 {
                    description: "Test deny 3"
                    scope: [Entity1]
                    severity: invalid2
                }
                """);

        ValidationResult result = new ChronosValidator().validate(model);
        
        var chr025Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-025"))
                .toList();

        assertEquals(2, chr025Errors.size(), "Should have 2 CHR-025 errors for Deny1 and Deny3");
    }
}

