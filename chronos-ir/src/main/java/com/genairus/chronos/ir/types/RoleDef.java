package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * An IR role declaration — a named permission set used for authorization.
 *
 * <pre>
 *   role AdminRole {
 *       allow: [create, read, update, delete]
 *       deny:  [admin_delete]
 *   }
 * </pre>
 *
 * @param name                the role name (PascalCase)
 * @param traits              trait applications applied to this role
 * @param docComments         lines from preceding {@code ///} doc comments
 * @param allowedPermissions  permission names in the {@code allow} list
 * @param deniedPermissions   permission names in the {@code deny} list (may be empty)
 * @param span                source location of the {@code role} keyword
 */
public record RoleDef(
        String name,
        List<TraitApplication> traits,
        List<String> docComments,
        List<String> allowedPermissions,
        List<String> deniedPermissions,
        Span span) implements IrShape {}
