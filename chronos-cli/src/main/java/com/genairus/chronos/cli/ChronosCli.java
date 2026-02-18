package com.genairus.chronos.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "chronos",
    mixinStandardHelpOptions = true,
    version = "0.1.0",
    description = "Chronos requirements language compiler - define journeys, generate artifacts",
    subcommands = {
        GenerateCommand.class,
        ValidateCommand.class
    }
)
public class ChronosCli implements Runnable {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ChronosCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        // When no subcommand is given, print help
        new CommandLine(this).usage(System.out);
    }
}