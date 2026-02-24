package com.genairus.chronos.mcp.tools;

import com.genairus.chronos.compiler.ChronosCompiler;
import com.genairus.chronos.compiler.SourceUnit;
import com.genairus.chronos.core.diagnostics.Diagnostic;
import com.genairus.chronos.ir.types.*;
import com.genairus.chronos.mcp.response.Envelope;
import com.genairus.chronos.mcp.response.McpMeta;
import com.genairus.chronos.mcp.security.PathSecurity;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * MCP tool: {@code chronos.list_symbols}
 *
 * <p>Compiles the specified {@code .chronos} files and returns a compact list of all
 * declared symbols (shapes). Supports three-state partial semantics:
 * <ul>
 *   <li>{@code partial=true, symbols=[]} — parse failure; use diagnostics to fix errors</li>
 *   <li>{@code partial=true, symbols=[...]} — parsed but not fully resolved (CHR-013 etc.)</li>
 *   <li>{@code partial=false, symbols=[...]} — fully compiled and finalized</li>
 * </ul>
 *
 * <p>Each symbol entry includes {@code name}, {@code kind}, {@code namespace}, and
 * {@code sourcePath}. Entity/shape symbols include {@code fieldNames}; journeys include
 * {@code actorName} and {@code stepNames}; enums include {@code memberNames}.
 */
