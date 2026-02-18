package com.genairus.chronos.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;

@Command(
    name = "generate",
    description = "Generate artifacts from a .chronos file",
    mixinStandardHelpOptions = true
)
public class GenerateCommand implements Runnable {

    @Parameters(
        index = "0",
        description = "The .chronos file to compile",
        paramLabel = "FILE"
    )
    private File inputFile;

    @Option(
        names = {"-t", "--target"},
        description = "Target output format: gherkin, jira, otel, mermaid (default: gherkin)",
        paramLabel = "TARGET"
    )
    private String target = "gherkin";

    @Option(
        names = {"-o", "--output"},
        description = "Output directory (default: ./generated)",
        paramLabel = "DIR"
    )
    private File outputDir = new File("./generated");

    @Override
    public void run() {
        if (!inputFile.exists()) {
            System.err.println("Error: File not found: " + inputFile.getPath());
            System.exit(1);
        }

        if (!inputFile.getName().endsWith(".chronos")) {
            System.err.println("Warning: File does not have .chronos extension: " + inputFile.getName());
        }

        if (!outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                System.err.println("Error: Could not create output directory: " + outputDir.getPath());
                System.exit(1);
            }
        }

        System.out.println("Compiling : " + inputFile.getPath());
        System.out.println("Target    : " + target);
        System.out.println("Output    : " + outputDir.getPath());

        // TODO: wire in the real pipeline once parser and generators are built:
        // 1. ChronosParser.parse(inputFile)    → ChronosModel
        // 2. ChronosValidator.validate(model)  → ValidationResult
        // 3. if validation fails, print errors and exit(1)
        // 4. GeneratorRegistry.get(target).generate(model, outputDir)

        System.out.println("Done.");
    }
}