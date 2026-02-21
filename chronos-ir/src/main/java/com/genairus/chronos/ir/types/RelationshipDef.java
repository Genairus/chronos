package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;
import com.genairus.chronos.core.refs.SymbolRef;

import java.util.List;
import java.util.Optional;

/**
 * An IR first-class relationship declaration between two entities.
 *
 * @param name           the relationship name (PascalCase)
 * @param traits         trait applications on this relationship
 * @param docComments    documentation comment lines
 * @param fromEntityRef  unresolved ref to the source entity
 * @param toEntityRef    unresolved ref to the target entity
 * @param cardinality    the cardinality constraint
 * @param semantics      the relationship semantics (optional, defaults to ASSOCIATION)
 * @param inverseField   the name of the inverse field on the target entity (optional)
 * @param span           source location of the relationship name token
 */
public record RelationshipDef(
        String name,
        List<TraitApplication> traits,
        List<String> docComments,
        SymbolRef fromEntityRef,
        SymbolRef toEntityRef,
        Cardinality cardinality,
        Optional<RelationshipSemantics> semantics,
        Optional<String> inverseField,
        Span span) implements IrShape {

    /** Returns the effective semantics, defaulting to ASSOCIATION if not specified. */
    public RelationshipSemantics effectiveSemantics() {
        return semantics.orElse(RelationshipSemantics.ASSOCIATION);
    }

    /** Returns the one-line summary from a {@code @description} trait, if present. */
    public Optional<String> description() {
        return traits.stream()
                .filter(t -> "description".equals(t.name()))
                .flatMap(t -> t.firstPositionalValue().stream())
                .filter(v -> v instanceof TraitValue.StringValue)
                .map(v -> ((TraitValue.StringValue) v).value())
                .findFirst();
    }
}
