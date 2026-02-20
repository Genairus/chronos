package com.genairus.chronos.validator;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-020: Severity must be one of: error, warning, info.
 */
class Chr020SeverityValidationTest {

    private final ChronosValidator validator = new ChronosValidator();

    @Test
    void entityInvariant_validSeverityError() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity Order {
                    total: Float
                    
                    invariant PositiveTotal {
                        expression: "total > 0"
                        severity: error
                    }
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chr020Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-020"))
                .toList();
        
        assertEquals(0, chr020Errors.size());
    }

    @Test
    void entityInvariant_validSeverityWarning() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity Order {
                    total: Float
                    
                    invariant PositiveTotal {
                        expression: "total > 0"
                        severity: warning
                    }
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chr020Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-020"))
                .toList();
        
        assertEquals(0, chr020Errors.size());
    }

    @Test
    void entityInvariant_validSeverityInfo() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity Order {
                    total: Float
                    
                    invariant PositiveTotal {
                        expression: "total > 0"
                        severity: info
                    }
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chr020Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-020"))
                .toList();
        
        assertEquals(0, chr020Errors.size());
    }

    @Test
    void entityInvariant_invalidSeverity() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity Order {
                    total: Float
                    
                    invariant PositiveTotal {
                        expression: "total > 0"
                        severity: critical
                    }
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chr020Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-020"))
                .toList();
        
        assertEquals(1, chr020Errors.size());
        assertTrue(chr020Errors.get(0).message().contains("PositiveTotal"));
        assertTrue(chr020Errors.get(0).message().contains("critical"));
        assertTrue(chr020Errors.get(0).message().contains("error, warning, info"));
    }

    @Test
    void globalInvariant_validSeverity() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity Order { total: Float }
                
                invariant AllOrdersPositive {
                    scope: [Order]
                    expression: "forAll(Order, o => o.total > 0)"
                    severity: warning
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chr020Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-020"))
                .toList();
        
        assertEquals(0, chr020Errors.size());
    }

    @Test
    void globalInvariant_invalidSeverity() {
        var model = new ChronosCompiler().compile("""
                namespace com.example
                
                entity Order { total: Float }
                
                invariant AllOrdersPositive {
                    scope: [Order]
                    expression: "forAll(Order, o => o.total > 0)"
                    severity: fatal
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chr020Errors = result.errors().stream()
                .filter(e -> e.code().equals("CHR-020"))
                .toList();
        
        assertEquals(1, chr020Errors.size());
        assertTrue(chr020Errors.get(0).message().contains("AllOrdersPositive"));
        assertTrue(chr020Errors.get(0).message().contains("fatal"));
    }
}

