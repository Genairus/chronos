package com.genairus.chronos.compiler;

import com.genairus.chronos.compiler.phases.*;
import com.genairus.chronos.compiler.symbols.GlobalSymbolTable;
import com.genairus.chronos.compiler.symbols.SymbolTable;
import com.genairus.chronos.compiler.util.IrRefWalker;
import com.genairus.chronos.core.diagnostics.Diagnostic;
import com.genairus.chronos.core.diagnostics.DiagnosticCollector;
import com.genairus.chronos.core.diagnostics.DiagnosticSeverity;
import com.genairus.chronos.core.refs.NamespaceId;
import com.genairus.chronos.core.refs.QualifiedName;
import com.genairus.chronos.core.refs.SymbolRef;
import com.genairus.chronos.ir.model.IrModel;
import com.genairus.chronos.syntax.SyntaxModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The main entry point for the Chronos semantic compiler.
 *
 * <p>Executes the seven-phase pipeline:
 * <ol>
 *   <li><b>ParseAndLower</b>    — ANTLR lex/parse + lower to {@link SyntaxModel}</li>
 *   <li><b>CollectSymbols</b>   — build symbol table, detect duplicates</li>
 *   <li><b>BuildIrSkeleton</b>  — convert {@link SyntaxModel} into {@link ChronosModel}</li>
 *   <li><b>TypeResolution</b>   — verify all named type references resolve</li>
 *   <li><b>CrossLinkResolution</b> — verify actor, relationship, and other cross-refs</li>
 *   <li><b>Validation</b>       — run all CHR-xxx semantic rules</li>
 *   <li><b>FinalizeIr</b>       — confirm no unresolved references; mark finalized</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>{@code
 * CompileResult result = new ChronosCompiler().compile(sourceText, "checkout.chronos");
 * if (result.success()) {
 *     ChronosModel model = result.modelOrNull();
 * } else {
 *     result.errors().forEach(System.err::println);
 * }
 * }</pre>
 */
public final class ChronosCompiler {

    public ChronosCompiler() {}

    /**
     * Compiles Chronos source text and returns a {@link CompileResult}.
     *
     * <p>The result always contains the full diagnostic list regardless of
     * whether compilation succeeded.  If the source could not be parsed,
     * {@link CompileResult#modelOrNull()} is {@code null} and
     * {@link CompileResult#parsed()} is {@code false}.
     *
     * @param sourceText the raw {@code .chronos} source text
     * @param sourceName a logical name for the source (e.g. a file path or {@code "<inline>"})
     * @return the compilation result; never {@code null}
     */
    public CompileResult compile(String sourceText, String sourceName) {
        var collector = new DiagnosticCollector();
        var symbols   = new SymbolTable();

        // Temporary context for Phase 1 (namespace not yet known)
        var parseCtx = new ResolverContext(
                new NamespaceId("<unknown>"), List.of(), symbols, collector, Map.of(),
                new GlobalSymbolTable());

        // ── Phase 1: Parse + Lower ─────────────────────────────────────────────
        SyntaxModel syntax = new ParseAndLowerPhase(sourceName).execute(sourceText, parseCtx);

        if (syntax == null) {
            return new CompileResult(null, collector.all(), false, false);
        }

        // ── Build resolver context with real namespace + use imports ───────────
        NamespaceId namespace = new NamespaceId(syntax.namespace());
        List<QualifiedName> uses = syntax.imports().stream()
                .map(u -> {
                    var qn = u.name();
                    return qn.isQualified()
                            ? QualifiedName.qualified(qn.namespaceOrNull(), qn.name())
                            : QualifiedName.local(qn.name());
                })
                .collect(Collectors.toList());

        var ctx = new ResolverContext(namespace, uses, symbols, collector, Map.of(),
                new GlobalSymbolTable());

        // ── Phase 2: Collect Symbols ───────────────────────────────────────────
        new CollectSymbolsPhase().execute(syntax, ctx);

        // ── Phase 3: Build IR Skeleton ─────────────────────────────────────────
        IrModel model = new BuildIrSkeletonPhase().execute(syntax, ctx);

        // ── Phase 4: Type Resolution ───────────────────────────────────────────
        model = new TypeResolutionPhase().execute(model, ctx);

        // ── Phase 5: Cross-Link Resolution ────────────────────────────────────
        model = new CrossLinkResolutionPhase().execute(model, ctx);

        // ── Phase 6: Validation ────────────────────────────────────────────────
        new ValidationPhase().execute(model, ctx);

        // ── Phase 7: Finalize ──────────────────────────────────────────────────
        FinalizeIrPhase.Result finalResult = new FinalizeIrPhase().execute(model, ctx);

        return new CompileResult(
                finalResult.model(),
                collector.all(),
                true,
                finalResult.finalized());
    }

