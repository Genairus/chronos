package com.genairus.chronos.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;

@Command(
    name = "chronos",
    mixinStandardHelpOptions = true,
    version = "0.1.0",
    description = "Chronos requirements language compiler - define journeys, generate artifacts",
    subcommands = {
        GenerateCommand.class,
        ValidateCommand.class,
        InitCommand.class,
        SelectCommand.class,
        DiffCommand.class,
        CleanCommand.class,
        BuildCommand.class
    }
)
public class ChronosCli implements Runnable {

    @Spec
    CommandSpec spec;

    @Option(names = "--debug",
            description = "Print exception stack traces on error")
    boolean debug;

    @Option(names = "--no-color",
            description = "Disable ANSI colour output")
    boolean noColor;

    @Option(names = "--force-color",
            description = "Force ANSI colour output even when not in a terminal")
    boolean forceColor;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ChronosCli()).execute(args);
        System.exit(exitCode);
    }

    /**
     * Creates an {@link AnsiConsole} backed by the given output streams and
     * this command's colour/debug flags.
     */
    public AnsiConsole console(PrintWriter out, PrintWriter err) {
        return new AnsiConsole(out, err, forceColor, noColor, debug);
    }

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
