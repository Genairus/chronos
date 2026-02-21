package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * An IR top-level deny definition — a prohibition on what the system must never do.
 *
 * @param name        the deny name (PascalCase)
 * @param traits      trait applications (e.g. {@code @compliance})
 * @param docComments documentation comment lines
 * @param description required description of the prohibition
 * @param scope       list of entity names this prohibition applies to
 * @param severity    severity level: critical, high, medium, or low
 * @param span        source location of the {@code deny} keyword
 */
public record DenyDef(
        String name,
        List<TraitApplication> traits,
        List<String> docComments,
        String description,
        List<String> scope,
        String severity,
        Span span) implements IrShape {}
