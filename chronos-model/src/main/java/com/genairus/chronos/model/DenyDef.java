package com.genairus.chronos.model;

import java.util.List;

/**
 * A top-level deny definition expressing a prohibition — something the system must never do.
 *
 * <pre>
 *   deny StorePlaintextPasswords {
 *       description: "The system must never store passwords in plaintext"
 *       scope: [UserCredential]
 *       severity: critical
 *   }
 * </pre>
 *
 * @param name        the deny name (PascalCase by convention)
 * @param traits      trait applications on this deny (e.g. {@code @compliance})
 * @param docComments documentation comments (lines starting with ///)
 * @param description required description of the prohibition
 * @param scope       list of entity names this prohibition applies to
 * @param severity    severity level: critical, high, medium, or low
 * @param location    source location of the deny keyword
 */
public record DenyDef(
        String name,
        List<TraitApplication> traits,
        List<String> docComments,
        String description,
        List<String> scope,
        String severity,
        SourceLocation location) implements ShapeDefinition {}

