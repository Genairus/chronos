package com.genairus.chronos.mcp.tools;

import com.genairus.chronos.artifacts.IrBundleEmitter;
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
 * MCP tool: {@code chronos.emit_ir_bundle}
 *
 * <p>Compiles the specified {@code .chronos} files and emits an {@code ir-bundle.json}
 * into the specified output directory. The bundle can be used offline by other tooling
 * (e.g. {@code chronos generate --from-ir-bundle}) without re-parsing source files.
 *
 * <p>{@code bundlePath} in the response is an absolute path to the written file.
 */
public class EmitIrBundleTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "required": ["inputPaths", "outDir"],
              "properties": {
                "inputPaths": {
                  "type": "array",
                  "items": {"type": "string"},
                  "description": "Absolute paths to .chronos files to compile into an IR bundle."
                },
                "workspaceRoot": {
                  "type": "string",
                  "description": "Workspace root for path security. Defaults to CHRONOS_WORKSPACE env var or CWD."
                },
                "outDir": {
                  "type": "string",
                  "description": "Absolute path to the output directory for the ir-bundle.json file."
                }
              }
            }
            """;

    private final Gson gson = new Gson();
    private final ChronosCompiler compiler = new ChronosCompiler();

    /**
     * Executes the emit_ir_bundle logic. Callable from tests without a running MCP server.
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
                return Envelope.error(McpMeta.TOOL_EMIT_IR_BUNDLE,
                        McpMeta.ErrorCode.INVALID_INPUT,
                        "inputPaths is required and must not be empty", false);
            }

            Object outDirRaw = arguments.get("outDir");
            if (outDirRaw == null) {
                return Envelope.error(McpMeta.TOOL_EMIT_IR_BUNDLE,
                        McpMeta.ErrorCode.INVALID_INPUT,
                        "outDir is required", false);
            }
            Path outDir;
            try {
                outDir = PathSecurity.validate(Path.of(outDirRaw.toString()), workspaceRoot);
            } catch (PathSecurity.PathOutsideWorkspaceException e) {
                return Envelope.error(McpMeta.TOOL_EMIT_IR_BUNDLE,
                        McpMeta.ErrorCode.PATH_OUTSIDE_WORKSPACE,
                        "outDir outside workspace: " + outDirRaw, false);
            }

            List<SourceUnit> units = new ArrayList<>();
            for (var elem : inputPathsRaw) {
                String pathStr = elem.toString();
                Path path;
                try {
                    path = PathSecurity.validate(Path.of(pathStr), workspaceRoot);
                } catch (PathSecurity.PathOutsideWorkspaceException e) {
                    return Envelope.error(McpMeta.TOOL_EMIT_IR_BUNDLE,
                            McpMeta.ErrorCode.PATH_OUTSIDE_WORKSPACE,
                            "Path outside workspace: " + pathStr, false);
                }
                String text;
                try {
                    text = Files.readString(path);
                } catch (IOException e) {
                    return Envelope.error(McpMeta.TOOL_EMIT_IR_BUNDLE,
                            McpMeta.ErrorCode.INVALID_INPUT,
                            "Cannot read file: " + pathStr, false);
                }
                units.add(new SourceUnit(path.toString(), text));
            }

            var compileResult = compiler.compileAll(units);
            var sortedDiags = sortDiagnostics(compileResult.diagnostics());

            if (!compileResult.errors().isEmpty() || compileResult.unitOrNull() == null) {
                int errorCount = compileResult.errors().size();
                String msg = errorCount > 0
                        ? "Source files have " + errorCount + " error(s) — fix all errors before emitting IR bundle"
                        : "Compilation did not produce an IR unit — cannot emit bundle";
                var details = new JsonObject();
                details.addProperty("diagnosticSort", McpMeta.DIAGNOSTIC_SORT);
                details.add("diagnostics", buildDiagnosticsArray(sortedDiags));
                return Envelope.error(McpMeta.TOOL_EMIT_IR_BUNDLE,
                        McpMeta.ErrorCode.COMPILE_ERROR, msg, false, details);
            }

            Files.createDirectories(outDir);
            Path bundlePath = IrBundleEmitter.emit(compileResult.unitOrNull(), outDir);

            int modelCount = compileResult.unitOrNull().compiledSources().size();

            var resultJson = new JsonObject();
            resultJson.addProperty("bundlePath",
                    bundlePath.toAbsolutePath().normalize().toString());
            resultJson.addProperty("format", IrBundleEmitter.FORMAT);
            resultJson.addProperty("version", IrBundleEmitter.VERSION);
            resultJson.addProperty("modelCount", modelCount);
            resultJson.addProperty("diagnosticSort", McpMeta.DIAGNOSTIC_SORT);
            resultJson.add("diagnostics", buildDiagnosticsArray(sortedDiags));

            return Envelope.success(McpMeta.TOOL_EMIT_IR_BUNDLE, resultJson);

        } catch (IOException e) {
            return Envelope.error(McpMeta.TOOL_EMIT_IR_BUNDLE,
                    McpMeta.ErrorCode.INTERNAL_ERROR,
                    "IO error writing bundle: " + e.getMessage(), true);
        } catch (Exception e) {
            return Envelope.error(McpMeta.TOOL_EMIT_IR_BUNDLE,
                    McpMeta.ErrorCode.INTERNAL_ERROR,
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), true);
        }
    }

    /** Returns the fully wired MCP tool specification. */
    public McpServerFeatures.SyncToolSpecification specification() {
        var mapper = McpJsonDefaults.getMapper();
        var tool = McpSchema.Tool.builder()
                .name(McpMeta.TOOL_EMIT_IR_BUNDLE)
                .description("Compile .chronos files and emit an ir-bundle.json file. "
                        + "The bundle can be used offline by other tooling without re-parsing source files. "
                        + "bundlePath in the response is an absolute path to the written file.")
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

    private List<Diagnostic> sortDiagnostics(List<Diagnostic> diagnostics) {
        return diagnostics.stream()
                .sorted(Comparator
                        .<Diagnostic, String>comparing(EmitIrBundleTool::sortPath)
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

    private Path resolveWorkspaceRoot(Map<String, Object> arguments) {
        return PathSecurity.resolveWorkspaceRoot(arguments);
    }
}
