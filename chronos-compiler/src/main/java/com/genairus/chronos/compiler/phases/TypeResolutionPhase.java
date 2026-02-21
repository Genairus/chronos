package com.genairus.chronos.compiler.phases;

import com.genairus.chronos.compiler.ResolverContext;
import com.genairus.chronos.compiler.symbols.Symbol;
import com.genairus.chronos.core.diagnostics.DiagnosticCollector;
import com.genairus.chronos.core.refs.QualifiedName;
import com.genairus.chronos.core.refs.ShapeId;
import com.genairus.chronos.core.refs.SymbolKind;
import com.genairus.chronos.core.refs.SymbolRef;
import com.genairus.chronos.ir.model.IrModel;
import com.genairus.chronos.ir.types.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Pass 4: Resolves every {@link TypeRef.NamedTypeRef} in the model, replacing each
 * unresolved {@link SymbolRef} with a resolved one, and returns a new {@link IrModel}.
 *
 * <p>Reports {@code CHR-013} for each type reference that cannot be resolved —
 * either because the name is unknown or because the referenced symbol is not a
 * valid type kind (ENTITY, STRUCT, ENUM, LIST, or MAP).
 *
 * <p>Affected shapes: {@link EntityDef}, {@link ShapeStructDef}, {@link ListDef},
 * {@link MapDef}, and {@link ErrorDef} (payload fields). All other shapes are
 * passed through unchanged.
 */
public final class TypeResolutionPhase implements ResolverPhase<IrModel, IrModel> {

    /** Shape kinds that are valid in type-reference position. */
    private static final Set<SymbolKind> VALID_TYPE_KINDS = Set.of(
            SymbolKind.ENTITY, SymbolKind.STRUCT, SymbolKind.ENUM,
            SymbolKind.LIST, SymbolKind.MAP);

    @Override
    public IrModel execute(IrModel model, ResolverContext ctx) {
        List<IrShape> updated = new ArrayList<>(model.shapes().size());
        for (IrShape shape : model.shapes()) {
            updated.add(switch (shape) {
                case EntityDef e      -> resolveEntity(e, ctx);
                case ShapeStructDef s -> resolveShapeStruct(s, ctx);
                case ListDef l        -> resolveList(l, ctx);
                case MapDef m         -> resolveMap(m, ctx);
                case ErrorDef e       -> resolveError(e, ctx);
                default               -> shape;
            });
        }
        return new IrModel(model.namespace(), model.imports(), updated);
    }

    // ── Per-shape resolvers ───────────────────────────────────────────────────

    private EntityDef resolveEntity(EntityDef e, ResolverContext ctx) {
        List<FieldDef> resolved = resolveFields(e.fields(), ctx);
        if (resolved == e.fields()) return e;
        return new EntityDef(
                e.name(), e.traits(), e.docComments(),
                e.parentRef(), resolved, e.invariants(), e.span());
    }

    private ShapeStructDef resolveShapeStruct(ShapeStructDef s, ResolverContext ctx) {
        List<FieldDef> resolved = resolveFields(s.fields(), ctx);
        if (resolved == s.fields()) return s;
        return new ShapeStructDef(s.name(), s.traits(), s.docComments(), resolved, s.span());
    }

    private ListDef resolveList(ListDef l, ResolverContext ctx) {
        TypeRef resolved = resolveTypeRef(l.memberType(), l.name(), ctx);
        if (resolved == l.memberType()) return l;
        return new ListDef(l.name(), l.traits(), l.docComments(), resolved, l.span());
    }

    private MapDef resolveMap(MapDef m, ResolverContext ctx) {
        TypeRef resolvedKey   = resolveTypeRef(m.keyType(),   m.name(), ctx);
        TypeRef resolvedValue = resolveTypeRef(m.valueType(), m.name(), ctx);
        if (resolvedKey == m.keyType() && resolvedValue == m.valueType()) return m;
        return new MapDef(m.name(), m.traits(), m.docComments(), resolvedKey, resolvedValue, m.span());
    }

    private ErrorDef resolveError(ErrorDef e, ResolverContext ctx) {
        List<FieldDef> resolved = resolveFields(e.payload(), ctx);
        if (resolved == e.payload()) return e;
        return new ErrorDef(
                e.name(), e.traits(), e.docComments(),
                e.code(), e.severity(), e.recoverable(), e.message(),
                resolved, e.span());
    }

    // ── Field / TypeRef resolution ────────────────────────────────────────────

