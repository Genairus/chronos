package com.genairus.chronos.cli;

import com.genairus.chronos.model.*;
import com.genairus.chronos.parser.ChronosModelParser;
import com.genairus.chronos.parser.ChronosParseException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
    name = "diff",
    description = "Compare shape names and types between two .chronos files",
    mixinStandardHelpOptions = true
)
public class DiffCommand implements Callable<Integer> {

    @ParentCommand
    private ChronosCli parent;

    @Spec
    private CommandSpec spec;

    @Parameters(index = "0", description = "Base .chronos file", paramLabel = "BASE")
    private File base;

    @Parameters(index = "1", description = "Head .chronos file", paramLabel = "HEAD")
    private File head;

    @Override
    public Integer call() {
        var console = parent.console(spec.commandLine().getOut(), spec.commandLine().getErr());

        ChronosModel baseModel = parseOrExit(base, console);
        if (baseModel == null) return 1;

        ChronosModel headModel = parseOrExit(head, console);
        if (headModel == null) return 1;

        // Build name→type maps (LinkedHashMap preserves source order)
        Map<String, String> baseShapes = shapeMap(baseModel);
        Map<String, String> headShapes = shapeMap(headModel);

        boolean hasDiff = false;

        // Removals and type changes: shapes in BASE not matching HEAD
        for (var entry : baseShapes.entrySet()) {
            String name = entry.getKey();
            String baseType = entry.getValue();
            if (!headShapes.containsKey(name)) {
                console.removed("- " + baseType + " " + name);
                hasDiff = true;
            } else if (!headShapes.get(name).equals(baseType)) {
                // Type changed — show the new (HEAD) type
                console.changed("~ " + headShapes.get(name) + " " + name);
                hasDiff = true;
            }
        }

        // Additions: shapes in HEAD not in BASE
        for (var entry : headShapes.entrySet()) {
            String name = entry.getKey();
            if (!baseShapes.containsKey(name)) {
                console.added("+ " + entry.getValue() + " " + name);
                hasDiff = true;
            }
        }

        return hasDiff ? 1 : 0;
    }

    private ChronosModel parseOrExit(File file, AnsiConsole console) {
        if (!file.exists()) {
            console.error("Error: File not found: " + file.getPath());
            return null;
        }
        try {
            return ChronosModelParser.parseFile(file.toPath());
        } catch (ChronosParseException e) {
            console.exception(e);
            return null;
        } catch (IOException e) {
            console.error("Error reading file: " + e.getMessage());
            return null;
        }
    }

    private static Map<String, String> shapeMap(ChronosModel model) {
        var map = new LinkedHashMap<String, String>();
        for (ShapeDefinition shape : model.shapes()) {
            map.put(shape.name(), typeName(shape));
        }
        return map;
    }

    private static String typeName(ShapeDefinition shape) {
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
        };
    }
}
