package com.genairus.chronos.validator.expr;

/**
 * The inferred type of an invariant expression node.
 *
 * <p>{@link #UNKNOWN} is used when the type cannot be determined (e.g. unresolved
 * field reference, lambda parameter, or {@link InvariantExpr.ParseError}).
 * The type checker suppresses CHR-043 whenever either operand is {@code UNKNOWN}
 * to avoid false positives.
 */
public enum ExprType {
    BOOLEAN,
    INTEGER,
    FLOAT,
    STRING,
    TIMESTAMP,
    /** Non-navigable opaque type (Blob, Document, enum constant). */
    OPAQUE,
    /** Type cannot be determined at validation time. */
    UNKNOWN
}
