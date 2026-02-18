package com.genairus.chronos.model;

import java.util.List;

/**
 * A closed enumeration of named values.
 *
 * <pre>
 *   enum OrderStatus {
 *       PENDING = 1
 *       PAID    = 2
 *       SHIPPED = 3
 *   }
 * </pre>
 *
 * @param name        the enum name (PascalCase)
 * @param traits      trait applications applied to this enum
 * @param docComments lines from preceding {@code ///} doc comments
 * @param members     the ordered list of enum members
 * @param location    source location of the {@code enum} keyword
 */
public record EnumDef(
        String name,
        List<TraitApplication> traits,
        List<String> docComments,
        List<EnumMember> members,
        SourceLocation location) implements ShapeDefinition {}
