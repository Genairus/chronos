package com.genairus.chronos.validator.expr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ExpressionParser}.
 * Tests verify AST structure produced for various expression forms.
 */
class ExpressionParserTest {

    private final ExpressionParser parser = new ExpressionParser();

    private InvariantExpr parse(String expr) {
        return parser.parse(expr);
    }

    // ── Literals ───────────────────────────────────────────────────────────────

    @Test
    void parsesIntLiteral() {
        var result = parse("42");
        assertInstanceOf(InvariantExpr.IntLit.class, result);
        assertEquals(42L, ((InvariantExpr.IntLit) result).value());
    }

    @Test
    void parsesFloatLiteral() {
        var result = parse("3.14");
        assertInstanceOf(InvariantExpr.FloatLit.class, result);
        assertEquals(3.14, ((InvariantExpr.FloatLit) result).value(), 0.0001);
    }

    @Test
    void parsesStringLiteral() {
        var result = parse("\"hello world\"");
        assertInstanceOf(InvariantExpr.StrLit.class, result);
        assertEquals("hello world", ((InvariantExpr.StrLit) result).value());
    }

    @Test
    void parsesBoolLiteralTrue() {
        var result = parse("true");
        assertInstanceOf(InvariantExpr.BoolLit.class, result);
        assertTrue(((InvariantExpr.BoolLit) result).value());
    }

    @Test
    void parsesBoolLiteralFalse() {
        var result = parse("false");
        assertInstanceOf(InvariantExpr.BoolLit.class, result);
        assertFalse(((InvariantExpr.BoolLit) result).value());
    }

    @Test
    void parsesNullLiteral() {
        var result = parse("null");
        assertInstanceOf(InvariantExpr.NullLit.class, result);
    }

    // ── Identifiers ────────────────────────────────────────────────────────────

    @Test
    void parsesSimpleFieldRef() {
        var result = parse("price");
        assertInstanceOf(InvariantExpr.FieldRef.class, result);
        assertEquals("price", ((InvariantExpr.FieldRef) result).name());
    }

    @Test
    void parsesDotPathFieldRef() {
        var result = parse("Order.customerId");
        assertInstanceOf(InvariantExpr.FieldRef.class, result);
        assertEquals("Order.customerId", ((InvariantExpr.FieldRef) result).name());
    }

    @Test
    void parsesEnumRef() {
        var result = parse("ACTIVE");
        assertInstanceOf(InvariantExpr.EnumRef.class, result);
        assertEquals("ACTIVE", ((InvariantExpr.EnumRef) result).name());
    }

    @Test
    void parsesPascalCaseAsEnumRef() {
        // PascalCase identifiers without dots → EnumRef
        var result = parse("Customer");
        assertInstanceOf(InvariantExpr.EnumRef.class, result);
    }

    // ── Comparisons ────────────────────────────────────────────────────────────

    @Test
    void parsesComparisonGt() {
        var result = parse("price > 0");
        assertInstanceOf(InvariantExpr.BinaryOp.class, result);
        var op = (InvariantExpr.BinaryOp) result;
        assertEquals(InvariantExpr.BinOp.GT, op.op());
        assertInstanceOf(InvariantExpr.FieldRef.class, op.left());
        assertInstanceOf(InvariantExpr.IntLit.class, op.right());
    }

    @Test
    void parsesNullGuard() {
        var result = parse("email != null");
        assertInstanceOf(InvariantExpr.BinaryOp.class, result);
        var op = (InvariantExpr.BinaryOp) result;
        assertEquals(InvariantExpr.BinOp.NEQ, op.op());
        assertInstanceOf(InvariantExpr.FieldRef.class, op.left());
        assertEquals("email", ((InvariantExpr.FieldRef) op.left()).name());
        assertInstanceOf(InvariantExpr.NullLit.class, op.right());
    }

    @Test
    void parsesEqualityComparison() {
        var result = parse("status == ACTIVE");
        assertInstanceOf(InvariantExpr.BinaryOp.class, result);
        var op = (InvariantExpr.BinaryOp) result;
        assertEquals(InvariantExpr.BinOp.EQ, op.op());
    }

    // ── Logical operators ──────────────────────────────────────────────────────

    @Test
    void parsesLogicalAnd() {
        var result = parse("a > 0 && b < 10");
        assertInstanceOf(InvariantExpr.BinaryOp.class, result);
        var op = (InvariantExpr.BinaryOp) result;
        assertEquals(InvariantExpr.BinOp.AND, op.op());
        assertInstanceOf(InvariantExpr.BinaryOp.class, op.left());
        assertInstanceOf(InvariantExpr.BinaryOp.class, op.right());
    }

