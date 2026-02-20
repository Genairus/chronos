package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;

import java.util.List;
import java.util.Optional;

/**
 * An IR global invariant definition — a boolean constraint spanning multiple entities.
 *
 * @param name        the invariant name (PascalCase)
 * @param traits      trait applications applied to this invariant
 * @param docComments lines from preceding {@code ///} doc comments
 * @param scope       list of entity names referenced in the expression
 * @param expression  the boolean expression as a string
 * @param severity    the severity level (error, warning, info)
 * @param message     optional custom error message
 * @param span        source location of the {@code invariant} keyword
 */
public record InvariantDef(
        String name,
        List<TraitApplication> traits,
        List<String> docComments,
        List<String> scope,
        String expression,
        String severity,
        Optional<String> message,
        Span span) implements IrShape {}
