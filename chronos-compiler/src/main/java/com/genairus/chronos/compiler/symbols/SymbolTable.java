package com.genairus.chronos.compiler.symbols;

import com.genairus.chronos.core.diagnostics.DiagnosticCollector;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A flat registry mapping simple shape names to their {@link Symbol} entries
 * within a single compilation unit.
 *
 * <p>The table is populated during the Collect Symbols phase (Pass 2).  Once
 * populated it is read-only for all subsequent passes.
 *
 * <h2>Duplicate detection</h2>
 * When {@link #define} is called with a name that already exists, a
 * {@code CHR-005} diagnostic is reported via the supplied
 * {@link DiagnosticCollector} and the <em>first</em> definition wins
 * (subsequent duplicates are silently ignored after the error is recorded).
 */
public final class SymbolTable {

    /** Primary index: simple name → Symbol. */
    private final Map<String, Symbol> byName = new LinkedHashMap<>();

    // ── Mutation ──────────────────────────────────────────────────────────────

    /**
     * Registers a new symbol.  If a symbol with the same name already exists,
     * a {@code CHR-005} diagnostic is reported and the existing entry is kept.
     *
     * @param symbol the symbol to define
     * @param diag   collector that receives duplicate-name errors
     */
    public void define(Symbol symbol, DiagnosticCollector diag) {
        String simpleName = symbol.name().name();
        Symbol existing = byName.get(simpleName);
        if (existing != null) {
            diag.error("CHR-005",
                    "Duplicate shape name '" + simpleName + "'",
                    symbol.span());
        } else {
            byName.put(simpleName, symbol);
        }
    }

    // ── Lookup ────────────────────────────────────────────────────────────────

    /**
     * Looks up a symbol by its simple name.
     *
     * @param simpleName the unqualified shape name (e.g. {@code "Order"})
     * @return the symbol, or empty if not found
     */
    public Optional<Symbol> lookup(String simpleName) {
        return Optional.ofNullable(byName.get(simpleName));
    }

    /**
     * Returns {@code true} if a symbol with the given simple name is defined.
     *
     * @param simpleName the unqualified shape name
     */
    public boolean contains(String simpleName) {
        return byName.containsKey(simpleName);
    }

    /**
     * Returns an unmodifiable view of all symbols in definition order.
     */
    public Collection<Symbol> all() {
        return Collections.unmodifiableCollection(byName.values());
    }

    /** Returns the number of symbols currently registered. */
    public int size() {
        return byName.size();
    }
}
