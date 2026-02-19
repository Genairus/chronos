package com.genairus.chronos.cli;

import com.genairus.chronos.generators.GeneratorRegistry;
import com.genairus.chronos.model.ChronosModel;
import com.genairus.chronos.parser.ChronosModelParser;
import com.genairus.chronos.parser.ChronosParseException;
import com.genairus.chronos.validator.ChronosValidator;
import com.genairus.chronos.validator.ValidationSeverity;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

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

        Path baseDir = configPath.getParent();

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

        int filesWritten = 0;
        boolean anySourceFailed = false;

        for (Path source : sourceFiles) {
            // Parse
            ChronosModel model;
            try {
                model = ChronosModelParser.parseFile(source);
            } catch (ChronosParseException e) {
                console.error("Parse error in " + source + ":");
                console.exception(e);
                anySourceFailed = true;
                continue;
            } catch (IOException e) {
                console.error("Error reading " + source + ": " + e.getMessage());
                anySourceFailed = true;
                continue;
            }

            // Validate — skip source on any ERROR
            var result = new ChronosValidator().validate(model);
            if (result.hasErrors()) {
                for (var issue : result.issues()) {
                    if (issue.severity() == ValidationSeverity.ERROR) {
                        console.error(issue.toString());
                    } else {
                        console.warning(issue.toString());
                    }
                }
                console.error("Skipping " + source.getFileName() + " due to validation errors.");
                anySourceFailed = true;
                continue;
            }

            // Generate for each target
            for (BuildTarget target : config.targets()) {
                var projected  = ModelProjection.apply(model, target);
                var generator  = GeneratorRegistry.get(target.generator());
                var output     = generator.generate(projected);
                Path outDir    = baseDir.resolve(target.output());

                for (var entry : output.files().entrySet()) {
                    Path dest = outDir.resolve(entry.getKey());
                    try {
                        Files.createDirectories(dest.getParent());
                        Files.writeString(dest, entry.getValue());
                        console.plain("[" + target.name() + "] Wrote: " + dest.toAbsolutePath());
                        filesWritten++;
                    } catch (IOException e) {
                        console.error("Error writing " + dest + ": " + e.getMessage());
                        anySourceFailed = true;
                    }
                }
            }
        }

        if (anySourceFailed) {
            console.warning("Build finished with errors. " + filesWritten + " file(s) written.");
            return 1;
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