    /**
     * Compiles a collection of Chronos source files and returns a {@link CompileAllResult}.
     *
     * <h2>Pipeline</h2>
     * <ol>
     *   <li><b>Index pass</b> ({@link IndexCompilationUnitPhase}) — parses and lowers every
     *       file, builds a {@link GlobalSymbolTable}, detects cross-file duplicate
     *       definitions ({@code CHR-014}), and binds each file's {@code use} imports
     *       ({@code CHR-016} unknown, {@code CHR-017} ambiguous).</li>
     *   <li>If any file fails to parse, returns immediately with {@code parsed=false}
     *       and {@code unitOrNull=null}.</li>
     *   <li><b>Global pipeline</b> — each successfully-parsed file is compiled through
     *       Phases 3–6 (BuildIrSkeleton → Validation) using a {@link ResolverContext}
     *       wired to the global symbol table and per-file import bindings, enabling
     *       cross-file type and cross-link resolution.</li>
     *   <li><b>Unit-level finalize</b> — {@link IrRefWalker} scans every model for
     *       remaining unresolved {@link SymbolRef}s; each emits a {@code CHR-012}
     *       diagnostic with the source path and expected kind. Diagnostics are emitted
     *       in stable order: by file path, then by kind, name, and span start line.</li>
     * </ol>
     *
     * <p>{@link CompileAllResult#finalized()} is {@code true} if and only if the
     * aggregate diagnostics contain no {@link DiagnosticSeverity#ERROR} entries —
     * a compilation-unit–level guarantee that no unresolved {@link SymbolRef} remains
     * in any model.
     *
     * <p>Files are processed in path-sorted order for deterministic output.
     *
     * @param sources the source units to compile, in declaration order
     * @return the aggregated result; never {@code null}
     */
    public CompileAllResult compileAll(List<SourceUnit> sources) {
        // ── Index pass: parse all files + build global symbol table ───────────
        IndexCompilationUnitPhase.Result indexResult =
                new IndexCompilationUnitPhase().execute(sources);

        if (!indexResult.parsed()) {
            return new CompileAllResult(
                    null,
                    indexResult.diagnostics(),
                    false,
                    false);
        }

        // All index diagnostics (CHR-005, CHR-014, CHR-016, CHR-017) go into the aggregate.
        List<Diagnostic> allDiagnostics = new ArrayList<>(indexResult.diagnostics());

        // Sort by path for deterministic compilation order.
        List<SourceUnitIndex> indices = indexResult.indices().stream()
                .sorted(Comparator.comparing(SourceUnitIndex::path))
                .toList();

        List<IrCompilationUnit.CompiledSource> compiledSources = new ArrayList<>();

        // ── Global pipeline: Phases 3–6 per file ─────────────────────────────
        // FinalizeIrPhase is NOT run per-file; a unit-level finalize replaces it.
        for (SourceUnitIndex idx : indices) {
            if (idx.syntaxModelOrNull() == null) continue; // parse failed

            NamespaceId ns = new NamespaceId(idx.namespace());
            List<QualifiedName> uses = idx.uses().stream()
                    .map(u -> {
                        var qn = u.name();
                        return qn.isQualified()
                                ? QualifiedName.qualified(qn.namespaceOrNull(), qn.name())
                                : QualifiedName.local(qn.name());
                    })
                    .collect(Collectors.toList());

            var fileCollector = new DiagnosticCollector();
            var ctx = new ResolverContext(
                    ns, uses,
                    idx.localSymbols(),
                    fileCollector,
                    idx.importBindings().bindings(),
                    indexResult.globalSymbols());

            // Phase 3: Build IR Skeleton
            IrModel model = new BuildIrSkeletonPhase().execute(idx.syntaxModelOrNull(), ctx);

            // Phase 4: Type Resolution
            model = new TypeResolutionPhase().execute(model, ctx);

            // Phase 5: Cross-Link Resolution
            model = new CrossLinkResolutionPhase().execute(model, ctx);

            // Phase 6: Validation
            new ValidationPhase().execute(model, ctx);

            allDiagnostics.addAll(fileCollector.all());
            compiledSources.add(new IrCompilationUnit.CompiledSource(idx.path(), model));
        }

        // ── Unit-level finalize: CHR-012 for all remaining unresolved refs ────
        // Files are already in path-sorted order; within each file sort by
        // kind → name → span.startLine for deterministic diagnostic output.
        for (var cs : compiledSources) {
            IrModel model      = cs.model();
            String  sourcePath = cs.path();

            List<SymbolRef> unresolved = IrRefWalker.findUnresolvedRefs(model).stream()
                    .sorted(Comparator
                            .comparing((SymbolRef r) -> r.kind().name())
                            .thenComparing(r -> r.name().name())
                            .thenComparing(r -> r.span().startLine()))
                    .toList();

            for (SymbolRef ref : unresolved) {
                String name = ref.name().name();
                String kind = ref.kind().name();
                allDiagnostics.add(new Diagnostic(
                        "CHR-012",
                        DiagnosticSeverity.ERROR,
                        "Unresolved reference '" + name + "' (expected kind: " + kind + ")",
                        ref.span(),
                        sourcePath));
            }
        }

        // finalized iff no ERROR diagnostics across the entire compilation unit.
        boolean allFinalized = allDiagnostics.stream()
                .noneMatch(d -> d.severity() == DiagnosticSeverity.ERROR);

        IrCompilationUnit unit = new IrCompilationUnit(List.copyOf(compiledSources));
        return new CompileAllResult(
                unit,
                List.copyOf(allDiagnostics),
                true,
                allFinalized);
    }
}
