package com.genairus.chronos.compiler;

import com.genairus.chronos.compiler.symbols.GlobalSymbolTable;
import com.genairus.chronos.compiler.symbols.Symbol;
import com.genairus.chronos.compiler.symbols.SymbolTable;
import com.genairus.chronos.core.diagnostics.DiagnosticCollector;
import com.genairus.chronos.core.refs.NamespaceId;
import com.genairus.chronos.core.refs.QualifiedName;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared state threaded through every resolver pass.
 *
 * <p>A new {@code ResolverContext} is created once per compilation unit,
 * populated by {@code CollectSymbolsPhase}, and then passed (read-only)
 * to all subsequent phases.
 *
 * @param namespace        the namespace declared at the top of the source file
 * @param uses             the shapes listed in {@code use} import declarations
 *                         (raw qualified names — kept for backward compat with
 *                         {@code TypeResolutionPhase} and {@code CrossLinkResolutionPhase})
 * @param symbols          the symbol table built during Collect Symbols (Pass 2)
 * @param diagnostics      the collector that accumulates all compiler diagnostics
 * @param importedSymbols  simple-name → {@link Symbol} map produced by
 *                         {@link com.genairus.chronos.compiler.imports.ImportResolver};
 *                         {@code Map.of()} for single-file compiles and until
 *                         Phase 3 import binding runs
 * @param globalSymbols    the cross-file symbol registry built by the index pass;
 *                         an empty table for single-file compiles
 */
public record ResolverContext(
        NamespaceId namespace,
        List<QualifiedName> uses,
        SymbolTable symbols,
        DiagnosticCollector diagnostics,
        Map<String, Symbol> importedSymbols,
        GlobalSymbolTable globalSymbols) {

    /**
     * Resolves a name using the full resolution chain:
     * <ol>
     *   <li>If {@code nameText} contains {@code '#'}: exact fully-qualified lookup
     *       ({@code "namespace#Name"}) in the global symbol table.</li>
     *   <li>If {@code nameText} contains {@code '.'}: split on the last dot and
     *       perform a fully-qualified global lookup ({@code "namespace.subns.Name"}).</li>
     *   <li>Otherwise (simple name):
     *     <ol>
     *       <li>Local {@link SymbolTable} (defined in this file).</li>
     *       <li>Import bindings ({@link #importedSymbols}) — populated in Phase 3.</li>
     *       <li>Same-namespace fallback in {@link GlobalSymbolTable}.</li>
     *     </ol>
     *   </li>
     * </ol>
     *
     * @param nameText the name to resolve; may be simple, dot-qualified, or hash-qualified
     * @return the matching {@link Symbol}, or empty if not found
     */
    public Optional<Symbol> resolveName(String nameText) {
        // '#' separator: "namespace#Name"
        int hashIdx = nameText.indexOf('#');
        if (hashIdx >= 0) {
            return globalSymbols.lookupFq(
                    nameText.substring(0, hashIdx),
                    nameText.substring(hashIdx + 1));
        }
        // Dot-qualified: split on last dot — "com.example.Name"
        int lastDot = nameText.lastIndexOf('.');
        if (lastDot >= 0) {
            return globalSymbols.lookupFq(
                    nameText.substring(0, lastDot),
                    nameText.substring(lastDot + 1));
        }
        // Simple name: local → imports → global same-namespace fallback
        Optional<Symbol> local = symbols.lookup(nameText);
        if (local.isPresent()) return local;
        Symbol imported = importedSymbols.get(nameText);
        if (imported != null) return Optional.of(imported);
        return globalSymbols.lookupFq(namespace.value(), nameText);
    }

    /**
     * Resolves a simple name using local-first precedence.
     *
     * <p>Delegates to {@link #resolveName}: returns the first match found in
     * local symbols, import bindings, or the global same-namespace fallback.
     *
     * @param simpleName the unqualified shape name to look up
     * @return the matching {@link Symbol}, or empty if not found in any scope
     */
    public Optional<Symbol> resolveSimpleName(String simpleName) {
        return resolveName(simpleName);
    }

    /**
     * Returns {@code true} if the given simple name is resolvable in this context.
     *
     * @param simpleName the unqualified shape name to look up
     */
    public boolean isResolvable(String simpleName) {
        return resolveName(simpleName).isPresent();
    }
}
