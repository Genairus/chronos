package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * An IR enumeration definition — a closed set of named values.
 *
 * @param name        the enum name (PascalCase)
 * @param traits      trait applications applied to this enum
 * @param docComments lines from preceding {@code ///} doc comments
 * @param members     the ordered list of enum members
 * @param span        source location of the {@code enum} keyword
 */
public record EnumDef(
        String name,
        List<TraitApplication> traits,
        List<String> docComments,
        List<EnumMember> members,
        Span span) implements IrShape {}
