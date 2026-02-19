package com.genairus.chronos.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
    name = "init",
    description = "Write a starter .chronos file to PATH (default: ./model.chronos)",
    mixinStandardHelpOptions = true
)
public class InitCommand implements Callable<Integer> {

    @ParentCommand
    private ChronosCli parent;

    @Spec
    private CommandSpec spec;

    @Parameters(
        index = "0",
        description = "Destination file path (default: ./model.chronos)",
        paramLabel = "PATH",
        defaultValue = "./model.chronos"
    )
    private File outputFile;

    @Option(names = "--force", description = "Overwrite the file if it already exists")
    private boolean force;

    @Override
    public Integer call() {
        var console = parent.console(spec.commandLine().getOut(), spec.commandLine().getErr());

        if (outputFile.exists() && !force) {
            console.error("Error: '" + outputFile.getPath() + "' already exists. Use --force to overwrite.");
            return 1;
        }

        // Derive namespace from the current working directory name
        String dirName = Path.of("").toAbsolutePath().getFileName().toString();
        String namespace = toNamespace(dirName);

        String content = starterContent(namespace);

        try {
            Files.writeString(outputFile.toPath(), content);
        } catch (IOException e) {
            console.error("Error writing file: " + e.getMessage());
            return 1;
        }

        console.plain("Created: " + outputFile.getPath());
        return 0;
    }

    /** Converts a directory name into a simple lowercase namespace token. */
    private static String toNamespace(String dirName) {
        // Replace hyphens and spaces with dots, strip everything else non-alphanumeric
        return dirName.toLowerCase()
                .replaceAll("[\\s-]+", ".")
                .replaceAll("[^a-z0-9.]", "");
    }

    private static String starterContent(String namespace) {
        return "namespace " + namespace + "\n"
             + "\n"
             + "actor User\n"
             + "\n"
             + "journey ExampleJourney {\n"
             + "    actor: User\n"
             + "    steps: [\n"
             + "        step doSomething {\n"
             + "            action: \"Actor performs an action\"\n"
             + "            expectation: \"System responds appropriately\"\n"
             + "        }\n"
             + "    ]\n"
             + "    outcomes: {\n"
             + "        success: \"Journey completed successfully\"\n"
             + "    }\n"
             + "}\n";
    }
}
