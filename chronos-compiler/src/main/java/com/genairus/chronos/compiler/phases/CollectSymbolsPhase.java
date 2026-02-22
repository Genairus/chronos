package com.genairus.chronos.compiler.phases;

import com.genairus.chronos.compiler.ResolverContext;
import com.genairus.chronos.compiler.symbols.Symbol;
import com.genairus.chronos.core.refs.NamespaceId;
import com.genairus.chronos.core.refs.QualifiedName;
import com.genairus.chronos.core.refs.ShapeId;
import com.genairus.chronos.core.refs.SymbolKind;
import com.genairus.chronos.syntax.*;

/**
 * Pass 2: Walks every top-level declaration in the {@link SyntaxModel} and
 * registers a {@link Symbol} for each one in the {@link com.genairus.chronos.compiler.symbols.SymbolTable}.
 *
 * <p>Duplicate names are detected here and reported as {@code CHR-005}.
 * The phase passes the {@code SyntaxModel} through unchanged.
 */
public final class CollectSymbolsPhase implements ResolverPhase<SyntaxModel, SyntaxModel> {

    @Override
    public SyntaxModel execute(SyntaxModel syntax, ResolverContext ctx) {
        NamespaceId ns = ctx.namespace();

        for (SyntaxDecl decl : syntax.declarations()) {
            SymbolKind kind = kindOf(decl);
            ShapeId id = new ShapeId(ns, decl.name());
            QualifiedName qname = QualifiedName.qualified(ns.value(), decl.name());
            Symbol symbol = new Symbol(kind, id, qname, decl.span(), decl);
            ctx.symbols().define(symbol, ctx.diagnostics());
        }

        return syntax;
    }

    private SymbolKind kindOf(SyntaxDecl decl) {
        return switch (decl) {
            case SyntaxEntityDecl       ignored -> SymbolKind.ENTITY;
            case SyntaxShapeDecl        ignored -> SymbolKind.STRUCT;
            case SyntaxEnumDecl         ignored -> SymbolKind.ENUM;
            case SyntaxListDecl         ignored -> SymbolKind.LIST;
            case SyntaxMapDecl          ignored -> SymbolKind.MAP;
            case SyntaxActorDecl        ignored -> SymbolKind.ACTOR;
            case SyntaxPolicyDecl       ignored -> SymbolKind.POLICY;
            case SyntaxJourneyDecl      ignored -> SymbolKind.JOURNEY;
            case SyntaxRelationshipDecl ignored -> SymbolKind.RELATIONSHIP;
            case SyntaxInvariantDecl    ignored -> SymbolKind.INVARIANT;
            case SyntaxDenyDecl         ignored -> SymbolKind.DENY;
            case SyntaxErrorDecl        ignored -> SymbolKind.ERROR;
            case SyntaxStateMachineDecl ignored -> SymbolKind.STATEMACHINE;
            case SyntaxRoleDecl         ignored -> SymbolKind.ROLE;
        };
    }
}
