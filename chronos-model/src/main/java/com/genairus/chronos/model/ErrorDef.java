package com.genairus.chronos.model;

import java.util.List;

/**
 * A typed error definition with code, severity, recoverability, and optional payload.
 *
 * <pre>
 *   error PaymentDeclinedError {
 *       code: "PAY-001"
 *       severity: high
 *       recoverable: true
 *       message: "Payment was declined by the gateway"
 *       payload: {
 *           gatewayCode: String
 *           retryable: Boolean
 *       }
 *   }
 * </pre>
 *
 * @param name         the error name (PascalCase)
 * @param traits       trait applications (e.g., @description)
 * @param docComments  documentation comments
 * @param code         unique error code (e.g., "PAY-001")
 * @param severity     severity level (critical, high, medium, low)
 * @param recoverable  whether the error condition is recoverable
 * @param message      human-readable error message
 * @param payload      optional payload fields (may be empty)
 * @param location     source location of the error name token
 */
public record ErrorDef(
        String name,
        List<TraitApplication> traits,
        List<String> docComments,
        String code,
        String severity,
        boolean recoverable,
        String message,
        List<FieldDef> payload,
        SourceLocation location) implements ShapeDefinition {}

