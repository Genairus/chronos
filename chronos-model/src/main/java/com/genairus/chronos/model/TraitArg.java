package com.genairus.chronos.model;

/**
 * A single argument inside a trait application's argument list.
 *
 * <p>Arguments are either positional or named:
 * <pre>
 *   @description("text")              → TraitArg(key=null, value=StringValue("text"))
 *   @kpi(metric: "X", target: ">75%") → TraitArg(key="metric", value=StringValue("X"))
 * </pre>
 *
 * @param key      the argument name, or {@code null} for positional arguments
 * @param value    the argument value
 * @param location source location of this argument
 */
public record TraitArg(String key, TraitValue value, SourceLocation location) {

    /** Returns {@code true} if this is a named (key: value) argument. */
    public boolean isNamed() {
        return key != null;
    }
}
