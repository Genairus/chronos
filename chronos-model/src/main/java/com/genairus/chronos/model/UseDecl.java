package com.genairus.chronos.model;

/**
 * Represents a {@code use} import statement, e.g.:
 * <pre>
 *   use com.genairus.data.orders#Order
 * </pre>
 *
 * @param namespace the dot-separated namespace portion (e.g. {@code com.genairus.data.orders})
 * @param shapeName the shape being imported (e.g. {@code Order})
 * @param location  source location of the {@code use} keyword
 */
public record UseDecl(String namespace, String shapeName, SourceLocation location) {

    /** Returns the fully-qualified shape ID, e.g. {@code com.genairus.data.orders#Order}. */
    public String qualifiedId() {
        return namespace + "#" + shapeName;
    }
}
