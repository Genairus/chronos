package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * Root syntax DTO — the lowered form of a {@code .chronos} file
 * (grammar rule: {@code model}).
 *
 * <p>Contains the namespace declaration, all {@code use} imports, and
 * the ordered list of top-level shape declarations. All names are raw strings;
 * no symbol resolution has occurred.
 */
public record SyntaxModel(
        String namespace,
        List<SyntaxUseDecl> imports,
        List<SyntaxDecl> declarations,
        Span span
) {}
