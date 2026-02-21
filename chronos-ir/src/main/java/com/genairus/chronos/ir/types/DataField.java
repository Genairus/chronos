package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;

/**
 * A named, typed data field used in step {@code input} and {@code output} blocks.
 *
 * @param name the field name (lowerCamelCase identifier)
 * @param type the declared type reference (resolved after type-resolution phase)
 * @param span source location
 */
public record DataField(String name, TypeRef type, Span span) {}
