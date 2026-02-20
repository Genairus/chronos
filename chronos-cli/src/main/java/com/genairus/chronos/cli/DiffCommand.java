package com.genairus.chronos.cli;

import com.genairus.chronos.compiler.ChronosCompiler;
import com.genairus.chronos.ir.model.IrModel;
import com.genairus.chronos.ir.types.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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

        IrModel baseModel = compileOrNull(base, console);
        if (baseModel == null) return 1;

        IrModel headModel = compileOrNull(head, console);
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

    private IrModel compileOrNull(File file, AnsiConsole console) {
        if (!file.exists()) {
            console.error("Error: File not found: " + file.getPath());
            return null;
        }
        String text;
        try {
            text = Files.readString(file.toPath());
        } catch (IOException e) {
            console.error("Error reading file: " + e.getMessage());
            return null;
        }
        var result = new ChronosCompiler().compile(text, file.getPath());
        if (!result.parsed()) {
            for (var d : result.diagnostics()) {
                console.error(d.toString());
            }
            return null;
        }
        return result.modelOrNull();
    }

    private static Map<String, String> shapeMap(IrModel model) {
        var map = new LinkedHashMap<String, String>();
        for (IrShape shape : model.shapes()) {
            map.put(shape.name(), typeName(shape));
        }
        return map;
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
        };
    }
}
