package com.genairus.chronos.model;

/**
 * The value side of a trait argument.
 *
 * <p>Trait values may be a string literal, a number, a boolean, or a
 * reference to a named shape (qualified identifier). Examples:
 * <pre>
 *   @description("text")              → StringValue("text")
 *   @slo(latency: "2s", p99: true)   → StringValue("2s"), BoolValue(true)
 *   @range(min: 0, max: 100)         → NumberValue(0), NumberValue(100)
 *   @compliance("GDPR")              → StringValue("GDPR")
 * </pre>
 */
public sealed interface TraitValue
        permits TraitValue.StringValue,
                TraitValue.NumberValue,
                TraitValue.BoolValue,
                TraitValue.ReferenceValue {

    /** A double-quoted string literal: {@code "text"}. */
    record StringValue(String value) implements TraitValue {}

    /** An integer or floating-point literal: {@code 42}, {@code 3.14}. */
    record NumberValue(double value) implements TraitValue {}

    /** A boolean literal: {@code true} or {@code false}. */
    record BoolValue(boolean value) implements TraitValue {}

    /**
     * A qualified identifier referencing a named shape or namespace.
     * e.g. {@code com.example.OrderStatus}.
     */
    record ReferenceValue(String qualifiedId) implements TraitValue {}
}