    @Test
    void parsesLogicalOr() {
        var result = parse("a > 0 || b > 0");
        assertInstanceOf(InvariantExpr.BinaryOp.class, result);
        assertEquals(InvariantExpr.BinOp.OR, ((InvariantExpr.BinaryOp) result).op());
    }

    @Test
    void parsesCompoundNullGuardAndComparison() {
        var result = parse("total != null && total > 0");
        assertInstanceOf(InvariantExpr.BinaryOp.class, result);
        var op = (InvariantExpr.BinaryOp) result;
        assertEquals(InvariantExpr.BinOp.AND, op.op());
        // Left: total != null
        var left = (InvariantExpr.BinaryOp) op.left();
        assertEquals(InvariantExpr.BinOp.NEQ, left.op());
        assertInstanceOf(InvariantExpr.NullLit.class, left.right());
    }

    // ── Unary operators ────────────────────────────────────────────────────────

    @Test
    void parsesUnaryNot() {
        var result = parse("!active");
        assertInstanceOf(InvariantExpr.UnaryOp.class, result);
        var op = (InvariantExpr.UnaryOp) result;
        assertEquals(InvariantExpr.UnOp.NOT, op.op());
        assertInstanceOf(InvariantExpr.FieldRef.class, op.operand());
    }

    // ── Parentheses and precedence ─────────────────────────────────────────────

    @Test
    void parsesParenthesizedExpression() {
        // (a + b) > 0: the ADD must be the child of GT, not the other way
        var result = parse("(a + b) > 0");
        assertInstanceOf(InvariantExpr.BinaryOp.class, result);
        var gt = (InvariantExpr.BinaryOp) result;
        assertEquals(InvariantExpr.BinOp.GT, gt.op());
        assertInstanceOf(InvariantExpr.BinaryOp.class, gt.left()); // a + b
        assertEquals(InvariantExpr.BinOp.ADD, ((InvariantExpr.BinaryOp) gt.left()).op());
    }

    @Test
    void parsesOperatorPrecedence() {
        // a + b * c → a + (b * c), not (a+b)*c
        var result = parse("a + b * c");
        assertInstanceOf(InvariantExpr.BinaryOp.class, result);
        var add = (InvariantExpr.BinaryOp) result;
        assertEquals(InvariantExpr.BinOp.ADD, add.op());
        assertInstanceOf(InvariantExpr.BinaryOp.class, add.right());
        assertEquals(InvariantExpr.BinOp.MUL, ((InvariantExpr.BinaryOp) add.right()).op());
    }

    // ── Aggregate calls ────────────────────────────────────────────────────────

    @Test
    void parsesAggregateCallExists() {
        var result = parse("exists(Customer, c => c.id == Order.customerId)");
        assertInstanceOf(InvariantExpr.AggregateCall.class, result);
        var agg = (InvariantExpr.AggregateCall) result;
        assertEquals("exists", agg.func());
        assertInstanceOf(InvariantExpr.EnumRef.class, agg.target()); // Customer → EnumRef
        assertEquals("c", agg.lambda().param());
        assertInstanceOf(InvariantExpr.BinaryOp.class, agg.lambda().body());
    }

    @Test
    void parsesAggregateCallCount() {
        var result = parse("count(items, item => item.price > 0)");
        assertInstanceOf(InvariantExpr.AggregateCall.class, result);
        var agg = (InvariantExpr.AggregateCall) result;
        assertEquals("count", agg.func());
        assertEquals("item", agg.lambda().param());
    }

    // ── Error handling ─────────────────────────────────────────────────────────

    @Test
    void returnsParseErrorOnEmptyInput() {
        var result = parse("");
        assertInstanceOf(InvariantExpr.ParseError.class, result);
    }

    @Test
    void returnsParseErrorOnInvalidToken() {
        var result = parse("price >> 0");
        assertInstanceOf(InvariantExpr.ParseError.class, result,
                "Double > should not parse: " + result);
    }

    @Test
    void returnsParseErrorOnUnmatchedParen() {
        var result = parse("(price > 0");
        assertInstanceOf(InvariantExpr.ParseError.class, result);
    }

    @Test
    void returnsParseErrorOnTrailingToken() {
        var result = parse("price > 0 )");
        assertInstanceOf(InvariantExpr.ParseError.class, result);
    }
}
