package com.genairus.chronos.compiler.resolve;

import com.genairus.chronos.compiler.ResolverContext;
import com.genairus.chronos.compiler.symbols.Symbol;
import com.genairus.chronos.core.refs.SymbolKind;
import com.genairus.chronos.core.refs.SymbolRef;

import java.util.Optional;
import java.util.Set;

/**
 * Stateless utility for resolving {@link SymbolRef} instances against the
 * current compilation unit's symbol table.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * SymbolRef resolved = RefResolver.resolve(
 *     ctx,
 *     rel.fromEntityRef(),
 *     Set.of(SymbolKind.ENTITY),
 *     "CHR-011",
 *     "Relationship 'OrderItems' references undefined entity '%s' in 'from' field");
 * }</pre>
 *
 * <p>The {@code messageTemplate} must contain exactly one {@code %s} placeholder,
 * which is substituted with the simple name of the unresolved reference
 * (e.g. {@code "Order"}).  Embed any other contextual names — such as the
 * declaring shape's name — into the template string before calling this method.
 */
public final class RefResolver {

    private RefResolver() {}

    /**
     * Attempts to resolve {@code ref} against the symbol table in {@code ctx}.
     *
     * <ul>
     *   <li>If {@code ref} is {@code null}, returns {@code null}.</li>
     *   <li>If {@code ref.isResolved()}, returns {@code ref} unchanged.</li>
     *   <li>Otherwise looks up {@code ref.name().name()} in the symbol table.
     *       <ul>
     *         <li>If a symbol is found whose kind is in {@code expectedKinds},
     *             returns a new resolved {@link SymbolRef} via
     *             {@link SymbolRef#resolved(SymbolKind, com.genairus.chronos.core.refs.ShapeId, com.genairus.chronos.core.refs.Span)
     *             SymbolRef.resolved(kind, id, span)}.</li>
     *         <li>If not found (or wrong kind), emits an {@code errorCode}
     *             diagnostic using {@link String#format(String, Object...) String.format(messageTemplate, simpleName)}
     *             and returns {@code ref} unchanged.</li>
     *       </ul>
     *   </li>
     * </ul>
     *
     * @param ctx              shared resolver context carrying the symbol table and diagnostic sink
     * @param ref              the reference to resolve; may be {@code null}
     * @param expectedKinds    the set of {@link SymbolKind}s that constitute a valid resolution
     * @param errorCode        CHR-xxx diagnostic code emitted on resolution failure
     * @param messageTemplate  diagnostic message template with a single {@code %s}
     *                         placeholder for the unresolved simple name
     * @return a resolved {@link SymbolRef} on success, {@code null} if {@code ref}
     *         was {@code null}, or the original (unresolved) {@code ref} on failure
     */
    public static SymbolRef resolve(
            ResolverContext ctx,
            SymbolRef ref,
            Set<SymbolKind> expectedKinds,
            String errorCode,
            String messageTemplate) {

        if (ref == null) {
            return null;
        }
        if (ref.isResolved()) {
            return ref;
        }

        String simpleName = ref.name().name();
        Optional<Symbol> found = ctx.resolveName(simpleName);

        if (found.isPresent() && expectedKinds.contains(found.get().kind())) {
            Symbol sym = found.get();
            return SymbolRef.resolved(sym.kind(), sym.id(), ref.span());
        }

        ctx.diagnostics().error(
                errorCode,
                String.format(messageTemplate, simpleName),
                ref.span());
        return ref;
    }
}
