package com.genairus.chronos.cli;

import com.genairus.chronos.artifacts.IrArtifactEmitter;
import com.genairus.chronos.compiler.ChronosCompiler;
import com.genairus.chronos.compiler.SourceUnit;
import com.genairus.chronos.core.diagnostics.Diagnostic;
import com.genairus.chronos.core.diagnostics.DiagnosticSeverity;
import com.genairus.chronos.generators.MarkdownPrdGenerator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Generates a combined Markdown PRD from a single {@code .chronos} file or all
 * {@code .chronos} files found recursively under a directory.
 *
 * <p>When given a directory, files are collected in lexicographic path order (stable
 * ordering), hidden directories are skipped, and symbolic links are not followed.
 * All collected files are compiled together as a single compilation unit via
 * {@link ChronosCompiler#compileAll}.
 *
 * <p>On success one {@code <name>.md} file is written to the output directory.
 * On any parse or validation error, diagnostics are printed in
 * {@code file:line:col [SEVERITY CODE] message} format, sorted by file path then
 * source position, and the command exits with code {@code 1}.
 */
@Command(
    name = "prd",
    description = "Generate a combined Markdown PRD from a .chronos file or directory",
    mixinStandardHelpOptions = true
)
public class PrdCommand implements Callable<Integer> {

    @ParentCommand
    private ChronosCli parent;

    @Spec
    private CommandSpec spec;

    @Parameters(
        index = "0",
        description = "Source .chronos file or directory to scan recursively",
        paramLabel = "<input>"
    )
    private Path input;

    @Option(
        names = {"-o", "--out"},
        description = "Output root directory. PRD is written here; IR artifacts (if --emit-ir) go under <outDir>/ir/. Default: <input>/build/chronos",
        paramLabel = "<outDir>"
    )
    private Path outDir;

    @Option(
        names = {"-n", "--name"},
        description = "Base name for the output file without extension (default: chronos-prd)",
        paramLabel = "<name>"
    )
    private String docName = "chronos-prd";

    @Option(
        names = "--emit-ir",
        negatable = true,
        description = "Write per-file IR JSON artifacts to <out>/ir/ alongside the PRD (default: false)"
    )
    private boolean emitIr = false;

    @Override
    public Integer call() {
        var console = parent.console(spec.commandLine().getOut(), spec.commandLine().getErr());

        // ── Collect source files ───────────────────────────────────────────────
        List<SourceUnit> sources;
        try {
            if (Files.isRegularFile(input)) {
                sources = List.of(new SourceUnit(input.toString(), Files.readString(input)));
            } else if (Files.isDirectory(input)) {
                try (var walk = Files.walk(input)) {
                    sources = walk
                            .filter(p -> !hasHiddenComponent(p, input))
                            .filter(p -> p.toString().endsWith(".chronos"))
                            .filter(Files::isRegularFile)
                            .sorted()
                            .map(p -> {
                                try {
                                    return new SourceUnit(p.toString(), Files.readString(p));
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            })
                            .toList();
                }
                if (sources.isEmpty()) {
                    console.error("No .chronos files found under: " + input);
                    return 1;
                }
            } else {
                console.error("Input not found: " + input);
                return 1;
            }
        } catch (IOException e) {
            console.error("Error reading input: " + e.getMessage());
            return 1;
        } catch (UncheckedIOException e) {
            console.error("Error reading file: " + e.getCause().getMessage());
            return 1;
        }

        // ── Compile ────────────────────────────────────────────────────────────
        var result = new ChronosCompiler().compileAll(sources);

        // ── Print diagnostics sorted by: path → line → col → code ─────────────
        result.diagnostics().stream()
                .sorted(Comparator
                        .comparing((Diagnostic d) -> d.pathOrNull() != null ? d.pathOrNull() : "")
                        .thenComparingInt(d -> d.span().startLine())
                        .thenComparingInt(d -> d.span().startCol())
                        .thenComparing(Diagnostic::code))
                .forEach(d -> {
                    String tag = "[" + d.severity().name() + " " + d.code() + "]";
                    String msg = d.span() + " " + tag + " " + d.message();
                    if (d.severity() == DiagnosticSeverity.ERROR) {
                        console.error(msg);
                    } else {
                        console.warning(msg);
                    }
                });

        if (!result.parsed() || !result.finalized()) {
            console.error("Compilation failed — PRD not generated.");
            return 1;
        }

        // ── Compute output root ────────────────────────────────────────────────
        Path inputAbs = input.toAbsolutePath().normalize();
        Path inputDir = Files.isRegularFile(inputAbs) ? inputAbs.getParent() : inputAbs;
        Path outputRoot = outDir != null
                ? outDir.toAbsolutePath()
                : inputDir.resolve("build").resolve("chronos");

        try {
            Files.createDirectories(outputRoot);
        } catch (IOException e) {
            console.error("Cannot create output directory: " + e.getMessage());
            return 1;
        }

        // ── Generate combined PRD ──────────────────────────────────────────────
        var output = new MarkdownPrdGenerator()
                .generateCombined(result.unitOrNull().models(), docName);

        String filename = docName + ".md";
        Path dest = outputRoot.resolve(filename);
        try {
            Files.writeString(dest, output.content());
        } catch (IOException e) {
            console.error("Error writing " + dest + ": " + e.getMessage());
            return 1;
        }

        int n = sources.size();
        console.success("Generated " + filename + " from " + n + (n == 1 ? " file." : " files."));

        // ── Optionally emit IR JSON artifacts ──────────────────────────────────
        if (emitIr) {
            Path irDir = outputRoot.resolve("ir");
            var emitOptions = new IrArtifactEmitter.EmitOptions(inputDir, irDir, true);
            try {
                var emitted = IrArtifactEmitter.emit(result.unitOrNull(), sources, emitOptions);
                console.success("Emitted " + emitted.irFiles().size()
                        + (emitted.irFiles().size() == 1 ? " IR file" : " IR files")
                        + " to " + irDir + ".");
            } catch (IOException e) {
                console.error("Error writing IR artifacts: " + e.getMessage());
                return 1;
            }
        }

        return 0;
    }

    /**
     * Returns {@code true} if any path component of {@code path} relative to
     * {@code root} starts with {@code "."} (hidden file/directory convention).
     */
    private static boolean hasHiddenComponent(Path path, Path root) {
        Path rel = root.relativize(path);
        for (Path component : rel) {
            if (component.toString().startsWith(".")) return true;
        }
        return false;
    }
}
