package com.genairus.chronos.syntax;

import com.genairus.chronos.core.refs.QualifiedName;
import com.genairus.chronos.core.refs.Span;

/**
 * A {@code use} import declaration (grammar rule: {@code useDecl}).
 *
 * <p>Example: {@code use com.example.actors#Customer}
 * produces {@code QualifiedName.qualified("com.example.actors", "Customer")}.
 */
public record SyntaxUseDecl(
        QualifiedName name,
        Span span
) {}
