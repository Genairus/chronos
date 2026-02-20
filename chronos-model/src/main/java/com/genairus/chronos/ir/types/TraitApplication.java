package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;

import java.util.List;
import java.util.Optional;

/**
 * An IR trait application (e.g. {@code @kpi(metric: "m", target: "t")}).
 *
 * @param name the trait name (without the leading {@code @})
 * @param args the positional or named arguments
 * @param span source location of the trait annotation
 */
public record TraitApplication(String name, List<TraitArg> args, Span span) {

    /** Returns the value of the first argument with the given key, if any. */
    public Optional<TraitValue> namedValue(String key) {
        return args.stream()
                .filter(a -> key.equals(a.keyOrNull()))
                .map(TraitArg::value)
                .findFirst();
    }

    /** Returns the value of the first positional (unnamed) argument, if any. */
    public Optional<TraitValue> firstPositionalValue() {
        return args.stream()
                .filter(a -> a.keyOrNull() == null)
                .map(TraitArg::value)
                .findFirst();
    }
}
