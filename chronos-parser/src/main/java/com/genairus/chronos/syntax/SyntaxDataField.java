package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

/**
 * A named, typed data field used in step {@code input} and {@code output} blocks.
 *
 * @param name the field name (lowerCamelCase identifier)
 * @param type the declared type reference
 * @param span source location
 */
public record SyntaxDataField(String name, SyntaxTypeRef type, Span span) {}
