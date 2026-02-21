package com.genairus.chronos.compiler.imports;

import com.genairus.chronos.compiler.symbols.Symbol;
import com.genairus.chronos.core.diagnostics.Diagnostic;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The result of binding the {@code use} import declarations of a single source file
 * against the fully-populated {@link com.genairus.chronos.compiler.symbols.GlobalSymbolTable}.
 *
 * <p>Produced by {@link ImportResolver} after the global index pass has completed
 * (so every file's symbols are available for lookup).
 *
 * <h2>Diagnostics</h2>
 * Only import-binding diagnostics are stored here:
 * <ul>
 *   <li>{@code CHR-016} — a {@code use} target does not exist in the global table</li>
 *   <li>{@code CHR-017} — two imports bind the same simple name to different targets</li>
 * </ul>
 *
 * @param fileNamespace the namespace of the file that owns these bindings
 * @param bindings      map from simple name to the resolved {@link Symbol};
 *                      contains only successfully bound imports (no entry for
 *                      CHR-016 or CHR-017 failures)
 * @param diagnostics   CHR-016/CHR-017 errors produced during binding
 */
public record ImportBindings(
        String fileNamespace,
        Map<String, Symbol> bindings,
        List<Diagnostic> diagnostics) {

    /** Returns an empty binding set with no diagnostics (used when a file fails to parse). */
    public static ImportBindings empty(String fileNamespace) {
        return new ImportBindings(fileNamespace, Map.of(), List.of());
    }

    /**
     * Looks up an import by its simple name.
     *
     * @param simpleName the unqualified shape name (e.g. {@code "Order"})
     * @return the bound {@link Symbol}, or empty if not imported
     */
    public Optional<Symbol> lookup(String simpleName) {
        return Optional.ofNullable(bindings.get(simpleName));
    }

    /** Returns {@code true} if any CHR-016 or CHR-017 diagnostics were produced. */
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(
                d -> "CHR-016".equals(d.code()) || "CHR-017".equals(d.code()));
    }
}
