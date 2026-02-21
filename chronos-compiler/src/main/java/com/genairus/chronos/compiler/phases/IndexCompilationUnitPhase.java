package com.genairus.chronos.compiler.phases;

import com.genairus.chronos.compiler.ResolverContext;
import com.genairus.chronos.compiler.SourceUnit;
import com.genairus.chronos.compiler.SourceUnitIndex;
import com.genairus.chronos.compiler.imports.ImportBindings;
import com.genairus.chronos.compiler.imports.ImportResolver;
import com.genairus.chronos.compiler.symbols.GlobalSymbolTable;
import com.genairus.chronos.compiler.symbols.Symbol;
import com.genairus.chronos.compiler.symbols.SymbolTable;
import com.genairus.chronos.core.diagnostics.Diagnostic;
import com.genairus.chronos.core.diagnostics.DiagnosticCollector;
import com.genairus.chronos.core.refs.NamespaceId;
import com.genairus.chronos.core.refs.QualifiedName;
import com.genairus.chronos.syntax.SyntaxModel;
import com.genairus.chronos.syntax.SyntaxUseDecl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Index pass: parses and lowers ALL source files first, then builds a
 * {@link GlobalSymbolTable} covering every top-level declaration across
 * the entire multi-file compilation unit, and finally binds each file's
 * {@code use} imports against the completed global table.
 *
 * <h2>Two-pass structure</h2>
 * <b>Pass A — symbol collection (per file):</b>
 * <ol>
 *   <li>Run {@link ParseAndLowerPhase} → {@link SyntaxModel}.</li>
 *   <li>Run {@link CollectSymbolsPhase} → per-file {@link SymbolTable}.</li>
 *   <li>Register each symbol in the shared {@link GlobalSymbolTable};
 *       cross-file fqName duplicates emit {@code CHR-014}.</li>
 * </ol>
 *
 * <b>Pass B — import binding (after all symbols are known):</b>
 * <ol>
 *   <li>For each successfully-parsed file, run {@link ImportResolver} against
 *       the now-complete global table.</li>
 *   <li>Unknown import targets emit {@code CHR-016}.</li>
 *   <li>Ambiguous simple-name bindings (same name → different targets) emit
 *       {@code CHR-017}.</li>
 * </ol>
 *
 * <h2>Relationship to the single-file pipeline</h2>
 * This phase is <em>preparatory</em>. Its output (the index and global table)
 * is available for later cross-file resolution phases. The main purpose of
 * this phase is duplicate-definition detection, structural indexing, and
 * binding imports to real symbol targets.
 */
public final class IndexCompilationUnitPhase {

    /**
     * The output of the index pass.
     *
     * @param indices       one {@link SourceUnitIndex} per input {@link SourceUnit},
     *                      in input order
     * @param globalSymbols the combined symbol registry for all files
     * @param diagnostics   all diagnostics emitted during this pass (parse errors,
     *                      per-file CHR-005, cross-file CHR-014, CHR-016, CHR-017)
     * @param parsed        {@code true} only if every source unit parsed without
     *                      fatal syntax errors
     */
    public record Result(
            List<SourceUnitIndex> indices,
            GlobalSymbolTable globalSymbols,
            List<Diagnostic> diagnostics,
            boolean parsed) {}

    /** Transient per-file data between Pass A and Pass B. */
    private record FileData(
            SourceUnit source,
            SyntaxModel syntaxOrNull,
            String namespace,
            List<SyntaxUseDecl> rawUses,
            List<Symbol> symbols,
            SymbolTable localSymbols,
            List<Diagnostic> passADiagnostics) {}

    /**
     * Runs the two-pass index over the supplied source units.
     *
     * @param sources the files to index, in declaration order
     * @return the index result; never {@code null}
     */
    public Result execute(List<SourceUnit> sources) {
        GlobalSymbolTable globalSymbols = new GlobalSymbolTable();
        List<FileData>    fileDataList  = new ArrayList<>();
        List<Diagnostic>  allDiags     = new ArrayList<>();
        boolean           allParsed    = true;

        // ── Pass A: parse + collect symbols + populate global table ───────────
        for (SourceUnit source : sources) {
            DiagnosticCollector fileDiag = new DiagnosticCollector();
            SymbolTable         fileSyms = new SymbolTable();

            ResolverContext parseCtx = new ResolverContext(
                    new NamespaceId("<unknown>"), List.of(), fileSyms, fileDiag, Map.of(),
                    new GlobalSymbolTable());

            SyntaxModel syntax = new ParseAndLowerPhase(source.path())
                    .execute(source.text(), parseCtx);

            if (syntax == null) {
                allParsed = false;
                allDiags.addAll(fileDiag.all());
                fileDataList.add(new FileData(
                        source, null, "<unknown>", List.of(), List.of(),
                        new SymbolTable(), List.copyOf(fileDiag.all())));
                continue;
            }

            NamespaceId         ns       = new NamespaceId(syntax.namespace());
            List<SyntaxUseDecl> rawUses  = syntax.imports();
            List<QualifiedName> usesQN   = rawUses.stream()
                    .map(u -> {
                        var qn = u.name();
                        return qn.isQualified()
                                ? QualifiedName.qualified(qn.namespaceOrNull(), qn.name())
                                : QualifiedName.local(qn.name());
                    })
                    .collect(Collectors.toList());

            ResolverContext ctx = new ResolverContext(ns, usesQN, fileSyms, fileDiag, Map.of(),
                    new GlobalSymbolTable());
            new CollectSymbolsPhase().execute(syntax, ctx);

            // Register in global table — CHR-014 emitted on cross-file duplicates.
            for (Symbol sym : fileSyms.all()) {
                globalSymbols.define(sym, source.path(), fileDiag);
            }

            allDiags.addAll(fileDiag.all());
            fileDataList.add(new FileData(
                    source,
                    syntax,
                    syntax.namespace(),
                    List.copyOf(rawUses),
                    List.copyOf(fileSyms.all()),
                    fileSyms,
                    List.copyOf(fileDiag.all())));
        }

        // ── Pass B: bind imports for each successfully-parsed file ────────────
        ImportResolver     resolver = new ImportResolver();
        List<SourceUnitIndex> indices = new ArrayList<>();

        for (FileData fd : fileDataList) {
            if (fd.syntaxOrNull() == null) {
                // Parse failed — emit empty bindings, no import resolution possible.
                indices.add(new SourceUnitIndex(
                        fd.source().path(), null, "<unknown>",
                        List.of(), List.of(),
                        fd.localSymbols(),
                        ImportBindings.empty("<unknown>"),
                        fd.passADiagnostics()));
                continue;
            }

            ImportBindings bindings = resolver.bindImports(
                    fd.namespace(), fd.rawUses(), globalSymbols);

            // Aggregate import-binding diagnostics (CHR-016, CHR-017).
            allDiags.addAll(bindings.diagnostics());

            // Combine Pass A diagnostics + import-binding diagnostics for this file.
            List<Diagnostic> fileDiags = new ArrayList<>(fd.passADiagnostics());
            fileDiags.addAll(bindings.diagnostics());

            indices.add(new SourceUnitIndex(
                    fd.source().path(),
                    fd.syntaxOrNull(),
                    fd.namespace(),
                    fd.rawUses(),
                    fd.symbols(),
                    fd.localSymbols(),
                    bindings,
                    List.copyOf(fileDiags)));
        }

        return new Result(
                List.copyOf(indices),
                globalSymbols,
                List.copyOf(allDiags),
                allParsed);
    }
}
