package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * A deny (negative requirement) declaration (grammar rule: {@code denyDef}).
 *
 * <p>Example:
 * <pre>{@code
 * deny StorePlaintextPasswords {
 *     description: "The system must never store passwords in plaintext"
 *     scope: [UserCredential]
 *     severity: critical
 * }
 * }</pre>
 */
public record SyntaxDenyDecl(
        String name,
        List<String> docComments,
        String description,
        List<String> scope,
        String severity,
        List<SyntaxTrait> traits,
        Span span
) implements SyntaxDecl {}