public class ListSymbolsTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "required": ["inputPaths"],
              "properties": {
                "inputPaths": {
                  "type": "array",
                  "items": {"type": "string"},
                  "description": "Absolute paths to .chronos files to list symbols from."
                },
                "workspaceRoot": {
                  "type": "string",
                  "description": "Workspace root for path security validation. Defaults to CHRONOS_WORKSPACE env var or CWD."
                },
                "filterKind": {
                  "type": "string",
                  "description": "Optional. Filter symbols by shape kind (e.g. entity, journey, enum, actor)."
                },
                "filterNamespace": {
                  "type": "string",
                  "description": "Optional. Filter symbols by namespace string (exact match)."
                }
              }
            }
            """;

    private final Gson gson = new Gson();
    private final ChronosCompiler compiler = new ChronosCompiler();

    /**
     * Executes the list_symbols logic. Callable from tests without a running MCP server.
     *
     * @param arguments raw tool arguments
     * @return complete envelope JSON (success or error)
     */
    public JsonObject execute(Map<String, Object> arguments) {
        try {
            Path workspaceRoot = resolveWorkspaceRoot(arguments);

            @SuppressWarnings("unchecked")
            var inputPathsRaw = (List<Object>) arguments.get("inputPaths");
            if (inputPathsRaw == null || inputPathsRaw.isEmpty()) {
                return Envelope.error(McpMeta.TOOL_LIST_SYMBOLS,
                        McpMeta.ErrorCode.INVALID_INPUT,
                        "inputPaths is required and must not be empty", false);
            }

            String filterKind = arguments.containsKey("filterKind") && arguments.get("filterKind") != null
                    ? arguments.get("filterKind").toString().toLowerCase().trim() : null;
            String filterNs = arguments.containsKey("filterNamespace") && arguments.get("filterNamespace") != null
                    ? arguments.get("filterNamespace").toString().trim() : null;

            List<SourceUnit> units = new ArrayList<>();
            for (var elem : inputPathsRaw) {
                String pathStr = elem.toString();
                Path path;
                try {
                    path = PathSecurity.validate(Path.of(pathStr), workspaceRoot);
                } catch (PathSecurity.PathOutsideWorkspaceException e) {
                    return Envelope.error(McpMeta.TOOL_LIST_SYMBOLS,
                            McpMeta.ErrorCode.PATH_OUTSIDE_WORKSPACE,
                            "Path outside workspace: " + pathStr, false);
                }
                String text;
                try {
                    text = Files.readString(path);
                } catch (IOException e) {
                    return Envelope.error(McpMeta.TOOL_LIST_SYMBOLS,
                            McpMeta.ErrorCode.INVALID_INPUT,
                            "Cannot read file: " + pathStr, false);
                }
                units.add(new SourceUnit(path.toString(), text));
            }

            var compileResult = compiler.compileAll(units);

            // Three-state partial semantics:
            //   parsed=false          → partial=true, symbols=[]
            //   parsed=true, finalized=false → partial=true, symbols from models
            //   parsed=true, finalized=true  → partial=false, full symbols
            boolean partial = !compileResult.finalized();

            var symbolsArr = new JsonArray();
            if (compileResult.unitOrNull() != null) {
                for (var source : compileResult.unitOrNull().compiledSources()) {
                    String sourcePath = PathSecurity.toAbsolute(Path.of(source.path())).toString();
                    String ns = source.model().namespace();

                    for (var shape : source.model().shapes()) {
                        String kind = kindOf(shape);
                        if (filterKind != null && !kind.equals(filterKind)) continue;
                        if (filterNs != null && !ns.equals(filterNs)) continue;

                        symbolsArr.add(buildSymbol(shape, kind, ns, sourcePath));
                    }
                }
            }

            var resultJson = new JsonObject();
            var sortedDiags = compileResult.diagnostics().stream()
                    .sorted(Comparator
                            .comparing((Diagnostic d) -> d.pathOrNull() != null ? d.pathOrNull()
                                    : (d.span().sourceName() != null ? d.span().sourceName() : ""))
                            .thenComparingInt(d -> d.span().startLine())
                            .thenComparingInt(d -> d.span().startCol())
                            .thenComparing(Diagnostic::code))
                    .toList();

            resultJson.addProperty("partial", partial);
            resultJson.addProperty("diagnosticSort", McpMeta.DIAGNOSTIC_SORT);
            resultJson.add("symbols", symbolsArr);
            resultJson.add("diagnostics", buildDiagnosticsArray(sortedDiags));

            return Envelope.success(McpMeta.TOOL_LIST_SYMBOLS, resultJson);

        } catch (Exception e) {
            return Envelope.error(McpMeta.TOOL_LIST_SYMBOLS,
                    McpMeta.ErrorCode.INTERNAL_ERROR,
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), true);
        }
    }

    private JsonObject buildSymbol(IrShape shape, String kind, String namespace, String sourcePath) {
        var obj = new JsonObject();
        obj.addProperty("name", shape.name());
        obj.addProperty("kind", kind);
        obj.addProperty("namespace", namespace);
        obj.addProperty("sourcePath", sourcePath);

        switch (shape) {
            case EntityDef e -> {
                var fieldNames = new JsonArray();
                e.fields().forEach(f -> fieldNames.add(f.name()));
                obj.add("fieldNames", fieldNames);
            }
            case ShapeStructDef s -> {
                var fieldNames = new JsonArray();
                s.fields().forEach(f -> fieldNames.add(f.name()));
                obj.add("fieldNames", fieldNames);
            }
            case EnumDef e -> {
                var members = new JsonArray();
                e.members().forEach(m -> members.add(m.name()));
                obj.add("memberNames", members);
            }
            case JourneyDef j -> {
                j.actorName().ifPresent(a -> obj.addProperty("actorName", a));
                var stepNames = new JsonArray();
                j.steps().forEach(s -> stepNames.add(s.name()));
                obj.add("stepNames", stepNames);
            }
            default -> { /* no extra fields for other shapes */ }
        }

        return obj;
    }

    private String kindOf(IrShape shape) {
        return switch (shape) {
            case EntityDef       ignored -> "entity";
            case ShapeStructDef  ignored -> "shape";
            case EnumDef         ignored -> "enum";
            case ListDef         ignored -> "list";
            case MapDef          ignored -> "map";
            case ActorDef        ignored -> "actor";
            case PolicyDef       ignored -> "policy";
            case JourneyDef      ignored -> "journey";
            case RelationshipDef ignored -> "relationship";
            case InvariantDef    ignored -> "invariant";
            case DenyDef         ignored -> "deny";
            case ErrorDef        ignored -> "error";
            case StateMachineDef ignored -> "statemachine";
            case RoleDef         ignored -> "role";
            case EventDef        ignored -> "event";
        };
    }

    private JsonArray buildDiagnosticsArray(List<Diagnostic> diagnostics) {
        var arr = new JsonArray();
        for (var d : diagnostics) {
            var obj = new JsonObject();
            obj.addProperty("code", d.code());
            obj.addProperty("severity", d.severity().name());
            obj.addProperty("message", d.message());
            String pathStr = d.pathOrNull() != null ? d.pathOrNull() : d.span().sourceName();
            if (pathStr != null && !pathStr.equals("<unknown>")) {
                obj.addProperty("sourcePath",
                        PathSecurity.toAbsolute(Path.of(pathStr)).toString());
            }
            arr.add(obj);
        }
        return arr;
    }

    private Path resolveWorkspaceRoot(Map<String, Object> arguments) {
        return PathSecurity.resolveWorkspaceRoot(arguments);
    }

    /** Returns the fully wired MCP tool specification. */
    public McpServerFeatures.SyncToolSpecification specification() {
        var mapper = McpJsonDefaults.getMapper();
        var tool = McpSchema.Tool.builder()
                .name(McpMeta.TOOL_LIST_SYMBOLS)
                .description("List all declared symbols from one or more .chronos files. "
                        + "When partial=true, some symbols may be missing due to unresolved references — "
                        + "check diagnostics for details. "
                        + "Use this to discover available shapes before writing 'use' imports. "
                        + "Each symbol includes name, kind, namespace, sourcePath, and type-specific details.")
                .inputSchema(mapper, INPUT_SCHEMA)
                .build();

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
            var envelope = execute(request.arguments() != null ? request.arguments() : Map.of());
            return McpSchema.CallToolResult.builder()
                    .addTextContent(gson.toJson(envelope))
                    .isError(envelope.has("error"))
                    .build();
        });
    }
}
