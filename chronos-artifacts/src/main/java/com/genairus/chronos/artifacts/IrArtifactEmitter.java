package com.genairus.chronos.artifacts;

import com.genairus.chronos.compiler.IrCompilationUnit;
import com.genairus.chronos.compiler.SourceUnit;
import com.genairus.chronos.ir.json.IrModelSerializer;
import com.genairus.chronos.ir.model.IrModel;
import com.genairus.chronos.ir.types.ActorDef;
import com.genairus.chronos.ir.types.DenyDef;
import com.genairus.chronos.ir.types.EntityDef;
import com.genairus.chronos.ir.types.EnumDef;
import com.genairus.chronos.ir.types.ErrorDef;
import com.genairus.chronos.ir.types.InvariantDef;
import com.genairus.chronos.ir.types.IrShape;
import com.genairus.chronos.ir.types.JourneyDef;
import com.genairus.chronos.ir.types.ListDef;
import com.genairus.chronos.ir.types.MapDef;
import com.genairus.chronos.ir.types.PolicyDef;
import com.genairus.chronos.ir.types.RelationshipDef;
import com.genairus.chronos.ir.types.ShapeStructDef;
import com.genairus.chronos.ir.types.StateMachineDef;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Writes compiled IR models to disk as JSON artifact files.
 *
 * <p>For each {@link IrCompilationUnit.CompiledSource}, one
 * {@code <namespace>__<basename>.ir.json} file is produced using the canonical
 * {@link IrModelSerializer} serialiser. A {@code manifest.json} index is also written
 * listing every entry with its namespace, shape counts, and original source path.
 *
 * <h2>Output layout</h2>
 * <pre>
 * &lt;outputDir&gt;/
 *   manifest.json
 *   dogfood.payments__payments.ir.json
 *   dogfood.checkout__checkout.ir.json
 *   ...
 * </pre>
 *
 * <h2>Determinism</h2>
 * IR file contents are fully deterministic for identical inputs (the underlying
 * {@link IrModelSerializer} sorts properties alphabetically). The {@code generatedAt}
 * field in {@code manifest.json} reflects the wall-clock time of each run and
 * therefore varies between invocations.
 */
public final class IrArtifactEmitter {

    private IrArtifactEmitter() {}

    /**
     * Controls where and how artifacts are written.
     *
     * @param projectRoot  root of the project; used to relativise absolute source paths
     *                     in the manifest. May be {@code null} to skip relativisation.
     * @param outputDir    directory to write all artifact files into
     * @param prettyPrint  currently unused; reserved for a compact output mode
     */
    public record EmitOptions(Path projectRoot, Path outputDir, boolean prettyPrint) {}

    /**
     * Paths of the files that were written.
     *
     * @param manifestPath absolute path of the generated {@code manifest.json}
     * @param irFiles      absolute paths of the per-source IR JSON files, in
     *                     source-path order
     */
    public record EmittedIr(Path manifestPath, List<Path> irFiles) {}

    /**
     * Returns the conventional output directory for IR artifacts: {@code build/chronos/ir}
     * under the given project root.
     */
    public static Path defaultOutputDir(Path projectRoot) {
        return projectRoot.resolve("build").resolve("chronos").resolve("ir");
    }

