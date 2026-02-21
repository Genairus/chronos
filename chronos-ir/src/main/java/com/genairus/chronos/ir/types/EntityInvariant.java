package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;

import java.util.Optional;

/**
 * An entity-scoped invariant in the IR type system.
 *
 * @param name       invariant identifier
 * @param expression OCL-like invariant expression
 * @param severity   severity string (e.g. {@code "error"})
 * @param message    optional human-readable failure message
 * @param span       source location
 */
public record EntityInvariant(
        String name,
        String expression,
        String severity,
        Optional<String> message,
        Span span) {}
