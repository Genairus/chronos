package com.genairus.chronos.cli;

import com.genairus.chronos.generators.ChronosGenerator;
import com.genairus.chronos.generators.GeneratorRegistry;
import com.genairus.chronos.model.ChronosModel;
import com.genairus.chronos.parser.ChronosModelParser;
import com.genairus.chronos.parser.ChronosParseException;
import com.genairus.chronos.validator.ChronosValidator;
import com.genairus.chronos.validator.ValidationSeverity;
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

        // Parse
        ChronosModel model;
        try {
            model = ChronosModelParser.parseFile(inputFile.toPath());
        } catch (ChronosParseException e) {
            console.exception(e);
            return 1;
        } catch (IOException e) {
            console.error("Error reading file: " + e.getMessage());
            return 1;
        }

        // Validate — print all issues, exit 1 on any error
        var result = new ChronosValidator().validate(model);
        if (result.hasErrors()) {
            for (var issue : result.issues()) {
                if (issue.severity() == ValidationSeverity.ERROR) {
                    console.error(issue.toString());
                } else {
                    console.warning(issue.toString());
                }
            }
            return 1;
        }

        // Generate
        var output = generator.generate(model);

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
