package com.genairus.chronos.compiler;

import com.genairus.chronos.compiler.phases.*;
import com.genairus.chronos.compiler.symbols.SymbolTable;
import com.genairus.chronos.core.diagnostics.DiagnosticCollector;
import com.genairus.chronos.core.refs.NamespaceId;
import com.genairus.chronos.core.refs.QualifiedName;
import com.genairus.chronos.ir.model.IrModel;
import com.genairus.chronos.syntax.SyntaxModel;

import java.util.List;
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
                new NamespaceId("<unknown>"), List.of(), symbols, collector);

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

        var ctx = new ResolverContext(namespace, uses, symbols, collector);

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
}
