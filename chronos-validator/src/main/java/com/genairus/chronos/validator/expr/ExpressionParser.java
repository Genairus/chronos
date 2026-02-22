package com.genairus.chronos.validator.expr;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Recursive-descent parser for Chronos invariant expressions.
 *
 * <p>Returns an {@link InvariantExpr} AST. On parse failure, returns
 * {@link InvariantExpr.ParseError} — never throws.
 *
 * <h3>Token types produced by the tokenizer</h3>
 * <ul>
 *   <li>String literals: {@code "hello"} (including the surrounding quotes)
 *   <li>Integer/float literals: {@code 42}, {@code 3.14}
 *   <li>Two-char operators: {@code <=}, {@code >=}, {@code ==}, {@code !=},
 *       {@code ||}, {@code &&}, {@code =>}
 *   <li>Single-char punctuation: {@code < > ! + - * / ( ) ,}
 *   <li>Identifiers — including dot-path sequences joined by the tokenizer:
 *       {@code price}, {@code Order.customerId}, {@code c.id}
 * </ul>
 */
public class ExpressionParser {

    private static final Set<String> AGGREGATE_FUNCS =
            Set.of("count", "sum", "exists", "forAll");

    private List<String> tokens;
    private int pos;

    /**
     * Parses the given expression string.
     *
     * @param expression the invariant expression string
     * @return the parsed AST, or {@link InvariantExpr.ParseError} on failure
     */
    public InvariantExpr parse(String expression) {
        this.tokens = tokenize(expression);
        this.pos = 0;
        try {
            if (tokens.isEmpty()) {
                return new InvariantExpr.ParseError("Empty expression");
            }
            InvariantExpr result = parseOr();
            if (pos < tokens.size()) {
                return new InvariantExpr.ParseError(
                        "Unexpected token '" + tokens.get(pos) + "'");
            }
            return result;
        } catch (ParseException e) {
            return new InvariantExpr.ParseError(e.getMessage());
        }
    }

    // ── Tokenizer ─────────────────────────────────────────────────────────────

    private List<String> tokenize(String expr) {
        var result = new ArrayList<String>();
        int i = 0;
        int n = expr.length();

        while (i < n) {
            char c = expr.charAt(i);

            // Whitespace
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // String literal "..."
            if (c == '"') {
                int start = i++;
                while (i < n && expr.charAt(i) != '"') {
                    if (expr.charAt(i) == '\\') i++; // skip escape char
                    i++;
                }
                if (i < n) i++; // consume closing quote
                result.add(expr.substring(start, i));
                continue;
            }

            // Number: integer or float
            if (Character.isDigit(c)) {
                int start = i++;
                while (i < n && Character.isDigit(expr.charAt(i))) i++;
                if (i < n && expr.charAt(i) == '.'
                        && i + 1 < n && Character.isDigit(expr.charAt(i + 1))) {
                    i++; // consume '.'
                    while (i < n && Character.isDigit(expr.charAt(i))) i++;
                }
                result.add(expr.substring(start, i));
                continue;
            }

            // Identifier, optionally followed by dot-path components
            if (Character.isLetter(c) || c == '_') {
                int start = i++;
                while (i < n && isIdentChar(expr.charAt(i))) i++;
                // Join subsequent dot-path components
                while (i < n && expr.charAt(i) == '.'
                        && i + 1 < n
                        && (Character.isLetter(expr.charAt(i + 1)) || expr.charAt(i + 1) == '_')) {
                    i++; // consume '.'
                    while (i < n && isIdentChar(expr.charAt(i))) i++;
                }
                result.add(expr.substring(start, i));
                continue;
            }

            // Two-char operators (check before single-char)
            if (i + 1 < n) {
                String two = expr.substring(i, i + 2);
                if (two.equals("<=") || two.equals(">=") || two.equals("==")
                        || two.equals("!=") || two.equals("||") || two.equals("&&")
                        || two.equals("=>")) {
                    result.add(two);
                    i += 2;
                    continue;
                }
            }

            // Single-char punctuation and operators
            result.add(String.valueOf(c));
            i++;
        }
        return result;
    }

    private static boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    // ── Parser ────────────────────────────────────────────────────────────────

