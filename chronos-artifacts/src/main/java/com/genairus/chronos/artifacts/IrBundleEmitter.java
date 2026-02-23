package com.genairus.chronos.artifacts;

import com.genairus.chronos.compiler.IrCompilationUnit;
import com.genairus.chronos.ir.json.IrModelSerializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Writes all compiled IR models to a single {@code ir-bundle.json} file.
 *
 * <p>The bundle is a self-contained, deterministic snapshot of every compiled
 * {@link com.genairus.chronos.ir.model.IrModel} in an {@link IrCompilationUnit}.
 * It is designed for LLM and code-generation tooling that needs the full compiled
 * domain model without recompiling {@code .chronos} sources.
 *
 * <h2>Bundle format</h2>
 * <pre>{@code
 * {
 *   "entries": [
 *     {
 *       "model": { ... IrModelSerializer format ... },
 *       "namespace": "com.example",
 *       "sourcePath": "path/to/file.chronos"
 *     }
 *   ],
 *   "format": "chronos-ir-bundle",
 *   "version": "1"
 * }
 * }</pre>
 *
 * <ul>
 *   <li>{@code entries} are sorted by {@code sourcePath} (deterministic).</li>
 *   <li>No {@code generatedAt} timestamp — output is fully deterministic for identical inputs.</li>
 *   <li>Top-level and entry properties are in alphabetical order.</li>
 *   <li>The {@code model} object uses the canonical {@link IrModelSerializer} format.</li>
 * </ul>
 */
public final class IrBundleEmitter {

    /** The format identifier written to every bundle. */
    public static final String FORMAT = "chronos-ir-bundle";

    /** The version written to every bundle. */
    public static final String VERSION = "1";

    private IrBundleEmitter() {}

    /**
     * Emits {@code ir-bundle.json} into {@code outputDir}.
     *
     * @param unit      the compilation unit returned by
     *                  {@link com.genairus.chronos.compiler.ChronosCompiler#compileAll}
     * @param outputDir directory to write the bundle file into (created if absent)
     * @return the absolute path of the written bundle file
     * @throws IOException if the directory cannot be created or the file cannot be written
     */
    public static Path emit(IrCompilationUnit unit, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);

        List<IrCompilationUnit.CompiledSource> sorted = unit.compiledSources().stream()
                .sorted(Comparator.comparing(IrCompilationUnit.CompiledSource::path))
                .toList();

        String bundleJson = buildBundle(sorted);
        Path dest = outputDir.resolve("ir-bundle.json");
        Files.writeString(dest, bundleJson);
        return dest;
    }

    // ── JSON builders (hand-rolled; no extra deps) ─────────────────────────────

    private static String buildBundle(List<IrCompilationUnit.CompiledSource> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"entries\": [\n");

        for (int i = 0; i < entries.size(); i++) {
            IrCompilationUnit.CompiledSource cs = entries.get(i);
            String modelJson = IrModelSerializer.toJson(cs.model());

            sb.append("    {\n");
            // Properties in alphabetical order: model, namespace, sourcePath
            sb.append("      \"model\": ").append(embedModelJson(modelJson)).append(",\n");
            sb.append("      \"namespace\": ").append(jsonString(cs.model().namespace())).append(",\n");
            sb.append("      \"sourcePath\": ").append(jsonString(cs.path())).append("\n");
            sb.append("    }");
            if (i < entries.size() - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append("  ],\n");
        // Top-level properties in alphabetical order: entries, format, version
        sb.append("  \"format\": ").append(jsonString(FORMAT)).append(",\n");
        sb.append("  \"version\": ").append(jsonString(VERSION)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Embeds a model JSON string (produced by {@link IrModelSerializer}) inline
     * as a nested JSON object value.
     *
     * <p>The first line ({@code "{"}) is placed directly after the {@code "model": }
     * key. Every subsequent line is prefixed with 6 spaces to align at the entry
     * object's key-value depth (3 nesting levels × 2 spaces = 6 spaces), which
     * means model content appears at 8 spaces (6 + the model's own 2-space indent).
     */
    private static String embedModelJson(String modelJson) {
        String[] lines = modelJson.split("\n");
        StringBuilder sb = new StringBuilder();
        sb.append(lines[0]); // always "{"
        for (int i = 1; i < lines.length; i++) {
            sb.append("\n      ").append(lines[i]);
        }
        return sb.toString();
    }

    /** Minimal JSON string escaping (backslash and double-quote only). */
    private static String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