    /**
     * Emits all IR JSON files and {@code manifest.json} for the given compilation unit.
     *
     * @param unit    the compilation unit returned by
     *                {@link com.genairus.chronos.compiler.ChronosCompiler#compileAll}
     * @param sources the original source units; used to verify provenance but paths
     *                are taken directly from {@code unit.compiledSources()}
     * @param options where and how to write the artifacts
     * @return the paths of every file written
     * @throws IOException if any file cannot be created or written
     */
    public static EmittedIr emit(
            IrCompilationUnit unit,
            List<SourceUnit> sources,
            EmitOptions options) throws IOException {

        Files.createDirectories(options.outputDir());

        List<Path>   irFiles         = new ArrayList<>();
        List<String> manifestEntries = new ArrayList<>();

        for (IrCompilationUnit.CompiledSource cs : unit.compiledSources()) {
            String  sourcePath  = cs.path();
            IrModel model       = cs.model();

            // ── Write per-file IR JSON ─────────────────────────────────────────
            String irFileName = buildIrFileName(model.namespace(), sourcePath);
            Path   irFile     = options.outputDir().resolve(irFileName);
            Files.writeString(irFile, IrModelSerializer.toJson(model));
            irFiles.add(irFile);

            // ── Accumulate manifest entry ──────────────────────────────────────
            String relPath = toRelativePath(sourcePath, options.projectRoot());
            manifestEntries.add(
                    buildManifestEntry(relPath, irFileName, model.namespace(), countShapes(model)));
        }

        // ── Write manifest.json ────────────────────────────────────────────────
        Path manifestPath = options.outputDir().resolve("manifest.json");
        Files.writeString(manifestPath, buildManifest(manifestEntries));

        return new EmittedIr(manifestPath, List.copyOf(irFiles));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Builds the output filename for a single IR JSON file.
     *
     * <p>Pattern: {@code <namespace>__<basename>.ir.json}, where {@code <basename>}
     * is the source file name with the {@code .chronos} extension stripped.
     */
    static String buildIrFileName(String namespace, String sourcePath) {
        String baseName = Path.of(sourcePath).getFileName().toString();
        if (baseName.endsWith(".chronos")) {
            baseName = baseName.substring(0, baseName.length() - ".chronos".length());
        }
        return namespace + "__" + baseName + ".ir.json";
    }

    /** Relativises {@code sourcePath} against {@code projectRoot} if possible. */
    private static String toRelativePath(String sourcePath, Path projectRoot) {
        if (projectRoot == null) return sourcePath;
        try {
            Path p = Path.of(sourcePath);
            if (p.isAbsolute()) {
                return projectRoot.relativize(p).toString();
            }
        } catch (IllegalArgumentException ignored) {
            // different filesystem roots — return as-is
        }
        return sourcePath;
    }

    /**
     * Counts each IrShape kind in the model, sorted alphabetically for deterministic output.
     *
     * @return sorted map from kind name to count; entries with count zero are omitted
     */
    static TreeMap<String, Integer> countShapes(IrModel model) {
        TreeMap<String, Integer> counts = new TreeMap<>();
        for (IrShape shape : model.shapes()) {
            counts.merge(kindName(shape), 1, Integer::sum);
        }
        return counts;
    }

    private static String kindName(IrShape shape) {
        return switch (shape) {
            case EntityDef       ignored -> "entity";
            case ActorDef        ignored -> "actor";
            case JourneyDef      ignored -> "journey";
            case ShapeStructDef  ignored -> "shape";
            case EnumDef         ignored -> "enum";
            case ListDef         ignored -> "list";
            case MapDef          ignored -> "map";
            case PolicyDef       ignored -> "policy";
            case RelationshipDef ignored -> "relationship";
            case InvariantDef    ignored -> "invariant";
            case DenyDef         ignored -> "deny";
            case ErrorDef        ignored -> "error";
            case StateMachineDef ignored -> "statemachine";
            default -> "unknown";
        };
    }

    // ── JSON builders (simple hand-rolled; no extra deps) ────────────────────

    private static String buildManifestEntry(
            String sourcePath,
            String irFileName,
            String namespace,
            TreeMap<String, Integer> shapeCounts) {

        // Properties in alphabetical order: irFile, namespace, shapeCounts, sourcePath
        StringBuilder sb = new StringBuilder();
        sb.append("    {\n");
        sb.append("      \"irFile\": ").append(jsonString(irFileName)).append(",\n");
        sb.append("      \"namespace\": ").append(jsonString(namespace)).append(",\n");
        sb.append("      \"shapeCounts\": {");
        if (!shapeCounts.isEmpty()) {
            sb.append("\n");
            boolean first = true;
            for (var entry : shapeCounts.entrySet()) {
                if (!first) sb.append(",\n");
                sb.append("        ").append(jsonString(entry.getKey()))
                  .append(": ").append(entry.getValue());
                first = false;
            }
            sb.append("\n      }");
        } else {
            sb.append("}");
        }
        sb.append(",\n");
        sb.append("      \"sourcePath\": ").append(jsonString(sourcePath)).append("\n");
        sb.append("    }");
        return sb.toString();
    }

    private static String buildManifest(List<String> entries) {
        // Top-level properties in alphabetical order: entries, format, generatedAt, version
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"entries\": [\n");
        for (int i = 0; i < entries.size(); i++) {
            sb.append(entries.get(i));
            if (i < entries.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");
        sb.append("  \"format\": \"chronos-ir-manifest\",\n");
        sb.append("  \"generatedAt\": ").append(jsonString(Instant.now().toString())).append(",\n");
        sb.append("  \"version\": \"1\"\n");
        sb.append("}");
        return sb.toString();
    }

    /** Minimal JSON string escaping (backslash and double-quote only). */
    private static String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
