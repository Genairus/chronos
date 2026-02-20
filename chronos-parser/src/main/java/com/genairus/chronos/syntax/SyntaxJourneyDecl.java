package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * A journey declaration (grammar rule: {@code journeyDef}).
 *
 * <p>All body fields are optional at parse time — semantic requirements
 * (actor, outcomes, steps) are enforced later by the validator (CHR-001 etc.).
 *
 * <p>{@code actorOrNull} is {@code null} when the {@code actor:} clause is absent.
 * {@code outcomesOrNull} is {@code null} when the {@code outcomes:} block is absent.
 */
public record SyntaxJourneyDecl(
        String name,
        List<String> docComments,
        String actorOrNull,
        List<String> preconditions,
        List<SyntaxStep> steps,
        List<SyntaxVariant> variants,
        SyntaxOutcomes outcomesOrNull,
        List<SyntaxTrait> traits,
        Span span
) implements SyntaxDecl {}
