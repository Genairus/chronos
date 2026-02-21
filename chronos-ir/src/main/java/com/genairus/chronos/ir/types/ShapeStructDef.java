package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * An IR shape struct definition — a lightweight value object without identity.
 *
 * @param name        the shape name (PascalCase)
 * @param traits      trait applications applied to this shape
 * @param docComments lines from preceding {@code ///} doc comments
 * @param fields      the ordered list of field declarations
 * @param span        source location of the {@code shape} keyword
 */
public record ShapeStructDef(
        String name,
        List<TraitApplication> traits,
        List<String> docComments,
        List<FieldDef> fields,
        Span span) implements IrShape {}
