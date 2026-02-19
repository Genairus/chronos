package com.genairus.chronos.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Scanner;
import java.util.concurrent.Callable;

@Command(
    name = "clean",
    description = "Delete the generated output directory",
    mixinStandardHelpOptions = true
)
public class CleanCommand implements Callable<Integer> {

    @ParentCommand
    private ChronosCli parent;

    @Spec
    private CommandSpec spec;

    @Option(
        names = {"-o", "--output"},
        description = "Directory to delete (default: ./generated)",
        paramLabel = "DIR"
    )
    private File outputDir = new File("./generated");

    @Option(names = "--force", description = "Skip confirmation prompt")
    private boolean force;

    @Override
    public Integer call() {
        var console = parent.console(spec.commandLine().getOut(), spec.commandLine().getErr());

        if (!outputDir.exists()) {
            console.plain("Nothing to clean: '" + outputDir.getPath() + "' does not exist.");
            return 0;
        }

        // Confirm unless --force or running non-interactively
        boolean nonInteractive = force || System.console() == null;
        if (!nonInteractive) {
            console.plain("Delete '" + outputDir.getAbsolutePath() + "'? [y/N] ");
            var scanner = new Scanner(System.in);
            String response = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
            if (!response.equalsIgnoreCase("y") && !response.equalsIgnoreCase("yes")) {
                console.plain("Aborted.");
                return 0;
            }
        }

        // List and delete each top-level entry
        File[] entries = outputDir.listFiles();
        if (entries != null) {
            for (File entry : entries) {
                console.plain("Deleted: " + entry.getPath());
                deleteRecursively(entry.toPath(), console);
            }
        }

        // Delete the directory itself
        try {
            Files.deleteIfExists(outputDir.toPath());
        } catch (IOException e) {
            console.error("Error deleting directory: " + e.getMessage());
            return 1;
        }

        return 0;
    }

    private static void deleteRecursively(Path path, AnsiConsole console) {
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            console.error("Error deleting " + path + ": " + e.getMessage());
        }
    }
}
