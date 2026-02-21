package com.genairus.chronos.compiler;

import com.genairus.chronos.compiler.imports.ImportBindings;
import com.genairus.chronos.compiler.symbols.Symbol;
import com.genairus.chronos.compiler.symbols.SymbolTable;
import com.genairus.chronos.core.diagnostics.Diagnostic;
import com.genairus.chronos.syntax.SyntaxModel;
import com.genairus.chronos.syntax.SyntaxUseDecl;

import java.util.List;

/**
 * The per-file output of the {@code IndexCompilationUnitPhase}: the lowered
 * syntax model, the symbols collected from this file, the resolved import
 * bindings, and any diagnostics emitted while parsing, lowering, collecting
 * symbols, and binding imports.
 *
 * <p>Produced for every {@link SourceUnit} in the index pass — even for files
 * that failed to parse ({@link #syntaxModelOrNull()} is {@code null} in that case
 * and {@link #symbols()}, {@link #localSymbols()}, {@link #importBindings()} are
 * empty or empty-table).
 *
 * @param path               logical identifier of the source file (e.g. a file path)
 * @param syntaxModelOrNull  the lowered syntax tree, or {@code null} if parsing failed
 * @param namespace          the namespace declared in the file; {@code "<unknown>"} if
 *                           parsing failed
 * @param uses               the {@code use} import declarations from this file;
 *                           empty if parsing failed
 * @param symbols            the symbols collected from this file's declarations
 *                           as a list (for inspection); empty if parsing failed
 * @param localSymbols       the per-file {@link SymbolTable} built by
 *                           {@code CollectSymbolsPhase}; used by the global compilation
 *                           pipeline so phases can look up local names;
 *                           empty table if parsing failed
 * @param importBindings     the import bindings for this file (built after all symbols
 *                           are known); empty if parsing failed
 * @param diagnostics        parse, lower, symbol-collection, and import-binding
 *                           diagnostics for this file
 */
public record SourceUnitIndex(
        String path,
        SyntaxModel syntaxModelOrNull,
        String namespace,
        List<SyntaxUseDecl> uses,
        List<Symbol> symbols,
        SymbolTable localSymbols,
        ImportBindings importBindings,
        List<Diagnostic> diagnostics) {}
