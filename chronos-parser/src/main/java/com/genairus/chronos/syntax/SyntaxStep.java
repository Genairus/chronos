package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * A step within a journey or variant (grammar rule: {@code step}).
 *
 * <p>Steps may carry trait applications (e.g. {@code @slo}) and any combination
 * of {@code action}, {@code expectation}, {@code outcome}, {@code telemetry},
 * and {@code risk} fields.
 */
public record SyntaxStep(
        String name,
        List<SyntaxTrait> traits,
        List<SyntaxStepField> fields,
        Span span
) {}
