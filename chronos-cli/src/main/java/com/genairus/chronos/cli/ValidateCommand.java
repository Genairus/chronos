package com.genairus.chronos.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import java.io.File;

@Command(
    name = "validate",
    description = "Validate a .chronos file without generating output",
    mixinStandardHelpOptions = true
)
public class ValidateCommand implements Runnable {

    @Parameters(index = "0", description = "The .chronos file to validate")
    private File inputFile;

    @Option(names = {"-v", "--verbose"}, description = "Show detailed validation output")
    private boolean verbose = false;

    @Override
    public void run() {
        // TODO: 
        // 1. Parse inputFile
        // 2. Run ChronosValidator
        // 3. Print any errors/warnings
        System.out.println("Validating: " + inputFile);
    }
}