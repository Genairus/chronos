package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * An actor declaration (grammar rule: {@code actorDef}).
 *
 * <p>Example: {@code @description("A registered user") actor AuthenticatedUser extends BaseUser}
 * <p>{@code parentOrNull} is {@code null} when no {@code extends} clause is present.
 */
public record SyntaxActorDecl(
        String name,
        List<String> docComments,
        String parentOrNull,
        List<SyntaxTrait> traits,
        Span span
) implements SyntaxDecl {}
