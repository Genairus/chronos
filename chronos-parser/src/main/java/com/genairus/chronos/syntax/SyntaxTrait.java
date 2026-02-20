package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * A trait application (grammar rule: {@code traitApplication}).
 *
 * <p>Three forms: bare {@code @pii}, positional {@code @description("text")},
 * and named-arg {@code @kpi(metric: "X", target: "Y")}.
 */
public record SyntaxTrait(
        String name,
        List<SyntaxTraitArg> args,
        Span span
) {}
