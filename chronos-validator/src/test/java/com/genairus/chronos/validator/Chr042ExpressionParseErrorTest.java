package com.genairus.chronos.validator;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-042: Invariant expression failed to parse.
 */
class Chr042ExpressionParseErrorTest {

    private final ChronosValidator validator = new ChronosValidator();

    @Test
    void entityInvariantWithSyntaxError_triggersChr042() {
        var model = new ChronosCompiler().compile("""
                namespace com.example

                entity Order {
                    total: Float

                    invariant BadExpr {
                        expression: "total >>"
                        severity: error
                    }
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        assertTrue(result.errors().stream().anyMatch(d -> "CHR-042".equals(d.code())),
                "Syntax error in expression should trigger CHR-042; got: " + result.diagnostics());
    }

    @Test
    void globalInvariantWithSyntaxError_triggersChr042() {
        var model = new ChronosCompiler().compile("""
                namespace com.example

                entity Order {
                    total: Float
                }

                invariant GlobalBad {
                    scope: [Order]
                    expression: "Order.total &&& 0"
                    severity: error
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        assertTrue(result.errors().stream().anyMatch(d -> "CHR-042".equals(d.code())),
                "Parse error in global invariant should trigger CHR-042; got: " + result.diagnostics());
    }

    @Test
    void validExpression_doesNotTriggerChr042() {
        var model = new ChronosCompiler().compile("""
                namespace com.example

                entity Order {
                    @required
                    total: Float

                    invariant PositiveTotal {
                        expression: "total > 0"
                        severity: error
                    }
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        assertFalse(result.errors().stream().anyMatch(d -> "CHR-042".equals(d.code())),
                "Valid expression should not trigger CHR-042; got: " + result.diagnostics());
    }

    @Test
    void chr042MessageContainsInvariantName() {
        var model = new ChronosCompiler().compile("""
                namespace com.example

                entity Order {
                    total: Float

                    invariant MyBrokenInvariant {
                        expression: "total ++"
                        severity: error
                    }
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        var chr042 = result.errors().stream()
                .filter(d -> "CHR-042".equals(d.code()))
                .findFirst();

        assertTrue(chr042.isPresent(), "Expected CHR-042; got: " + result.diagnostics());
        assertTrue(chr042.get().message().contains("MyBrokenInvariant"),
                "CHR-042 message should contain the invariant name; got: " + chr042.get().message());
    }
}
