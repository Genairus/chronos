package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;

import java.util.List;
import java.util.Optional;

/**
 * An IR policy definition — a global business or regulatory constraint.
 *
 * @param name        the policy name (PascalCase)
 * @param traits      trait applications (e.g. {@code @compliance})
 * @param docComments lines from preceding {@code ///} doc comments
 * @param description the required policy description text
 * @param span        source location of the {@code policy} keyword
 */
public record PolicyDef(
        String name,
        List<TraitApplication> traits,
        List<String> docComments,
        String description,
        Span span) implements IrShape {

    /** Returns the compliance framework tag from a {@code @compliance} trait, if present. */
    public Optional<String> complianceFramework() {
        return traits.stream()
                .filter(t -> "compliance".equals(t.name()))
                .flatMap(t -> t.firstPositionalValue().stream())
                .filter(v -> v instanceof TraitValue.StringValue)
                .map(v -> ((TraitValue.StringValue) v).value())
                .findFirst();
    }
}
