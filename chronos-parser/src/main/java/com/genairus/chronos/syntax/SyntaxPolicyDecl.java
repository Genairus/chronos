package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * A policy declaration (grammar rule: {@code policyDef}).
 *
 * <p>Example:
 * <pre>{@code
 * @compliance("GDPR")
 * policy DataRetention {
 *     description: "Personal data must be purged after 7 years"
 * }
 * }</pre>
 */
public record SyntaxPolicyDecl(
        String name,
        List<String> docComments,
        String description,
        List<SyntaxTrait> traits,
        Span span
) implements SyntaxDecl {}
