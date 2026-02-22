package com.genairus.chronos.validator.expr;

/**
 * Sealed AST for Chronos invariant expressions.
 *
 * <p>Spans are intentionally NOT stored in AST nodes — the invariant-level
 * {@link com.genairus.chronos.core.refs.Span} is used for all diagnostics.
 *
 * <h3>Grammar</h3>
 * <pre>
 *   orExpr    := andExpr ('||' andExpr)*
 *   andExpr   := eqExpr  ('&&' eqExpr)*
 *   eqExpr    := relExpr (('==' | '!=') relExpr)?
 *   relExpr   := addExpr (('&lt;' | '&lt;=' | '&gt;' | '&gt;=') addExpr)?
 *   addExpr   := mulExpr (('+' | '-') mulExpr)*
 *   mulExpr   := unaryExpr (('*' | '/') unaryExpr)*
 *   unaryExpr := ('!' | '-') unaryExpr | atom
 *   atom      := literal | fieldRef | enumRef | aggCall | '(' orExpr ')'
 *   aggCall   := IDENT '(' orExpr ',' IDENT '=&gt;' orExpr ')'
 *   literal   := INT | FLOAT | STRING | 'true' | 'false' | 'null'
 *   fieldRef  := lowerIdent ('.' lowerIdent)*
 *   enumRef   := UPPER_IDENT
 * </pre>
 */
public sealed interface InvariantExpr
        permits InvariantExpr.FieldRef,
                InvariantExpr.IntLit,
                InvariantExpr.FloatLit,
                InvariantExpr.StrLit,
                InvariantExpr.BoolLit,
                InvariantExpr.NullLit,
                InvariantExpr.EnumRef,
                InvariantExpr.BinaryOp,
                InvariantExpr.UnaryOp,
                InvariantExpr.AggregateCall,
                InvariantExpr.Lambda,
                InvariantExpr.ParseError {

    /** Binary operator kinds. */
    enum BinOp { OR, AND, EQ, NEQ, LT, LTE, GT, GTE, ADD, SUB, MUL, DIV }

    /** Unary operator kinds. */
    enum UnOp { NOT, NEG }

    /** A field reference: simple name ("price") or dot-path ("Order.total"). */
    record FieldRef(String name) implements InvariantExpr {}

    record IntLit(long value) implements InvariantExpr {}

    record FloatLit(double value) implements InvariantExpr {}

    record StrLit(String value) implements InvariantExpr {}

    record BoolLit(boolean value) implements InvariantExpr {}

    /** The literal {@code null}. */
    record NullLit() implements InvariantExpr {}

    /** A PascalCase identifier treated as an enum constant or type reference. */
    record EnumRef(String name) implements InvariantExpr {}

    record BinaryOp(BinOp op, InvariantExpr left, InvariantExpr right) implements InvariantExpr {}

    record UnaryOp(UnOp op, InvariantExpr operand) implements InvariantExpr {}

    /**
     * An aggregate function call: {@code count(items, item => item.price > 0)}.
     *
     * @param func   function name: count, sum, exists, forAll
     * @param target the collection expression (first argument)
     * @param lambda the lambda (second argument)
     */
    record AggregateCall(String func, InvariantExpr target, Lambda lambda) implements InvariantExpr {}

    /**
     * A lambda expression used inside aggregate calls: {@code item => item.price > 0}.
     *
     * @param param the lambda parameter name
     * @param body  the lambda body expression
     */
    record Lambda(String param, InvariantExpr body) implements InvariantExpr {}

    /**
     * Sentinel node produced when parsing fails.
     * CHR-042 is emitted at the call site; the type checker returns UNKNOWN for this node.
     *
     * @param message description of the parse error
     */
    record ParseError(String message) implements InvariantExpr {}
}
