package com.genairus.chronos.validator;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-043: Type mismatch in invariant expression (WARNING).
 *
 * <p>CHR-043 is only emitted when BOTH sides of an operator have known,
 * incompatible types — expressions with unresolved fields (UNKNOWN) never
 * produce false-positive warnings.
 */
class Chr043TypeMismatchTest {

    private final ChronosValidator validator = new ChronosValidator();

    /** Boolean field used in numeric comparison → CHR-043 WARNING. */
    @Test
    void numericComparisonOnBooleanField_triggersChr043() {
        var model = new ChronosCompiler().compile("""
                namespace com.example

                entity Order {
                    @required
                    active: Boolean

                    invariant BadComparison {
                        expression: "active > 0"
                        severity: error
                    }
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        assertTrue(result.warnings().stream().anyMatch(d -> "CHR-043".equals(d.code())),
                "Boolean field in numeric comparison should trigger CHR-043; got: " + result.diagnostics());
    }

    /** String field added to integer → CHR-043 WARNING. */
    @Test
    void additionOfStringAndInt_triggersChr043() {
        var model = new ChronosCompiler().compile("""
                namespace com.example

                entity Product {
                    @required
                    name: String
                    @required
                    price: Integer

                    invariant BadAdd {
                        expression: "name + price"
                        severity: error
                    }
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        assertTrue(result.warnings().stream().anyMatch(d -> "CHR-043".equals(d.code())),
                "String + Integer should trigger CHR-043; got: " + result.diagnostics());
    }

    /** Integer fields used with logical AND → CHR-043 WARNING. */
    @Test
    void logicalAndOnNonBooleans_triggersChr043() {
        var model = new ChronosCompiler().compile("""
                namespace com.example

                entity Order {
                    @required
                    price: Integer
                    @required
                    quantity: Integer

                    invariant BadLogical {
                        expression: "price && quantity"
                        severity: error
                    }
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        assertTrue(result.warnings().stream().anyMatch(d -> "CHR-043".equals(d.code())),
                "Integer && Integer should trigger CHR-043; got: " + result.diagnostics());
    }

    /**
     * Unresolved field (UNKNOWN type) used in comparison → no CHR-043.
     * CHR-019 already catches the undefined field; CHR-043 must not double-fire.
     */
    @Test
    void unknownFieldDoesNotTriggerChr043() {
        var model = new ChronosCompiler().compile("""
                namespace com.example

                entity Order {
                    @required
                    price: Integer

                    invariant UndefinedField {
                        expression: "unknownField > 0"
                        severity: error
                    }
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        assertFalse(result.warnings().stream().anyMatch(d -> "CHR-043".equals(d.code())),
                "Unresolved field (UNKNOWN type) must not trigger CHR-043; got: " + result.diagnostics());
    }

    /** Valid boolean expression with numeric comparisons → no CHR-043. */
    @Test
    void validBooleanExpression_doesNotTriggerChr043() {
        var model = new ChronosCompiler().compile("""
                namespace com.example

                entity Order {
                    @required
                    price: Integer
                    @required
                    quantity: Integer

                    invariant ValidExpr {
                        expression: "price > 0 && quantity > 0"
                        severity: error
                    }
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        assertFalse(result.warnings().stream().anyMatch(d -> "CHR-043".equals(d.code())),
                "Valid boolean expression must not trigger CHR-043; got: " + result.diagnostics());
    }

    /** Equality comparison between Integer field and Boolean literal → CHR-043 WARNING. */
    @Test
    void equalityOnMismatchedTypes_triggersChr043() {
        var model = new ChronosCompiler().compile("""
                namespace com.example

                entity Order {
                    @required
                    price: Integer

                    invariant BadEquality {
                        expression: "price == true"
                        severity: error
                    }
                }
                """, "test").modelOrNull();

        var result = validator.validate(model);
        assertTrue(result.warnings().stream().anyMatch(d -> "CHR-043".equals(d.code())),
                "Integer == Boolean should trigger CHR-043; got: " + result.diagnostics());
    }
}
