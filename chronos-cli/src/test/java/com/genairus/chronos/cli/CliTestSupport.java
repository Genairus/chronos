package com.genairus.chronos.cli;

import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared helpers for CLI command tests. */
class CliTestSupport {

    // A minimal valid .chronos model: no errors, some warnings only (CHR-007, CHR-009)
    static final String VALID_MODEL = """
            namespace com.example

            actor User

            journey ExampleJourney {
                actor: User
                steps: [
                    step doSomething {
                        action: "Actor performs an action"
                        expectation: "System responds appropriately"
                    }
                ]
                outcomes: {
                    success: "Journey completed successfully"
                }
            }
            """;

    // A model that parses fine but triggers CHR-001 ERROR (no actor)
    static final String INVALID_MODEL = """
            namespace com.example

            journey BadJourney {
                outcomes: {
                    success: "done"
                }
            }
            """;

    record Result(int exit, String out, String err) {}

    static Result run(String... args) {
        var outSW = new StringWriter();
        var errSW = new StringWriter();
        var cmd = new CommandLine(new ChronosCli())
                .setOut(new PrintWriter(outSW, true))
                .setErr(new PrintWriter(errSW, true));
        int exit = cmd.execute(args);
        return new Result(exit, outSW.toString(), errSW.toString());
    }

    static Path writeChronos(Path dir, String content) throws Exception {
        Path file = dir.resolve("model.chronos");
        Files.writeString(file, content);
        return file;
    }

    static Path writeChronos(Path dir, String content, String filename) throws Exception {
        Path file = dir.resolve(filename);
        Files.writeString(file, content);
        return file;
    }
}
