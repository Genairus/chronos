package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;
import com.genairus.chronos.core.refs.SymbolRef;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An IR journey definition — the central construct of the Chronos language.
 *
 * @param name          the journey name (PascalCase)
 * @param traits        trait applications (e.g. {@code @kpi}, {@code @owner})
 * @param docComments   lines from preceding {@code ///} doc comments
 * @param actorRef      unresolved ref to the declared actor, or {@code null} if absent (CHR-001)
 * @param preconditions the preconditions list (may be empty)
 * @param steps         the ordered happy-path steps
 * @param variants      named variant branches keyed by variant name
 * @param outcomesOrNull the terminal outcome descriptions, or {@code null} if absent (CHR-002)
 * @param span          source location of the {@code journey} keyword
 */
public record JourneyDef(
        String name,
        List<TraitApplication> traits,
        List<String> docComments,
        SymbolRef actorRef,
        List<String> preconditions,
        List<Step> steps,
        Map<String, Variant> variants,
        JourneyOutcomes outcomesOrNull,
        Span span) implements IrShape {

    /** Returns the actor ref, or empty if not declared (triggers CHR-001). */
    public Optional<SymbolRef> actorRefOpt() {
        return Optional.ofNullable(actorRef);
    }

    /**
     * Returns the actor's simple name as a string, or empty if not declared.
     * Uses the resolved name when available, otherwise falls back to the unresolved name.
     */
    public Optional<String> actorName() {
        if (actorRef == null) return Optional.empty();
        String name = actorRef.isResolved() ? actorRef.id().name() : actorRef.name().name();
        return Optional.of(name);
    }

    /** Returns the outcomes block, or empty if absent (triggers CHR-002). */
    public Optional<JourneyOutcomes> journeyOutcomes() {
        return Optional.ofNullable(outcomesOrNull);
    }
}
