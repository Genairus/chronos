package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * Sealed base type for all top-level Chronos declarations (grammar rule: {@code shapeDef}).
 *
 * <p>Every permitted implementation is a record carrying a {@link Span} and a name.
 * Names are stored as raw strings — no symbol resolution has occurred at this layer.
 */
public sealed interface SyntaxDecl
        permits SyntaxEntityDecl, SyntaxShapeDecl, SyntaxListDecl, SyntaxMapDecl,
                SyntaxEnumDecl, SyntaxActorDecl, SyntaxPolicyDecl, SyntaxJourneyDecl,
                SyntaxRelationshipDecl, SyntaxInvariantDecl, SyntaxDenyDecl,
                SyntaxErrorDecl, SyntaxStateMachineDecl, SyntaxRoleDecl, SyntaxEventDecl {

    /** The declared name exactly as it appears in source. */
    String name();

    /**
     * Documentation lines from {@code ///} comments immediately preceding this declaration,
     * with the {@code ///} prefix (and one optional space) stripped.
     * Empty when no doc comments precede the declaration.
     */
    List<String> docComments();

    /** Source range covering the declaration keyword through its closing brace. */
    Span span();
}
