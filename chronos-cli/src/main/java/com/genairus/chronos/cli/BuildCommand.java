package com.genairus.chronos.cli;

import com.genairus.chronos.compiler.ChronosCompiler;
import com.genairus.chronos.compiler.SourceUnit;
import com.genairus.chronos.core.diagnostics.Diagnostic;
import com.genairus.chronos.core.diagnostics.DiagnosticSeverity;
import com.genairus.chronos.generators.GeneratorRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Builds artifacts from a {@code chronos-build.json} configuration.
 *
 * <p>All source files matched by the configured glob patterns are collected and
 * compiled together in a single {@link ChronosCompiler#compileAll} call, enabling
 * cross-file type and symbol resolution. If any file fails to parse or any
 * validation error is produced, the build fails atomically — no output is written.
 *
 * <p>On success each configured generator is run once per compiled model; all
 * output files are written to the target-specific output directories resolved
 * relative to the config file.
 */
@Command(
    name = "build",
    description = "Build artifacts from a chronos-build.json configuration",
    mixinStandardHelpOptions = true
)
public class BuildCommand implements Callable<Integer> {

    @ParentCommand
    private ChronosCli parent;

    @Spec
    private CommandSpec spec;

    @Option(
        names = {"--config", "-c"},
        description = "Path to chronos-build.json (default: ./chronos-build.json)",
        paramLabel = "FILE"
    )
    private File configFile;

    @Override
    public Integer call() {
        var console = parent.console(spec.commandLine().getOut(), spec.commandLine().getErr());

        // Locate the config file
        Path configPath = resolveConfig(console);
        if (configPath == null) return 1;

        // Use toAbsolutePath().normalize() so getParent() is never null.
        // A bare filename like "chronos-build.json" passed via --config would
        // produce Path.getParent() == null without this normalization.
        Path baseDir = configPath.toAbsolutePath().normalize().getParent();

        // Load and expand config
        BuildConfig config;
        try {
            config = BuildConfigLoader.load(configPath);
        } catch (BuildConfigException e) {
            console.exception(e);
            return 1;
        }

        // Resolve source globs
        List<Path> sourceFiles;
        try {
            sourceFiles = BuildConfigLoader.resolveSourceFiles(baseDir, config.sources());
        } catch (BuildConfigException e) {
            console.exception(e);
            return 1;
        }

        if (sourceFiles.isEmpty()) {
            console.warning("No source files matched the configured patterns.");
            return 0;
        }

        // Validate all generator names before touching the filesystem
        for (BuildTarget target : config.targets()) {
            try {
                GeneratorRegistry.get(target.generator());
            } catch (IllegalArgumentException e) {
                var known = GeneratorRegistry.knownTargets().stream().sorted()
                        .reduce((a, b) -> a + ", " + b).orElse("(none)");
                console.error("Unknown generator '" + target.generator()
                        + "' in target '" + target.name() + "'. Known targets: " + known);
                return 1;
            }
        }

        // ── Read all source files ──────────────────────────────────────────────
        List<SourceUnit> sources;
        try {
            sources = sourceFiles.stream()
                    .map(p -> {
                        try {
                            return new SourceUnit(p.toString(), Files.readString(p));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .toList();
        } catch (UncheckedIOException e) {
            console.error("Error reading source file: " + e.getCause().getMessage());
            return 1;
        }

        // ── Compile all sources together as one unit ───────────────────────────
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
            console.error("Build failed — no output written.");
            return 1;
        }

        // ── Generate for each compiled model and each target ───────────────────
        int filesWritten = 0;
        for (var model : result.unitOrNull().models()) {
            for (BuildTarget target : config.targets()) {
                var projected = ModelProjection.apply(model, target);
                var generator = GeneratorRegistry.get(target.generator());
                var output    = generator.generate(projected);
                Path outDir   = baseDir.resolve(target.output());

                for (var entry : output.files().entrySet()) {
                    Path dest = outDir.resolve(entry.getKey());
                    try {
                        Files.createDirectories(dest.getParent());
                        Files.writeString(dest, entry.getValue());
                        console.plain("[" + target.name() + "] Wrote: " + dest.toAbsolutePath());
                        filesWritten++;
                    } catch (IOException e) {
                        console.error("Error writing " + dest + ": " + e.getMessage());
                        return 1;
                    }
                }
            }
        }

        console.success("Build complete. " + filesWritten + " file(s) written.");
        return 0;
    }

    private Path resolveConfig(AnsiConsole console) {
        if (configFile != null) {
            if (!configFile.exists()) {
                console.error("Config file not found: " + configFile.getPath());
                return null;
            }
            return configFile.toPath();
        }
        // Default: search the current working directory
        Path candidate = Path.of("").toAbsolutePath().resolve("chronos-build.json");
        if (!Files.exists(candidate)) {
            console.error("No chronos-build.json found in " + Path.of("").toAbsolutePath()
                    + ". Use --config to specify a config file.");
            return null;
        }
        return candidate;
    }
}
