package com.genairus.chronos.compiler.symbols;

import com.genairus.chronos.core.diagnostics.DiagnosticCollector;

import java.util.*;

/**
 * A cross-file symbol registry that indexes every top-level declaration across
 * all files in a multi-file compilation unit.
 *
 * <p>Built during the {@code IndexCompilationUnitPhase} before any resolution
 * passes run. Each symbol is keyed by its <em>fully-qualified name</em>
 * ({@code "namespace#name"} — the canonical {@link com.genairus.chronos.core.refs.ShapeId}
 * string form), ensuring uniqueness across namespaces.
 *
 * <h2>Duplicate detection</h2>
 * When {@link #define} is called with a fully-qualified name that already exists
 * (defined in a different file), a {@code CHR-014} diagnostic is emitted. The first
 * definition wins; subsequent duplicates are recorded as errors but not stored.
 *
 * <h2>Lookup helpers</h2>
 * Two lookup paths are provided for future cross-file resolution phases:
 * <ul>
 *   <li>{@link #lookupFq} — exact namespace + name match (unambiguous)</li>
 *   <li>{@link #lookupBySimpleName} — simple name match (may return multiple if
 *       different namespaces define the same simple name)</li>
 * </ul>
 */
public final class GlobalSymbolTable {

    /** Primary index: fqName ("namespace#name") → Symbol. */
    private final Map<String, Symbol> byFqName = new LinkedHashMap<>();

    /** Tracks which source path first defined each fqName; used in CHR-014 messages. */
    private final Map<String, String> fqNameToPath = new LinkedHashMap<>();

    /** Secondary index: simple name → all symbols with that simple name (across namespaces). */
    private final Map<String, List<Symbol>> bySimpleName = new LinkedHashMap<>();

    // ── Mutation ──────────────────────────────────────────────────────────────

    /**
     * Registers a symbol from a given source file.
     *
     * <p>If a symbol with the same fully-qualified name ({@code namespace#name})
     * was already registered from a <em>different</em> source file, a
     * {@code CHR-014} error is emitted and the first definition is kept.
     *
     * <p>The simple-name index is always updated (including duplicates) to
     * support ambiguity detection in future resolution phases.
     *
     * @param sym        the symbol to register
     * @param sourcePath logical path of the source file (used in diagnostic messages)
     * @param diag       collector that receives {@code CHR-014} errors on cross-file duplicates
     */
    public void define(Symbol sym, String sourcePath, DiagnosticCollector diag) {
        String fqName = sym.id().toString();   // "namespace#name"
        Symbol existing = byFqName.get(fqName);
        if (existing != null) {
            String firstPath = fqNameToPath.get(fqName);
            diag.error(
                    "CHR-014",
                    "Duplicate definition: '" + fqName + "' defined in "
                            + firstPath + " and " + sourcePath,
                    sym.span());
        } else {
            byFqName.put(fqName, sym);
            fqNameToPath.put(fqName, sourcePath);
        }
        // Always index by simple name — preserves all definitions for ambiguity analysis.
        bySimpleName.computeIfAbsent(sym.name().name(), k -> new ArrayList<>()).add(sym);
    }

    // ── Lookup ────────────────────────────────────────────────────────────────

    /**
     * Looks up a symbol by its exact namespace and simple name.
     *
     * @param namespace the namespace string (e.g. {@code "com.example.checkout"})
     * @param name      the simple shape name (e.g. {@code "Order"})
     * @return the symbol, or empty if not found
     */
    public Optional<Symbol> lookupFq(String namespace, String name) {
        return Optional.ofNullable(byFqName.get(namespace + "#" + name));
    }

    /**
     * Returns all symbols with the given simple name, regardless of namespace.
     *
     * <p>Returns more than one element when multiple namespaces define a shape
     * with the same simple name; returns zero elements if not found.
     *
     * @param name the simple shape name (e.g. {@code "Order"})
     * @return unmodifiable list of matching symbols, in registration order
     */
    public List<Symbol> lookupBySimpleName(String name) {
        return Collections.unmodifiableList(bySimpleName.getOrDefault(name, List.of()));
    }

    /** Returns the total number of unique fully-qualified names registered. */
    public int size() {
        return byFqName.size();
    }
}
