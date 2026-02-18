package com.genairus.chronos.model;

import java.util.List;
import java.util.Optional;

/**
 * An applied trait on a shape, field, or step.
 *
 * <p>Three syntactic forms from the spec:
 * <pre>
 *   @pii                              → bare (no args)
 *   @description("A user")           → single positional arg
 *   @kpi(metric: "X", target: ">75%")→ named args
 * </pre>
 *
 * @param name     the trait name without the {@code @} prefix (e.g. {@code kpi})
 * @param args     the argument list; empty for bare traits
 * @param location source location of the {@code @} symbol
 */
public record TraitApplication(String name, List<TraitArg> args, SourceLocation location) {

    /** Returns {@code true} if this trait was applied without arguments. */
    public boolean isBare() {
        return args.isEmpty();
    }

    /**
     * Returns the first positional argument value, if present.
     * Useful for single-arg traits like {@code @description("text")}.
     */
    public Optional<TraitValue> firstPositionalValue() {
        return args.stream()
                .filter(a -> !a.isNamed())
                .map(TraitArg::value)
                .findFirst();
    }

    /**
     * Returns the value of the named argument with the given key, if present.
     * Useful for multi-arg traits like {@code @kpi(metric: "X", target: "Y")}.
     */
    public Optional<TraitValue> namedValue(String key) {
        return args.stream()
                .filter(a -> a.isNamed() && key.equals(a.key()))
                .map(TraitArg::value)
                .findFirst();
    }
}
