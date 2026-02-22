package com.genairus.chronos.validator.expr;

import com.genairus.chronos.core.diagnostics.Diagnostic;
import com.genairus.chronos.core.refs.Span;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Infers the {@link ExprType} of an {@link InvariantExpr} node and emits
 * CHR-043 diagnostics for type mismatches.
 *
 * <p>CHR-043 is a WARNING. It is only emitted when BOTH sides of an operator
 * have known (non-{@link ExprType#UNKNOWN}) types that are incompatible, so
 * unresolved field references (already reported by CHR-019) never cause false
 * positives here.
 *
 * <p>Usage:
 * <pre>
 *   new ExprTypeChecker().check(ast, fieldTypes, Set.of(), span, issues);
 * </pre>
 */
public class ExprTypeChecker {

    /**
     * Infers the type of {@code expr} and records CHR-043 diagnostics for any
     * detected type mismatches.
     *
     * @param expr        the expression AST to check
     * @param fieldTypes  map from field name (as it appears in {@link InvariantExpr.FieldRef})
     *                    to its {@link ExprType}
     * @param lambdaScope set of lambda parameter names currently in scope
     *                    (these resolve to {@link ExprType#UNKNOWN})
     * @param invName     invariant name for CHR-043 messages
     * @param span        span for CHR-043 diagnostics
     * @param issues      collector for CHR-043 diagnostics
     * @return the inferred type of the expression
     */
    public ExprType check(InvariantExpr expr, Map<String, ExprType> fieldTypes,
                          Set<String> lambdaScope, String invName,
                          Span span, List<Diagnostic> issues) {
        return switch (expr) {
            case InvariantExpr.ParseError ignored -> ExprType.UNKNOWN;
            case InvariantExpr.NullLit ignored    -> ExprType.BOOLEAN;
            case InvariantExpr.BoolLit ignored    -> ExprType.BOOLEAN;
            case InvariantExpr.IntLit ignored     -> ExprType.INTEGER;
            case InvariantExpr.FloatLit ignored   -> ExprType.FLOAT;
            case InvariantExpr.StrLit ignored     -> ExprType.STRING;
            case InvariantExpr.EnumRef ignored    -> ExprType.OPAQUE;

            case InvariantExpr.FieldRef f -> resolveFieldType(f.name(), fieldTypes, lambdaScope);

            case InvariantExpr.UnaryOp u -> checkUnary(u, fieldTypes, lambdaScope, invName, span, issues);

            case InvariantExpr.BinaryOp b -> checkBinary(b, fieldTypes, lambdaScope, invName, span, issues);

            case InvariantExpr.Lambda lam -> {
                // Extend lambda scope with the param
                var newScope = extendScope(lambdaScope, lam.param());
                check(lam.body(), fieldTypes, newScope, invName, span, issues);
                yield ExprType.UNKNOWN;
            }

            case InvariantExpr.AggregateCall agg -> {
                check(agg.target(), fieldTypes, lambdaScope, invName, span, issues);
                var lambda = agg.lambda();
                var newScope = extendScope(lambdaScope, lambda.param());
                ExprType bodyType = check(lambda.body(), fieldTypes, newScope, invName, span, issues);
                yield switch (agg.func()) {
                    case "sum"  -> isNumeric(bodyType) ? bodyType : ExprType.UNKNOWN;
                    default     -> ExprType.BOOLEAN; // count, exists, forAll
                };
            }
        };
    }

    // ── Unary ────────────────────────────────────────────────────────────────

    private ExprType checkUnary(InvariantExpr.UnaryOp u,
                                 Map<String, ExprType> fieldTypes,
                                 Set<String> lambdaScope,
                                 String invName, Span span,
                                 List<Diagnostic> issues) {
        ExprType operandType = check(u.operand(), fieldTypes, lambdaScope, invName, span, issues);
        return switch (u.op()) {
            case NOT -> ExprType.BOOLEAN;
            case NEG -> isNumeric(operandType) ? operandType : ExprType.UNKNOWN;
        };
    }

    // ── Binary ────────────────────────────────────────────────────────────────

    private ExprType checkBinary(InvariantExpr.BinaryOp b,
                                  Map<String, ExprType> fieldTypes,
                                  Set<String> lambdaScope,
                                  String invName, Span span,
                                  List<Diagnostic> issues) {
        ExprType left  = check(b.left(),  fieldTypes, lambdaScope, invName, span, issues);
        ExprType right = check(b.right(), fieldTypes, lambdaScope, invName, span, issues);

        return switch (b.op()) {
            case OR, AND -> {
                checkBothBoolean(left, right, b.op().name().toLowerCase(), invName, span, issues);
                yield ExprType.BOOLEAN;
            }
            case EQ, NEQ -> {
                checkCompatible(left, right, b.op() == InvariantExpr.BinOp.EQ ? "==" : "!=",
                        invName, span, issues);
                yield ExprType.BOOLEAN;
            }
            case LT, LTE, GT, GTE -> {
                checkBothNumericOrUnknown(left, right, b.op().name().toLowerCase(),
                        invName, span, issues);
                yield ExprType.BOOLEAN;
            }
            case ADD, SUB, MUL, DIV -> {
                checkBothNumericOrUnknown(left, right, b.op().name().toLowerCase(),
                        invName, span, issues);
                yield widenNumeric(left, right);
            }
        };
    }

    // ── Type rules ────────────────────────────────────────────────────────────

    private void checkBothBoolean(ExprType left, ExprType right, String op,
                                   String invName, Span span, List<Diagnostic> issues) {
        if (isKnown(left) && left != ExprType.BOOLEAN) {
            issues.add(chr043(invName, span,
                    "operator '" + op + "' requires boolean operands but left side is " + left));
        }
        if (isKnown(right) && right != ExprType.BOOLEAN) {
            issues.add(chr043(invName, span,
                    "operator '" + op + "' requires boolean operands but right side is " + right));
        }
    }

    private void checkCompatible(ExprType left, ExprType right, String op,
                                  String invName, Span span, List<Diagnostic> issues) {
        if (isKnown(left) && isKnown(right) && left != right) {
            // Allow OPAQUE on either side (enum constant comparisons)
            if (left == ExprType.OPAQUE || right == ExprType.OPAQUE) return;
            issues.add(chr043(invName, span,
                    "operator '" + op + "' compares incompatible types "
                    + left + " and " + right));
        }
    }

    private void checkBothNumericOrUnknown(ExprType left, ExprType right, String op,
                                            String invName, Span span,
                                            List<Diagnostic> issues) {
        if (isKnown(left) && !isNumeric(left)) {
            issues.add(chr043(invName, span,
                    "operator '" + op + "' requires numeric operands but left side is " + left));
        }
        if (isKnown(right) && !isNumeric(right)) {
            issues.add(chr043(invName, span,
                    "operator '" + op + "' requires numeric operands but right side is " + right));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ExprType resolveFieldType(String name, Map<String, ExprType> fieldTypes,
                                       Set<String> lambdaScope) {
        // If the first component of the name is a lambda param, it's opaque
        String prefix = name.contains(".") ? name.substring(0, name.indexOf('.')) : name;
        if (lambdaScope.contains(prefix)) {
            return ExprType.UNKNOWN;
        }
        ExprType type = fieldTypes.get(name);
        return type != null ? type : ExprType.UNKNOWN;
    }

    private static boolean isNumeric(ExprType t) {
        return t == ExprType.INTEGER || t == ExprType.FLOAT;
    }

    private static boolean isKnown(ExprType t) {
        return t != ExprType.UNKNOWN;
    }

    private static ExprType widenNumeric(ExprType l, ExprType r) {
        if (l == ExprType.FLOAT || r == ExprType.FLOAT) return ExprType.FLOAT;
        if (l == ExprType.INTEGER || r == ExprType.INTEGER) return ExprType.INTEGER;
        return ExprType.UNKNOWN;
    }

    private static Set<String> extendScope(Set<String> existing, String param) {
        if (existing.isEmpty()) return Set.of(param);
        var extended = new java.util.HashSet<>(existing);
        extended.add(param);
        return extended;
    }

    private static Diagnostic chr043(String invName, Span span, String detail) {
        return Diagnostic.warning("CHR-043",
                "Type mismatch in invariant '" + invName + "': " + detail,
                span != null ? span : Span.UNKNOWN);
    }
}
