package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;
import com.genairus.chronos.core.refs.SymbolRef;

import java.util.List;
import java.util.Optional;

/**
 * An IR actor declaration — who or what interacts with the system.
 *
 * @param name        the actor name (PascalCase)
 * @param traits      trait applications applied to this actor
 * @param docComments lines from preceding {@code ///} doc comments
 * @param parentRef   optional ref to the parent actor (for inheritance); unresolved until
 *                    {@code CrossLinkResolutionPhase}, resolved thereafter when finalized
 * @param span        source location of the {@code actor} keyword
 */
public record ActorDef(
        String name,
        List<TraitApplication> traits,
        List<String> docComments,
        Optional<SymbolRef> parentRef,
        Span span) implements IrShape {

    /** Returns the description from a {@code @description} trait, if present. */
    public Optional<String> description() {
        return traits.stream()
                .filter(t -> "description".equals(t.name()))
                .flatMap(t -> t.firstPositionalValue().stream())
                .filter(v -> v instanceof TraitValue.StringValue)
                .map(v -> ((TraitValue.StringValue) v).value())
                .findFirst();
    }
}
