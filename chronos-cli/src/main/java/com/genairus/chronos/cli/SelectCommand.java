package com.genairus.chronos.cli;

import com.genairus.chronos.compiler.ChronosCompiler;
import com.genairus.chronos.ir.model.IrModel;
import com.genairus.chronos.ir.types.*;
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

        String text;
        try {
            text = Files.readString(inputFile.toPath());
        } catch (IOException e) {
            console.error("Error reading file: " + e.getMessage());
            return 1;
        }

        var result = new ChronosCompiler().compile(text, inputFile.getPath());
        if (!result.parsed()) {
            for (var d : result.diagnostics()) {
                console.error(d.toString());
            }
            return 1;
        }

        IrModel model = result.modelOrNull();

        String lowerPattern = pattern.toLowerCase();
        for (IrShape shape : model.shapes()) {
            if (shape.name().toLowerCase().contains(lowerPattern)) {
                var span = shape.span();
                String location = span.sourceName() + ":" + span.startLine();
                console.plain(typeName(shape) + "  " + shape.name() + "  " + location);
            }
        }

        return 0;
    }

    private static String typeName(IrShape shape) {
        return switch (shape) {
            case EntityDef        e  -> "entity";
            case ShapeStructDef   s  -> "shape";
            case ListDef          l  -> "list";
            case MapDef           m  -> "map";
            case EnumDef          en -> "enum";
            case ActorDef         a  -> "actor";
            case PolicyDef        p  -> "policy";
            case JourneyDef       j  -> "journey";
            case RelationshipDef  r  -> "relationship";
            case InvariantDef     i  -> "invariant";
            case DenyDef          d  -> "deny";
            case ErrorDef         er -> "error";
            case StateMachineDef  sm -> "statemachine";
            case RoleDef          ro -> "role";
        };
    }
}