    /**
     * Resolves all fields in the list. Returns the original list instance if no
     * field changed (identity comparison), allowing callers to skip rebuilding.
     */
    private List<FieldDef> resolveFields(List<FieldDef> fields, ResolverContext ctx) {
        boolean changed = false;
        List<FieldDef> result = new ArrayList<>(fields.size());
        for (FieldDef field : fields) {
            TypeRef resolvedType = resolveTypeRef(field.type(), field.name(), ctx);
            if (resolvedType != field.type()) {
                changed = true;
                result.add(new FieldDef(field.name(), resolvedType, field.traits(), field.span()));
            } else {
                result.add(field);
            }
        }
        return changed ? result : fields;
    }

    /**
     * Recursively resolves a {@link TypeRef}. Returns the original instance if no
     * change was made (identity comparison).
     *
     * @param typeRef  the type reference to resolve
     * @param context  human-readable context name (field or shape name) for diagnostics
     */
    private TypeRef resolveTypeRef(TypeRef typeRef, String context, ResolverContext ctx) {
        return switch (typeRef) {
            case TypeRef.NamedTypeRef n -> resolveNamedTypeRef(n, context, ctx);
            case TypeRef.ListType l -> {
                TypeRef resolvedElem = resolveTypeRef(l.elementType(), context, ctx);
                yield resolvedElem == l.elementType() ? l : new TypeRef.ListType(resolvedElem);
            }
            case TypeRef.MapType m -> {
                TypeRef resolvedKey   = resolveTypeRef(m.keyType(),   context, ctx);
                TypeRef resolvedValue = resolveTypeRef(m.valueType(), context, ctx);
                yield (resolvedKey == m.keyType() && resolvedValue == m.valueType())
                        ? m : new TypeRef.MapType(resolvedKey, resolvedValue);
            }
            case TypeRef.PrimitiveType p -> p; // primitives always resolve
        };
    }

    /**
     * Attempts to resolve a single {@link TypeRef.NamedTypeRef}.
     *
     * <ol>
     *   <li>If already resolved, returns the ref unchanged.</li>
     *   <li>Looks up the simple name in the local symbol table.
     *       <ul>
     *         <li>Found with a valid kind → returns a new resolved {@code NamedTypeRef}.</li>
     *         <li>Found with an invalid kind → emits CHR-013 and returns the original.</li>
     *       </ul>
     *   </li>
     *   <li>Checks {@code use} import declarations — synthesizes a resolved ref of
     *       kind {@code TYPE} (exact kind is unknown for external shapes).</li>
     *   <li>Not found → emits CHR-013 and returns the original.</li>
     * </ol>
     */
    private TypeRef.NamedTypeRef resolveNamedTypeRef(
            TypeRef.NamedTypeRef namedRef, String context, ResolverContext ctx) {

        SymbolRef ref = namedRef.ref();
        if (ref.isResolved()) {
            return namedRef;
        }

        String simpleName        = ref.name().name();
        DiagnosticCollector diag = ctx.diagnostics();

        // ── 1. Global-aware resolution (local → imports → global same-namespace) ──
        Optional<Symbol> found = ctx.resolveName(simpleName);
        if (found.isPresent()) {
            Symbol sym = found.get();
            if (VALID_TYPE_KINDS.contains(sym.kind())) {
                return new TypeRef.NamedTypeRef(
                        SymbolRef.resolved(sym.kind(), sym.id(), ref.span()));
            }
            diag.error("CHR-013",
                    "Type '" + simpleName + "' in '" + context
                            + "' is not a valid type (kind: " + sym.kind() + ")",
                    ref.span());
            return namedRef;
        }

        // ── 2. Use imports ────────────────────────────────────────────────────
        Optional<QualifiedName> imported = ctx.uses().stream()
                .filter(q -> q.name().equals(simpleName))
                .findFirst();
        if (imported.isPresent()) {
            QualifiedName q = imported.get();
            String ns = q.namespaceOrNull() != null
                    ? q.namespaceOrNull()
                    : ctx.namespace().value();
            return new TypeRef.NamedTypeRef(
                    SymbolRef.resolved(SymbolKind.TYPE, ShapeId.of(ns, simpleName), ref.span()));
        }

        // ── 3. Unresolvable ───────────────────────────────────────────────────
        diag.error("CHR-013",
                "Unknown type reference '" + simpleName + "' in '" + context + "'",
                ref.span());
        return namedRef;
    }
}
