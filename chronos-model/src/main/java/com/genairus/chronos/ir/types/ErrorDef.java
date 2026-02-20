package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * An IR typed error definition with code, severity, recoverability, and optional payload.
 *
 * @param name        the error name (PascalCase)
 * @param traits      trait applications
 * @param docComments documentation comment lines
 * @param code        unique error code (e.g. "PAY-001")
 * @param severity    severity level (critical, high, medium, low)
 * @param recoverable whether the error condition is recoverable
 * @param message     human-readable error message
 * @param payload     optional payload fields (may be empty)
 * @param span        source location of the error name token
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
        Span span) implements IrShape {}
