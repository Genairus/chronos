package com.genairus.chronos.model;

import java.util.List;

/**
 * A top-level shape definition — a lightweight value object without identity.
 *
 * <p>Structurally identical to {@link EntityDef} but semantically distinct:
 * shapes describe <em>what</em> something is, entities describe <em>which one</em>.
 *
 * <pre>
 *   shape Money {
 *       @required
 *       amount: Float
 *       currency: String
 *   }
 * </pre>
 *
 * @param name        the shape name (PascalCase)
 * @param traits      trait applications applied to this shape
 * @param docComments lines from preceding {@code ///} doc comments
 * @param fields      the ordered list of field declarations
 * @param location    source location of the {@code shape} keyword
 */
public record ShapeStructDef(
        String name,
        List<TraitApplication> traits,
        List<String> docComments,
        List<FieldDef> fields,
        SourceLocation location) implements ShapeDefinition {}
