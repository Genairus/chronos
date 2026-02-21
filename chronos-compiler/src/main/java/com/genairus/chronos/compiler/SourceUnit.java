package com.genairus.chronos.compiler;

/**
 * A single Chronos source file represented as an in-memory (path, text) pair.
 *
 * <p>Used as input to {@link ChronosCompiler#compileAll} for multi-file builds.
 * The {@code path} is used only as a human-readable identifier in diagnostics and
 * does not need to correspond to an actual file on disk.
 *
 * @param path a logical identifier for the source (e.g. a file path or {@code "<inline>"})
 * @param text the raw {@code .chronos} source text
 */
public record SourceUnit(String path, String text) {}
