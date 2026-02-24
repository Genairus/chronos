package com.genairus.chronos.mcp.tools;

import com.genairus.chronos.compiler.ChronosCompiler;
import com.genairus.chronos.compiler.SourceUnit;
import com.genairus.chronos.core.diagnostics.Diagnostic;
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
 * MCP tool: {@code chronos.validate}
 *
 * <p>Reads the specified {@code .chronos} files from disk, runs the full 7-phase
 * compiler pipeline, and returns all diagnostics sorted
 * {@code path → startLine → startCol → code}.
 *
 * <p>The {@link #execute} method is package-accessible for direct testing without
 * spinning up an MCP server.
 */
public class ValidateTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "required": ["inputPaths"],
              "properties": {
                "inputPaths": {
                  "type": "array",
                  "items": {"type": "string"},
                  "description": "Absolute paths to .chronos files to validate"
                },
                "workspaceRoot": {
                  "type": "string",
                  "description": "Workspace root for path security validation. Defaults to CHRONOS_WORKSPACE env var or CWD."
                }
              }
            }
            """;

    private final Gson gson = new Gson();
    private final ChronosCompiler compiler = new ChronosCompiler();

    /**
     * Executes the validate logic.  Callable from tests without a running MCP server.
     *
     * @param arguments  raw tool arguments (matches the JSON Schema above)
     * @return           complete envelope JSON (success or error)
     */
    public JsonObject execute(Map<String, Object> arguments) {
        try {
            Path workspaceRoot = resolveWorkspaceRoot(arguments);

            @SuppressWarnings("unchecked")
            var inputPathsRaw = (List<Object>) arguments.get("inputPaths");
            if (inputPathsRaw == null || inputPathsRaw.isEmpty()) {
                return Envelope.error(McpMeta.TOOL_VALIDATE,
                        McpMeta.ErrorCode.INVALID_INPUT,
                        "inputPaths is required and must not be empty", false);
            }

            List<SourceUnit> units = new ArrayList<>();
            for (var elem : inputPathsRaw) {
                String pathStr = elem.toString();
                Path path;
                try {
                    path = PathSecurity.validate(Path.of(pathStr), workspaceRoot);
                } catch (PathSecurity.PathOutsideWorkspaceException e) {
                    return Envelope.error(McpMeta.TOOL_VALIDATE,
                            McpMeta.ErrorCode.PATH_OUTSIDE_WORKSPACE,
                            "Path outside workspace: " + pathStr, false);
                }
                String text;
                try {
                    text = Files.readString(path);
                } catch (IOException e) {
                    return Envelope.error(McpMeta.TOOL_VALIDATE,
                            McpMeta.ErrorCode.INVALID_INPUT,
                            "Cannot read file: " + pathStr, false);
                }
                units.add(new SourceUnit(path.toString(), text));
            }

            var result = compiler.compileAll(units);
            var diags = sortDiagnostics(result.diagnostics());

            var resultJson = new JsonObject();
            resultJson.addProperty("diagnosticSort", McpMeta.DIAGNOSTIC_SORT);
            resultJson.addProperty("errorCount", result.errors().size());
            resultJson.addProperty("warningCount", result.warnings().size());
            resultJson.addProperty("parsed", result.parsed());
            resultJson.addProperty("finalized", result.finalized());
            resultJson.add("diagnostics", buildDiagnosticsArray(diags));

            return Envelope.success(McpMeta.TOOL_VALIDATE, resultJson);

        } catch (Exception e) {
            return Envelope.error(McpMeta.TOOL_VALIDATE,
                    McpMeta.ErrorCode.INTERNAL_ERROR,
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), true);
        }
    }

    /** Returns the fully wired MCP tool specification. */
    public McpServerFeatures.SyncToolSpecification specification() {
        var mapper = McpJsonDefaults.getMapper();
        var tool = McpSchema.Tool.builder()
                .name(McpMeta.TOOL_VALIDATE)
                .description("Validate one or more .chronos files through the full compiler pipeline. "
                        + "Returns all diagnostics sorted path→line→col→code. "
                        + "Always check diagnosticSort metadata. "
                        + "Never call chronos.generate if errorCount > 0.")
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

    private Path resolveWorkspaceRoot(Map<String, Object> arguments) {
        return PathSecurity.resolveWorkspaceRoot(arguments);
    }

    private List<Diagnostic> sortDiagnostics(List<Diagnostic> diagnostics) {
        return diagnostics.stream()
                .sorted(Comparator
                        // pathOrNull is only set for CHR-012 (finalize phase); for all other
                        // diagnostics the file path is in span.sourceName.
                        .comparing(ValidateTool::sortPath)
                        .thenComparingInt(d -> d.span().startLine())
                        .thenComparingInt(d -> d.span().startCol())
                        .thenComparing(Diagnostic::code))
                .toList();
    }

    private static String sortPath(Diagnostic d) {
        if (d.pathOrNull() != null) return d.pathOrNull();
        var sn = d.span().sourceName();
        return sn != null ? sn : "";
    }

    private JsonArray buildDiagnosticsArray(List<Diagnostic> diagnostics) {
        var arr = new JsonArray();
        for (var d : diagnostics) {
            var obj = new JsonObject();
            obj.addProperty("code", d.code());
            obj.addProperty("severity", d.severity().name());
            obj.addProperty("message", d.message());
            // pathOrNull only set for CHR-012; for all others the path is span.sourceName
            String pathStr = d.pathOrNull() != null ? d.pathOrNull() : d.span().sourceName();
            if (pathStr != null && !pathStr.equals("<unknown>")) {
                obj.addProperty("sourcePath",
                        PathSecurity.toAbsolute(Path.of(pathStr)).toString());
            }
            var span = d.span();
            var spanObj = new JsonObject();
            spanObj.addProperty("sourceName", span.sourceName());
            spanObj.addProperty("startLine", span.startLine());
            spanObj.addProperty("startCol", span.startCol());
            spanObj.addProperty("endLine", span.endLine());
            spanObj.addProperty("endCol", span.endCol());
            obj.add("span", spanObj);
            arr.add(obj);
        }
        return arr;
    }
}
