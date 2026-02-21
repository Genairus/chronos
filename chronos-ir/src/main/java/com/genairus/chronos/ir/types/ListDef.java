package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * An IR named list collection shape.
 *
 * @param name        the list name (PascalCase)
 * @param traits      trait applications applied to this list
 * @param docComments lines from preceding {@code ///} doc comments
 * @param memberType  the element type reference
 * @param span        source location of the {@code list} keyword
 */
public record ListDef(
        String name,
        List<TraitApplication> traits,
        List<String> docComments,
        TypeRef memberType,
        Span span) implements IrShape {}
