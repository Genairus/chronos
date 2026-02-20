package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;
import com.genairus.chronos.core.refs.SymbolRef;

import java.util.List;
import java.util.Optional;

/**
 * An IR entity definition — a business object with identity.
 *
 * @param name        the entity name (PascalCase)
 * @param traits      trait applications applied to this entity
 * @param docComments lines from preceding {@code ///} doc comments
 * @param parentRef   optional ref to the parent entity (for inheritance); unresolved until
 *                    {@code CrossLinkResolutionPhase}, resolved thereafter when finalized
 * @param fields      the ordered list of field declarations
 * @param invariants  the ordered list of entity-scoped invariants
 * @param span        source location of the {@code entity} keyword
 */
public record EntityDef(
        String name,
        List<TraitApplication> traits,
        List<String> docComments,
        Optional<SymbolRef> parentRef,
        List<FieldDef> fields,
        List<EntityInvariant> invariants,
        Span span) implements IrShape {}
