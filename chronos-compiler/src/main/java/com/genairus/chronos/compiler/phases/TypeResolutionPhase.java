package com.genairus.chronos.compiler.phases;

import com.genairus.chronos.compiler.ResolverContext;
import com.genairus.chronos.core.refs.Span;
import com.genairus.chronos.ir.model.IrModel;
import com.genairus.chronos.ir.types.*;

/**
 * Pass 4: Verifies that every {@link TypeRef.NamedTypeRef} in the model can be
 * resolved — either via the local {@link com.genairus.chronos.compiler.symbols.SymbolTable}
 * or via a {@code use} import declaration.
 *
 * <p>Reports {@code CHR-008} for each unresolved type reference.
 * The model is passed through unchanged.
 */
public final class TypeResolutionPhase implements ResolverPhase<IrModel, IrModel> {

    @Override
    public IrModel execute(IrModel model, ResolverContext ctx) {
        for (IrShape shape : model.shapes()) {
            switch (shape) {
                case EntityDef e ->
                        e.fields().forEach(f -> checkTypeRef(f.type(), e.name(), ctx));
                case ShapeStructDef s ->
                        s.fields().forEach(f -> checkTypeRef(f.type(), s.name(), ctx));
                case ListDef l ->
                        checkTypeRef(l.memberType(), l.name(), ctx);
                case MapDef m -> {
                    checkTypeRef(m.keyType(),   m.name(), ctx);
                    checkTypeRef(m.valueType(), m.name(), ctx);
                }
                default -> { /* other shapes have no type refs */ }
            }
        }
        return model;
    }

    private void checkTypeRef(TypeRef ref, String context, ResolverContext ctx) {
        switch (ref) {
            case TypeRef.NamedTypeRef n -> {
                if (!ctx.isResolvable(n.qualifiedId())) {
                    ctx.diagnostics().error(
                            "CHR-008",
                            "Unresolved type reference '" + n.qualifiedId() + "' in '" + context + "'",
                            Span.UNKNOWN);
                }
            }
            case TypeRef.ListType l -> checkTypeRef(l.elementType(), context, ctx);
            case TypeRef.MapType m -> {
                checkTypeRef(m.keyType(),   context, ctx);
                checkTypeRef(m.valueType(), context, ctx);
            }
            case TypeRef.PrimitiveType p -> { /* primitives always resolve */ }
        }
    }
}
