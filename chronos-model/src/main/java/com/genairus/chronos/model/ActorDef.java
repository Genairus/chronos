package com.genairus.chronos.model;

import java.util.List;
import java.util.Optional;

/**
 * A standalone actor declaration — who or what interacts with the system.
 *
 * <pre>
 *   @description("A registered user authenticated via SSO")
 *   actor AuthenticatedUser
 * </pre>
 *
 * @param name        the actor name (PascalCase)
 * @param traits      trait applications (typically includes {@code @description})
 * @param docComments lines from preceding {@code ///} doc comments
 * @param location    source location of the {@code actor} keyword
 */
public record ActorDef(
        String name,
        List<TraitApplication> traits,
        List<String> docComments,
        SourceLocation location) implements ShapeDefinition {

    /**
     * Returns the description from a {@code @description} trait, if present.
     * Validator rule CHR-007 warns when this is absent.
     */
    public Optional<String> description() {
        return traits.stream()
                .filter(t -> "description".equals(t.name()))
                .flatMap(t -> t.firstPositionalValue().stream())
                .filter(v -> v instanceof TraitValue.StringValue)
                .map(v -> ((TraitValue.StringValue) v).value())
                .findFirst();
    }
}
