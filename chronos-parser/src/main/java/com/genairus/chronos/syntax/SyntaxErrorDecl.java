package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * A typed error declaration (grammar rule: {@code errorDef}).
 *
 * <p>Example:
 * <pre>{@code
 * error PaymentDeclinedError {
 *     code: "PAY-001"
 *     severity: high
 *     recoverable: true
 *     message: "Payment was declined by the gateway"
 *     payload: { gatewayCode: String }
 * }
 * }</pre>
 *
 * <p>String fields default to empty string when absent; {@code payload} defaults to
 * an empty list when the {@code payload} block is absent.
 */
public record SyntaxErrorDecl(
        String name,
        List<String> docComments,
        String code,
        String severity,
        boolean recoverable,
        String message,
        List<SyntaxFieldDef> payload,
        List<SyntaxTrait> traits,
        Span span
) implements SyntaxDecl {}
