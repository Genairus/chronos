package com.genairus.chronos.compiler;

import com.genairus.chronos.ir.model.IrModel;

import java.util.List;

/**
 * The compiled output of a multi-file build: an ordered list of {@link IrModel}s,
 * one per successfully parsed {@link SourceUnit}.
 *
 * <p>Present in {@link CompileAllResult#unitOrNull()} only when every source unit
 * parsed without fatal syntax errors. Models are in the same order as the input
 * {@link SourceUnit} list, with any files that failed to parse omitted.
 *
 * <p>At this stage (Phase 1) models are independent — cross-file symbol references
 * declared via {@code use} are trusted but not verified against other models in the
 * unit. A future linking phase will resolve cross-file references.
 *
 * @param models the compiled IR models in source-input order
 */
public record IrCompilationUnit(List<IrModel> models) {}
