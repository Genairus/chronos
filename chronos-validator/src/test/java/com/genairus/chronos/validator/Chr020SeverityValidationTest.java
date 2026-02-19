package com.genairus.chronos.validator;

import com.genairus.chronos.parser.ChronosModelParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-020: Severity must be one of: error, warning, info.
 */
class Chr020SeverityValidationTest {

    private final ChronosValidator validator = new ChronosValidator();

    @Test
    void entityInvariant_validSeverityError() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity Order {
                    total: Float
                    
                    invariant PositiveTotal {
                        expression: "total > 0"
                        severity: error
                    }
                }
                """);

        var result = validator.validate(model);
        var chr020Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-020"))
                .toList();
        
        assertEquals(0, chr020Errors.size());
    }

    @Test
    void entityInvariant_validSeverityWarning() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity Order {
                    total: Float
                    
                    invariant PositiveTotal {
                        expression: "total > 0"
                        severity: warning
                    }
                }
                """);

        var result = validator.validate(model);
        var chr020Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-020"))
                .toList();
        
        assertEquals(0, chr020Errors.size());
    }

    @Test
    void entityInvariant_validSeverityInfo() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity Order {
                    total: Float
                    
                    invariant PositiveTotal {
                        expression: "total > 0"
                        severity: info
                    }
                }
                """);

        var result = validator.validate(model);
        var chr020Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-020"))
                .toList();
        
        assertEquals(0, chr020Errors.size());
    }

    @Test
    void entityInvariant_invalidSeverity() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity Order {
                    total: Float
                    
                    invariant PositiveTotal {
                        expression: "total > 0"
                        severity: critical
                    }
                }
                """);

        var result = validator.validate(model);
        var chr020Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-020"))
                .toList();
        
        assertEquals(1, chr020Errors.size());
        assertTrue(chr020Errors.get(0).message().contains("PositiveTotal"));
        assertTrue(chr020Errors.get(0).message().contains("critical"));
        assertTrue(chr020Errors.get(0).message().contains("error, warning, info"));
    }

    @Test
    void globalInvariant_validSeverity() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity Order { total: Float }
                
                invariant AllOrdersPositive {
                    scope: [Order]
                    expression: "forAll(Order, o => o.total > 0)"
                    severity: warning
                }
                """);

        var result = validator.validate(model);
        var chr020Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-020"))
                .toList();
        
        assertEquals(0, chr020Errors.size());
    }

    @Test
    void globalInvariant_invalidSeverity() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity Order { total: Float }
                
                invariant AllOrdersPositive {
                    scope: [Order]
                    expression: "forAll(Order, o => o.total > 0)"
                    severity: fatal
                }
                """);

        var result = validator.validate(model);
        var chr020Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-020"))
                .toList();
        
        assertEquals(1, chr020Errors.size());
        assertTrue(chr020Errors.get(0).message().contains("AllOrdersPositive"));
        assertTrue(chr020Errors.get(0).message().contains("fatal"));
    }
}

