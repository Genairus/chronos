package com.genairus.chronos.compiler;

import com.genairus.chronos.ir.model.IrModel;

import java.util.List;

/**
 * The compiled output of a multi-file build: an ordered list of
 * {@link CompiledSource}s, each associating a source path with its
 * fully-compiled {@link IrModel}.
 *
 * <p>Present in {@link CompileAllResult#unitOrNull()} only when every source
 * unit parsed without fatal syntax errors.  Entries are in stable path-sorted
 * order, matching the order used by {@link ChronosCompiler#compileAll}.
 *
 * <p>The convenience method {@link #models()} provides a plain list of models
 * for callers that do not need the path association (e.g. PRD generation).
 *
 * @param compiledSources each source path paired with its compiled model,
 *                        in stable path-sorted order
 */
public record IrCompilationUnit(List<CompiledSource> compiledSources) {

    /**
     * Associates a logical source path with the {@link IrModel} produced for
     * that file during compilation.
     *
     * @param path  the path string from the originating {@link SourceUnit};
     *              may be absolute, relative, or {@code "<inline>"}
     * @param model the fully compiled, resolved, and validated IR model
     */
    public record CompiledSource(String path, IrModel model) {}

    /**
     * Returns all compiled {@link IrModel}s in source-path order.
     *
     * <p>This is a convenience view; prefer iterating {@link #compiledSources()}
     * directly when the source path is also needed (e.g. artifact emission).
     */
    public List<IrModel> models() {
        return compiledSources.stream().map(CompiledSource::model).toList();
    }
}
