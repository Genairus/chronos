package com.genairus.chronos.cli;

import com.genairus.chronos.compiler.ChronosCompiler;
import com.genairus.chronos.core.diagnostics.DiagnosticSeverity;
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
    name = "validate",
    description = "Validate a .chronos file without generating output",
    mixinStandardHelpOptions = true
)
public class ValidateCommand implements Callable<Integer> {

    @ParentCommand
    private ChronosCli parent;

    @Spec
    private CommandSpec spec;

    @Parameters(index = "0", description = "The .chronos file to validate", paramLabel = "FILE")
    private File inputFile;

    @Option(names = {"-v", "--verbose"}, description = "Print a success message when the model is clean")
    private boolean verbose = false;

    @Override
    public Integer call() {
        var console = parent.console(spec.commandLine().getOut(), spec.commandLine().getErr());

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

        var result = new ChronosCompiler().compile(text, inputFile.getPath());

        for (var d : result.diagnostics()) {
            if (d.severity() == DiagnosticSeverity.ERROR) {
                console.error(d.toString());
            } else {
                console.warning(d.toString());
            }
        }

        if (!result.parsed() || !result.finalized()) {
            return 1;
        }

        if (verbose) {
            console.success("✓ No issues found");
        }
        return 0;
    }
}
