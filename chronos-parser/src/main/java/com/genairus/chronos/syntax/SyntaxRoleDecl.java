package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * Syntax DTO for a {@code role} declaration.
 *
 * <pre>
 *   role AdminRole {
 *       allow: [create, read, update, delete]
 *       deny:  [admin_delete]
 *   }
 * </pre>
 *
 * @param name                the role name (PascalCase)
 * @param docComments         lines from preceding {@code ///} doc comments
 * @param allowedPermissions  permission names in the {@code allow} list
 * @param deniedPermissions   permission names in the {@code deny} list (may be empty)
 * @param traits              trait applications applied to this role
 * @param span                source location of the {@code role} keyword
 */
public record SyntaxRoleDecl(
        String name,
        List<String> docComments,
        List<String> allowedPermissions,
        List<String> deniedPermissions,
        List<SyntaxTrait> traits,
        Span span
) implements SyntaxDecl {}
