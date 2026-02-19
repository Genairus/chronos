package com.genairus.chronos.cli;

import com.genairus.chronos.model.*;
import com.genairus.chronos.parser.ChronosModelParser;
import com.genairus.chronos.parser.ChronosParseException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

@Command(
    name = "select",
    description = "Print top-level shapes whose name contains PATTERN (case-insensitive)",
    mixinStandardHelpOptions = true
)
public class SelectCommand implements Callable<Integer> {

    @ParentCommand
    private ChronosCli parent;

    @Spec
    private CommandSpec spec;

    @Parameters(index = "0", description = "The .chronos file to inspect", paramLabel = "FILE")
    private File inputFile;

    @Option(
        names = {"-p", "--pattern"},
        description = "Case-insensitive substring to match against shape names",
        paramLabel = "PATTERN",
        required = true
    )
    private String pattern;

    @Override
    public Integer call() {
        var console = parent.console(spec.commandLine().getOut(), spec.commandLine().getErr());

        if (!inputFile.exists()) {
            console.error("Error: File not found: " + inputFile.getPath());
            return 1;
        }

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

        String lowerPattern = pattern.toLowerCase();
        for (ShapeDefinition shape : model.shapes()) {
            if (shape.name().toLowerCase().contains(lowerPattern)) {
                console.plain(typeName(shape) + "  " + shape.name() + "  " + shape.location());
            }
        }

        return 0;
    }

    private static String typeName(ShapeDefinition shape) {
        return switch (shape) {
            case EntityDef      e -> "entity";
            case ShapeStructDef s -> "shape";
            case ListDef        l -> "list";
            case MapDef         m -> "map";
            case EnumDef        en -> "enum";
            case ActorDef       a -> "actor";
            case PolicyDef      p -> "policy";
            case JourneyDef     j -> "journey";
        };
    }
}