    /** orExpr := andExpr ('||' andExpr)* */
    private InvariantExpr parseOr() {
        InvariantExpr left = parseAnd();
        while (at("||")) {
            consume("||");
            InvariantExpr right = parseAnd();
            left = new InvariantExpr.BinaryOp(InvariantExpr.BinOp.OR, left, right);
        }
        return left;
    }

    /** andExpr := eqExpr ('&&' eqExpr)* */
    private InvariantExpr parseAnd() {
        InvariantExpr left = parseEq();
        while (at("&&")) {
            consume("&&");
            InvariantExpr right = parseEq();
            left = new InvariantExpr.BinaryOp(InvariantExpr.BinOp.AND, left, right);
        }
        return left;
    }

    /** eqExpr := relExpr (('==' | '!=') relExpr)? */
    private InvariantExpr parseEq() {
        InvariantExpr left = parseRel();
        if (at("==")) {
            consume("==");
            return new InvariantExpr.BinaryOp(InvariantExpr.BinOp.EQ, left, parseRel());
        }
        if (at("!=")) {
            consume("!=");
            return new InvariantExpr.BinaryOp(InvariantExpr.BinOp.NEQ, left, parseRel());
        }
        return left;
    }

    /** relExpr := addExpr (('<' | '<=' | '>' | '>=') addExpr)? */
    private InvariantExpr parseRel() {
        InvariantExpr left = parseAdd();
        if (at("<=")) {
            consume("<=");
            return new InvariantExpr.BinaryOp(InvariantExpr.BinOp.LTE, left, parseAdd());
        }
        if (at(">=")) {
            consume(">=");
            return new InvariantExpr.BinaryOp(InvariantExpr.BinOp.GTE, left, parseAdd());
        }
        if (at("<")) {
            consume("<");
            return new InvariantExpr.BinaryOp(InvariantExpr.BinOp.LT, left, parseAdd());
        }
        if (at(">")) {
            consume(">");
            return new InvariantExpr.BinaryOp(InvariantExpr.BinOp.GT, left, parseAdd());
        }
        return left;
    }

    /** addExpr := mulExpr (('+' | '-') mulExpr)* */
    private InvariantExpr parseAdd() {
        InvariantExpr left = parseMul();
        while (at("+") || at("-")) {
            InvariantExpr.BinOp op = at("+") ? InvariantExpr.BinOp.ADD : InvariantExpr.BinOp.SUB;
            consume();
            left = new InvariantExpr.BinaryOp(op, left, parseMul());
        }
        return left;
    }

    /** mulExpr := unaryExpr (('*' | '/') unaryExpr)* */
    private InvariantExpr parseMul() {
        InvariantExpr left = parseUnary();
        while (at("*") || at("/")) {
            InvariantExpr.BinOp op = at("*") ? InvariantExpr.BinOp.MUL : InvariantExpr.BinOp.DIV;
            consume();
            left = new InvariantExpr.BinaryOp(op, left, parseUnary());
        }
        return left;
    }

    /** unaryExpr := ('!' | '-') unaryExpr | atom */
    private InvariantExpr parseUnary() {
        if (at("!")) {
            consume("!");
            return new InvariantExpr.UnaryOp(InvariantExpr.UnOp.NOT, parseUnary());
        }
        if (at("-")) {
            consume("-");
            return new InvariantExpr.UnaryOp(InvariantExpr.UnOp.NEG, parseUnary());
        }
        return parseAtom();
    }

