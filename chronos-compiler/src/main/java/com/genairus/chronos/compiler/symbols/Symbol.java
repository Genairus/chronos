package com.genairus.chronos.compiler.symbols;

import com.genairus.chronos.core.refs.QualifiedName;
import com.genairus.chronos.core.refs.ShapeId;
import com.genairus.chronos.core.refs.Span;
import com.genairus.chronos.core.refs.SymbolKind;
import com.genairus.chronos.syntax.SyntaxDecl;

/**
 * A resolved entry in the {@link SymbolTable}.
 *
 * <p>Every top-level declaration in a {@code .chronos} file corresponds to
 * exactly one {@code Symbol} after the Collect Symbols phase completes.
 *
 * @param kind       the declaration kind (entity, journey, actor, …)
 * @param id         canonical identity of this shape (e.g. {@code com.example#Order})
 * @param name       raw source name as a {@link QualifiedName}
 * @param span       source location of the declaration name token
 * @param syntaxDecl the syntax DTO node that produced this symbol; may be {@code null}
 *                   for symbols defined outside the current compilation unit
 */
public record Symbol(
        SymbolKind kind,
        ShapeId id,
        QualifiedName name,
        Span span,
        SyntaxDecl syntaxDecl) {

    @Override
    public String toString() {
        return "Symbol[" + kind + " " + id + " @" + span + "]";
    }
}
