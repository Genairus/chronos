package com.genairus.chronos.model;

import java.util.List;
import java.util.Optional;

/**
 * A global business or regulatory constraint.
 *
 * <pre>
 *   @compliance("GDPR")
 *   policy DataRetention {
 *       description: "Personal data must be purged after 7 years of inactivity"
 *   }
 * </pre>
 *
 * @param name        the policy name (PascalCase)
 * @param description the policy description text
 * @param traits      trait applications (e.g. {@code @compliance("GDPR")})
 * @param docComments lines from preceding {@code ///} doc comments
 * @param location    source location of the {@code policy} keyword
 */
public record PolicyDef(
        String name,
        String description,
        List<TraitApplication> traits,
        List<String> docComments,
        SourceLocation location) implements ShapeDefinition {

    /**
     * Returns the compliance framework tag from a {@code @compliance} trait,
     * e.g. {@code "GDPR"}, {@code "PCI-DSS"}.
     */
    public Optional<String> complianceFramework() {
        return traits.stream()
                .filter(t -> "compliance".equals(t.name()))
                .flatMap(t -> t.firstPositionalValue().stream())
                .filter(v -> v instanceof TraitValue.StringValue)
                .map(v -> ((TraitValue.StringValue) v).value())
                .findFirst();
    }
}
