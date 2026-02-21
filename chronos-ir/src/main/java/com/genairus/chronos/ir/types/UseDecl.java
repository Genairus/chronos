package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;

/**
 * An IR {@code use} import declaration.
 *
 * @param namespace the namespace portion (may be empty for local imports)
 * @param name      the shape name being imported
 * @param span      source location of the use declaration
 */
public record UseDecl(String namespace, String name, Span span) {}
