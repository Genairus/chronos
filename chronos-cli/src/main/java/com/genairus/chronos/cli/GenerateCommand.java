package com.genairus.chronos.cli;

import com.genairus.chronos.artifacts.IrBundleEmitter;
import com.genairus.chronos.compiler.ChronosCompiler;
import com.genairus.chronos.compiler.SourceUnit;
import com.genairus.chronos.core.diagnostics.Diagnostic;
import com.genairus.chronos.core.diagnostics.DiagnosticSeverity;
import com.genairus.chronos.generators.ChronosGenerator;
import com.genairus.chronos.generators.GeneratorRegistry;
import com.genairus.chronos.ir.model.IrModel;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Generates artifacts from a single {@code .chronos} file or all
 * {@code .chronos} files found recursively under a directory, or from a
 * pre-compiled {@code ir-bundle.json} file.
 *
 * <p>When given a directory, files are collected in lexicographic path order
 * (stable ordering), hidden directories are skipped, and symbolic links are
 * not followed. All collected files are compiled together as a single
 * compilation unit via {@link ChronosCompiler#compileAll}.
 *
 * <p>On success the generator is run once per compiled model and all output
 * files are written to the output directory. On any parse or validation error,
 * diagnostics are printed in {@code file:line:col [SEVERITY CODE] message}
 * format, sorted by file path then source position, and the command exits
 * with code {@code 1}.
 */
@Command(
    name = "generate",
    description = "Generate artifacts from a .chronos file or directory",
    mixinStandardHelpOptions = true
)
public class GenerateCommand implements Callable<Integer> {

    @ParentCommand
    private ChronosCli parent;

    @Spec
    private CommandSpec spec;

    @Parameters(
        index = "0",
        arity = "0..1",
        description = "Source .chronos file or directory to scan recursively",
        paramLabel = "<input>"
    )
    private Path input;

    @Option(
        names = {"-t", "--target"},
        description = "Output format (known targets: markdown, prd; default: markdown)",
        paramLabel = "TARGET"
    )
    private String target = "markdown";

    @Option(
        names = {"-o", "--output"},
        description = "Output directory (default: ./generated)",
        paramLabel = "DIR"
    )
    private Path outputDir = Path.of("./generated");

    @Option(
        names = "--from-ir-bundle",
        description = "Load models from a pre-compiled ir-bundle.json instead of compiling sources",
        paramLabel = "FILE"
    )
    private Path bundleFile;

    @Option(
        names = "--emit-bundle",
        description = "Write an ir-bundle.json to the output directory after successful compilation"
    )
    private boolean emitBundle = false;

    @Override
    public Integer call() {
        var console = parent.console(spec.commandLine().getOut(), spec.commandLine().getErr());

        // Validate mutual exclusion of <input> and --from-ir-bundle
        if (bundleFile != null && input != null) {
            console.error("--from-ir-bundle and <input> are mutually exclusive");
            return 1;
        }
        if (bundleFile == null && input == null) {
            console.error("Specify either <input> (source file/directory) or --from-ir-bundle FILE");
            return 1;
        }

        // Validate target before doing any expensive work
        ChronosGenerator generator;
        try {
            generator = GeneratorRegistry.get(target);
        } catch (IllegalArgumentException e) {
            var known = GeneratorRegistry.knownTargets().stream().sorted()
                    .reduce((a, b) -> a + ", " + b).orElse("(none)");
            console.error("Unknown target '" + target + "'. Known targets: " + known);
            return 1;
        }

        // ── Obtain models: from bundle or by compiling sources ─────────────────
        List<IrModel> models;

        if (bundleFile != null) {
            // ── Bundle mode: load pre-compiled IR ─────────────────────────────
            if (!Files.isRegularFile(bundleFile)) {
                console.error("Bundle file not found: " + bundleFile);
                return 1;
            }
            try {
                models = IrBundleLoader.load(bundleFile);
            } catch (IOException e) {
                console.error("Cannot read bundle: " + e.getMessage());
                return 1;
            } catch (IllegalArgumentException e) {
                console.error("Invalid bundle: " + e.getMessage());
                return 1;
            }
        } else {
            // ── Source mode: collect and compile ──────────────────────────────
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

            var result = new ChronosCompiler().compileAll(sources);

            // Print diagnostics sorted by: path → line → col → code
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
                return 1;
            }

            models = result.unitOrNull().models();

            // Optionally emit bundle alongside output
            if (emitBundle) {
                try {
                    Path bundlePath = IrBundleEmitter.emit(result.unitOrNull(), outputDir);
                    console.plain("Bundle: " + bundlePath.toAbsolutePath());
                } catch (IOException e) {
                    console.error("Error writing bundle: " + e.getMessage());
                    return 1;
                }
            }
        }

        // ── Create output directory ────────────────────────────────────────────
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            console.error("Error: Could not create output directory: " + outputDir);
            return 1;
        }

        // ── Generate for each compiled model and collect all output files ──────
        Map<String, String> allFiles = new LinkedHashMap<>();
        for (var model : models) {
            var output = generator.generate(model);
            allFiles.putAll(output.files());
        }

        // ── Write output files ─────────────────────────────────────────────────
        for (var entry : allFiles.entrySet()) {
            var dest = outputDir.resolve(entry.getKey());
            try {
                Files.writeString(dest, entry.getValue());
                console.plain("Wrote: " + dest.toAbsolutePath());
            } catch (IOException e) {
                console.error("Error writing " + dest + ": " + e.getMessage());
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
