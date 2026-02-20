package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;

/**
 * Terminal outcome descriptions for a journey.
 *
 * @param successOrNull success outcome text, or {@code null} if absent
 * @param failureOrNull failure outcome text, or {@code null} if absent
 * @param span          source location of the outcomes block
 */
public record JourneyOutcomes(
        String successOrNull,
        String failureOrNull,
        Span span) {}
