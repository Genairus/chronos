package com.genairus.chronos.validator;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-028: Error severity must be one of: critical, high, medium, low.
 */
class Chr028ErrorSeverityValidationTest {

    private final ChronosValidator validator = new ChronosValidator();

    @Test
    void validSeverity_critical_shouldPass() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                error CriticalError {
                    code: "CRIT-001"
                    severity: critical
                    recoverable: false
                    message: "Critical error"
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chr028Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-028"))
                .toList();
        
        assertEquals(0, chr028Errors.size());
    }

    @Test
    void validSeverity_high_shouldPass() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                error HighError {
                    code: "HIGH-001"
                    severity: high
                    recoverable: true
                    message: "High severity error"
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chr028Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-028"))
                .toList();
        
        assertEquals(0, chr028Errors.size());
    }

    @Test
    void validSeverity_medium_shouldPass() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                error MediumError {
                    code: "MED-001"
                    severity: medium
                    recoverable: true
                    message: "Medium severity error"
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chr028Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-028"))
                .toList();
        
        assertEquals(0, chr028Errors.size());
    }

    @Test
    void validSeverity_low_shouldPass() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                error LowError {
                    code: "LOW-001"
                    severity: low
                    recoverable: true
                    message: "Low severity error"
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chr028Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-028"))
                .toList();
        
        assertEquals(0, chr028Errors.size());
    }

    @Test
    void invalidSeverity_shouldFail() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                error InvalidError {
                    code: "INV-001"
                    severity: extreme
                    recoverable: true
                    message: "Invalid severity"
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chr028Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-028"))
                .toList();
        
        assertEquals(1, chr028Errors.size());
        assertTrue(chr028Errors.get(0).message().contains("InvalidError"));
        assertTrue(chr028Errors.get(0).message().contains("extreme"));
        assertTrue(chr028Errors.get(0).message().contains("critical, high, medium, low"));
    }

    @Test
    void multipleMixedSeverities_shouldReportOnlyInvalid() {
        var model = new ChronosCompiler().compile("""
                namespace com.example

                error ValidError {
                    code: "VALID-001"
                    severity: high
                    recoverable: true
                    message: "Valid error"
                }

                error InvalidError1 {
                    code: "INV-001"
                    severity: urgent
                    recoverable: true
                    message: "Invalid severity 1"
                }

                error InvalidError2 {
                    code: "INV-002"
                    severity: severe
                    recoverable: true
                    message: "Invalid severity 2"
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chr028Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-028"))
                .toList();

        // Should have 2 errors for the invalid severities
        assertEquals(2, chr028Errors.size());
        assertTrue(chr028Errors.stream().anyMatch(e -> e.message().contains("InvalidError1")));
        assertTrue(chr028Errors.stream().anyMatch(e -> e.message().contains("InvalidError2")));
    }
}

