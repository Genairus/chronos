package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;

/**
 * A single argument in an IR trait application.
 *
 * @param keyOrNull named key, or {@code null} for positional arguments
 * @param value     the argument value
 * @param span      source location
 */
public record TraitArg(String keyOrNull, TraitValue value, Span span) {}
