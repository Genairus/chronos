package com.genairus.chronos.cli;

import com.genairus.chronos.compiler.ChronosCompiler;
import com.genairus.chronos.core.diagnostics.DiagnosticSeverity;
import com.genairus.chronos.generators.ChronosGenerator;
import com.genairus.chronos.generators.GeneratorRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.Callable;

@Command(
    name = "generate",
    description = "Generate artifacts from a .chronos file",
    mixinStandardHelpOptions = true
)
public class GenerateCommand implements Callable<Integer> {

    @ParentCommand
    private ChronosCli parent;

    @Spec
    private CommandSpec spec;

    @Parameters(
        index = "0",
        description = "The .chronos file to compile",
        paramLabel = "FILE"
    )
    private File inputFile;

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
    private File outputDir = new File("./generated");

    @Override
    public Integer call() {
        var console = parent.console(spec.commandLine().getOut(), spec.commandLine().getErr());

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

        if (!inputFile.exists()) {
            console.error("Error: File not found: " + inputFile.getPath());
            return 1;
        }

        String text;
        try {
            text = Files.readString(inputFile.toPath());
        } catch (IOException e) {
            console.error("Error reading file: " + e.getMessage());
            return 1;
        }

        // Compile (parse + resolve + validate in one pass)
        var result = new ChronosCompiler().compile(text, inputFile.getPath());

        // Always print all diagnostics
        for (var d : result.diagnostics()) {
            if (d.severity() == DiagnosticSeverity.ERROR) {
                console.error(d.toString());
            } else {
                console.warning(d.toString());
            }
        }

        // Only generate if the IR is fully valid
        if (!result.parsed() || !result.finalized()) {
            return 1;
        }

        // Generate
        var output = generator.generate(result.modelOrNull());

        // Write files to outputDir
        if (!outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                console.error("Error: Could not create output directory: " + outputDir.getPath());
                return 1;
            }
        }

        for (var entry : output.files().entrySet()) {
            var dest = outputDir.toPath().resolve(entry.getKey());
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
}
