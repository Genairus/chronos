package com.genairus.chronos.compiler;

import com.genairus.chronos.compiler.symbols.SymbolTable;
import com.genairus.chronos.core.diagnostics.DiagnosticCollector;
import com.genairus.chronos.core.refs.NamespaceId;
import com.genairus.chronos.core.refs.QualifiedName;

import java.util.List;

/**
 * Shared state threaded through every resolver pass.
 *
 * <p>A new {@code ResolverContext} is created once per compilation unit,
 * populated by {@code CollectSymbolsPhase}, and then passed (read-only)
 * to all subsequent phases.
 *
 * @param namespace   the namespace declared at the top of the source file
 * @param uses        the shapes listed in {@code use} import declarations
 * @param symbols     the symbol table built during Collect Symbols (Pass 2)
 * @param diagnostics the collector that accumulates all compiler diagnostics
 */
public record ResolverContext(
        NamespaceId namespace,
        List<QualifiedName> uses,
        SymbolTable symbols,
        DiagnosticCollector diagnostics) {

    /**
     * Returns {@code true} if the given simple name is either defined locally
     * or listed as an import.
     *
     * @param simpleName the unqualified shape name to look up
     */
    public boolean isResolvable(String simpleName) {
        if (symbols.contains(simpleName)) {
            return true;
        }
        return uses.stream().anyMatch(q -> q.name().equals(simpleName));
    }
}