    /**
     * atom := '(' orExpr ')' | literal | fieldRef | enumRef | aggCall | funcCall
     *
     * <ul>
     *   <li>PascalCase identifier (no dot) → {@link InvariantExpr.EnumRef}
     *   <li>Dot-path or lowercase identifier → {@link InvariantExpr.FieldRef}
     *   <li>Aggregate function (count/sum/exists/forAll) followed by '(' → {@link InvariantExpr.AggregateCall}
     *   <li>Other identifier followed by '(' → treated as opaque function call, returns EnumRef
     * </ul>
     */
    private InvariantExpr parseAtom() {
        if (!hasMore()) {
            throw new ParseException("Unexpected end of expression");
        }

        // Parenthesised expression
        if (at("(")) {
            consume("(");
            InvariantExpr inner = parseOr();
            consume(")");
            return inner;
        }

        String tok = peek();

        // String literal
        if (tok.startsWith("\"")) {
            consume();
            // Strip quotes and return
            String value = tok.length() >= 2 ? tok.substring(1, tok.length() - 1) : "";
            return new InvariantExpr.StrLit(value);
        }

        // Number literal
        if (!tok.isEmpty() && Character.isDigit(tok.charAt(0))) {
            consume();
            if (tok.contains(".")) {
                return new InvariantExpr.FloatLit(Double.parseDouble(tok));
            } else {
                return new InvariantExpr.IntLit(Long.parseLong(tok));
            }
        }

        // Identifier
        if (!tok.isEmpty() && (Character.isLetter(tok.charAt(0)) || tok.charAt(0) == '_')) {
            consume();

            // Keywords
            if (tok.equals("true"))  return new InvariantExpr.BoolLit(true);
            if (tok.equals("false")) return new InvariantExpr.BoolLit(false);
            if (tok.equals("null"))  return new InvariantExpr.NullLit();

            // Aggregate function call
            if (AGGREGATE_FUNCS.contains(tok) && at("(")) {
                return parseAggregateCall(tok);
            }

            // Any other identifier followed by '(' — treat as opaque function call
            // (won't trigger CHR-019 field-reference checks)
            if (at("(")) {
                parseGenericFunctionArgs();
                return new InvariantExpr.EnumRef(tok);
            }

            // Dot-path identifier → FieldRef (even if starts with uppercase like "Order.field")
            if (tok.contains(".")) {
                return new InvariantExpr.FieldRef(tok);
            }

            // Plain uppercase identifier → EnumRef
            if (Character.isUpperCase(tok.charAt(0))) {
                return new InvariantExpr.EnumRef(tok);
            }

            // Plain lowercase identifier → FieldRef
            return new InvariantExpr.FieldRef(tok);
        }

        throw new ParseException("Unexpected token '" + tok + "'");
    }

    /**
     * Parses an aggregate call after the function name has already been consumed.
     * Expected form: {@code func(targetExpr, param => bodyExpr)}
     */
    private InvariantExpr parseAggregateCall(String func) {
        consume("(");
        InvariantExpr target = parseOr();
        consume(",");

        // Lambda: param '=>' body
        if (!hasMore() || !isIdentifier(peek())) {
            throw new ParseException("Expected lambda parameter in " + func + "(...)");
        }
        String param = consume();
        consume("=>");
        InvariantExpr body = parseOr();
        consume(")");

        return new InvariantExpr.AggregateCall(func, target,
                new InvariantExpr.Lambda(param, body));
    }

    /**
     * Consumes a generic function call argument list {@code (expr, expr, ...)}.
     * The results are discarded; these function calls are treated as opaque.
     */
    private void parseGenericFunctionArgs() {
        consume("(");
        if (!at(")")) {
            parseOr();
            while (at(",")) {
                consume(",");
                parseOr();
            }
        }
        consume(")");
    }

    // ── Token helpers ──────────────────────────────────────────────────────────

    private boolean hasMore() {
        return pos < tokens.size();
    }

    private String peek() {
        return tokens.get(pos);
    }

    private boolean at(String expected) {
        return hasMore() && tokens.get(pos).equals(expected);
    }

    private String consume() {
        return tokens.get(pos++);
    }

    private String consume(String expected) {
        if (!hasMore() || !tokens.get(pos).equals(expected)) {
            String found = hasMore() ? "'" + tokens.get(pos) + "'" : "end of expression";
            throw new ParseException("Expected '" + expected + "' but found " + found);
        }
        return tokens.get(pos++);
    }

    private static boolean isIdentifier(String tok) {
        if (tok.isEmpty()) return false;
        return Character.isLetter(tok.charAt(0)) || tok.charAt(0) == '_';
    }

    // ── Internal exception ─────────────────────────────────────────────────────

    private static final class ParseException extends RuntimeException {
        ParseException(String message) {
            super(message);
        }
    }
}
