package com.genairus.chronos.compiler.imports;

import com.genairus.chronos.compiler.symbols.GlobalSymbolTable;
import com.genairus.chronos.compiler.symbols.Symbol;
import com.genairus.chronos.core.diagnostics.DiagnosticCollector;
import com.genairus.chronos.syntax.SyntaxUseDecl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Binds the {@code use} import declarations of a single source file to their
 * target symbols in the {@link GlobalSymbolTable}.
 *
 * <h2>Resolution rules</h2>
 * <ol>
 *   <li>Each {@code use} statement introduces one simple name into the file's
 *       import scope.</li>
 *   <li>If the import carries an explicit namespace ({@code use ns#Name}):
 *       the target fqName is {@code ns#Name}.</li>
 *   <li>If the import carries no namespace ({@code use Name} — same-namespace
 *       shorthand): the target fqName is {@code fileNamespace#Name}.</li>
 *   <li>If the target fqName does not exist in the global table →
 *       {@code CHR-016} error; the simple name is not bound.</li>
 *   <li>If two imports bind the <em>same</em> simple name to <em>different</em>
 *       targets → {@code CHR-017} error; the first binding is kept.
 *       Duplicate imports of the identical target are silently ignored.</li>
 * </ol>
 *
 * <h2>Precedence</h2>
 * Import bindings are applied <em>after</em> local symbol collection; callers
 * (e.g. {@link com.genairus.chronos.compiler.ResolverContext#resolveSimpleName})
 * must check local symbols first.
 */
public final class ImportResolver {

    /**
     * Binds all {@code use} declarations for one source file.
     *
     * @param fileNamespace the namespace of the file owning these imports
     * @param uses          the file's {@code use} declarations in source order
     * @param global        the fully-populated global symbol table
     * @return the binding result, including any CHR-016/CHR-017 diagnostics
     */
    public ImportBindings bindImports(
            String fileNamespace,
            List<SyntaxUseDecl> uses,
            GlobalSymbolTable global) {

        DiagnosticCollector diag     = new DiagnosticCollector();
        Map<String, Symbol> bindings = new LinkedHashMap<>();

        for (SyntaxUseDecl use : uses) {
            var qn = use.name();

            // Determine target namespace: explicit or same-namespace shorthand.
            String targetNs   = qn.isQualified() ? qn.namespaceOrNull() : fileNamespace;
            String simpleName = qn.name();

            Optional<Symbol> found = global.lookupFq(targetNs, simpleName);

            if (found.isEmpty()) {
                diag.error(
                        "CHR-016",
                        "Unknown import '" + targetNs + "#" + simpleName
                                + "' (from use " + qn + ")",
                        use.span());
                continue;
            }

            Symbol sym = found.get();
            if (bindings.containsKey(simpleName)) {
                Symbol existing = bindings.get(simpleName);
                if (!existing.id().equals(sym.id())) {
                    diag.error(
                            "CHR-017",
                            "Ambiguous import for '" + simpleName
                                    + "': imports both '" + existing.id()
                                    + "' and '" + sym.id() + "'",
                            use.span());
                }
                // Identical re-import: silently ignored.
            } else {
                bindings.put(simpleName, sym);
            }
        }

        return new ImportBindings(
                fileNamespace,
                Map.copyOf(bindings),
                List.copyOf(diag.all()));
    }
}
