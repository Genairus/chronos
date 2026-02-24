package com.genairus.chronos.mcp.tools;

import com.genairus.chronos.compiler.ChronosCompiler;
import com.genairus.chronos.compiler.SourceUnit;
import com.genairus.chronos.core.diagnostics.Diagnostic;
import com.genairus.chronos.generators.GeneratorRegistry;
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
 * MCP tool: {@code chronos.generate}
 *
 * <p>Compiles the specified {@code .chronos} files and runs a generator to produce
 * artifacts (PRDs, Jira CSVs, TypeScript types, etc.) in the specified output directory.
 *
 * <p>Set {@code dryRun: true} to validate and preview planned output files without
 * writing to disk. The {@code generated} flag in the response is {@code false} when
 * any error diagnostics are present or when {@code dryRun} is active.
 */
public class GenerateTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "required": ["inputPaths", "outDir", "target"],
              "properties": {
                "inputPaths": {
                  "type": "array",
                  "items": {"type": "string"},
                  "description": "Absolute paths to .chronos files to generate artifacts from."
                },
                "workspaceRoot": {
                  "type": "string",
                  "description": "Workspace root for path security. Defaults to CHRONOS_WORKSPACE env var or CWD."
                },
                "outDir": {
                  "type": "string",
                  "description": "Absolute path to the output directory where files will be written."
                },
                "target": {
                  "type": "string",
                  "description": "Generator target. Known targets: markdown, prd, jira, test-scaffold, typescript, mermaid-state, statemachine-tests."
                },
                "dryRun": {
                  "type": "boolean",
                  "description": "If true, validate and plan output files without writing to disk. Default: false."
                }
              }
            }
            """;

    private final Gson gson = new Gson();
    private final ChronosCompiler compiler = new ChronosCompiler();

    /**
     * Executes the generate logic. Callable from tests without a running MCP server.
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
                return Envelope.error(McpMeta.TOOL_GENERATE,
                        McpMeta.ErrorCode.INVALID_INPUT,
                        "inputPaths is required and must not be empty", false);
            }

            Object outDirRaw = arguments.get("outDir");
            if (outDirRaw == null) {
                return Envelope.error(McpMeta.TOOL_GENERATE,
                        McpMeta.ErrorCode.INVALID_INPUT,
                        "outDir is required", false);
            }
            Path outDir;
            try {
                outDir = PathSecurity.validate(Path.of(outDirRaw.toString()), workspaceRoot);
            } catch (PathSecurity.PathOutsideWorkspaceException e) {
                return Envelope.error(McpMeta.TOOL_GENERATE,
                        McpMeta.ErrorCode.PATH_OUTSIDE_WORKSPACE,
                        "outDir outside workspace: " + outDirRaw, false);
            }

            Object targetRaw = arguments.get("target");
            if (targetRaw == null) {
                return Envelope.error(McpMeta.TOOL_GENERATE,
                        McpMeta.ErrorCode.INVALID_INPUT,
                        "target is required", false);
            }
            String target = targetRaw.toString().trim();

            boolean dryRun = arguments.containsKey("dryRun")
                    && Boolean.parseBoolean(arguments.get("dryRun").toString());

            // Validate target early
            if (!GeneratorRegistry.knownTargets().contains(target)) {
                return Envelope.error(McpMeta.TOOL_GENERATE,
                        McpMeta.ErrorCode.INVALID_INPUT,
                        "Unknown generator target '" + target + "'. Known targets: "
                                + GeneratorRegistry.knownTargets().stream().sorted()
                                        .reduce((a, b) -> a + ", " + b).orElse(""),
                        false);
            }

            // Read and validate source files
            List<SourceUnit> units = new ArrayList<>();
            for (var elem : inputPathsRaw) {
                String pathStr = elem.toString();
                Path path;
                try {
                    path = PathSecurity.validate(Path.of(pathStr), workspaceRoot);
                } catch (PathSecurity.PathOutsideWorkspaceException e) {
                    return Envelope.error(McpMeta.TOOL_GENERATE,
                            McpMeta.ErrorCode.PATH_OUTSIDE_WORKSPACE,
                            "Path outside workspace: " + pathStr, false);
                }
                String text;
                try {
                    text = Files.readString(path);
                } catch (IOException e) {
                    return Envelope.error(McpMeta.TOOL_GENERATE,
                            McpMeta.ErrorCode.INVALID_INPUT,
                            "Cannot read file: " + pathStr, false);
                }
                units.add(new SourceUnit(path.toString(), text));
            }

            var compileResult = compiler.compileAll(units);
            var sortedDiags = sortDiagnostics(compileResult.diagnostics());
            boolean hasErrors = !compileResult.errors().isEmpty();

            var writtenFilesArr = new JsonArray();
            var plannedFilesArr = new JsonArray();

            // Only generate if compilation has no errors and the unit is available
            if (!hasErrors && compileResult.unitOrNull() != null) {
                var generator = GeneratorRegistry.get(target);
                for (var source : compileResult.unitOrNull().compiledSources()) {
                    var output = generator.generate(source.model());
                    for (var entry : output.files().entrySet()) {
                        Path filePath = outDir.resolve(entry.getKey()).normalize();
                        if (!filePath.startsWith(outDir)) {
                            return Envelope.error(McpMeta.TOOL_GENERATE,
                                    McpMeta.ErrorCode.PATH_OUTSIDE_WORKSPACE,
                                    "Generator produced path outside output directory: " + filePath,
                                    false);
                        }

                        var planned = new JsonObject();
                        planned.addProperty("path", filePath.toString());
                        planned.addProperty("generator", target);
                        plannedFilesArr.add(planned);

                        if (!dryRun) {
                            try {
                                Files.createDirectories(filePath.getParent());
                                Files.writeString(filePath, entry.getValue());
                                writtenFilesArr.add(filePath.toString());
                            } catch (IOException writeErr) {
                                var details = new JsonObject();
                                details.addProperty("failedPath", filePath.toString());
                                details.add("writtenFiles", writtenFilesArr.deepCopy());
                                details.add("plannedFiles", plannedFilesArr.deepCopy());
                                details.addProperty("diagnosticSort", McpMeta.DIAGNOSTIC_SORT);
                                details.add("diagnostics", buildDiagnosticsArray(sortedDiags));

                                return Envelope.error(McpMeta.TOOL_GENERATE,
                                        McpMeta.ErrorCode.INTERNAL_ERROR,
                                        "Generation failed while writing '" + filePath
                                                + "' after writing " + writtenFilesArr.size()
                                                + " file(s)",
                                        false,
                                        details);
                            }
                        }
                    }
                }
            }

            boolean generated = !dryRun && !hasErrors && writtenFilesArr.size() > 0;

            var resultJson = new JsonObject();
            resultJson.addProperty("generated", generated);
            resultJson.addProperty("dryRun", dryRun);
            resultJson.add("writtenFiles", writtenFilesArr);
            // plannedFiles only populated on dryRun; empty array otherwise
            resultJson.add("plannedFiles", dryRun ? plannedFilesArr : new JsonArray());
            resultJson.addProperty("diagnosticSort", McpMeta.DIAGNOSTIC_SORT);
            resultJson.add("diagnostics", buildDiagnosticsArray(sortedDiags));

            return Envelope.success(McpMeta.TOOL_GENERATE, resultJson);

        } catch (Exception e) {
            return Envelope.error(McpMeta.TOOL_GENERATE,
                    McpMeta.ErrorCode.INTERNAL_ERROR,
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), true);
        }
    }

    /** Returns the fully wired MCP tool specification. */
    public McpServerFeatures.SyncToolSpecification specification() {
        var mapper = McpJsonDefaults.getMapper();
        var tool = McpSchema.Tool.builder()
                .name(McpMeta.TOOL_GENERATE)
                .description("Generate artifacts from validated .chronos files. "
                        + "Use dryRun:true to preview planned output files without writing to disk. "
                        + "generated:false when any error diagnostics are present — fix errors first. "
                        + "All paths in writtenFiles and plannedFiles are absolute.")
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
                        .comparing(GenerateTool::sortPath)
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
            arr.add(obj);
        }
        return arr;
    }
}
