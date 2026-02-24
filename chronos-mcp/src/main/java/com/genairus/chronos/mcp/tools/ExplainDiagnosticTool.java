package com.genairus.chronos.mcp.tools;

import com.genairus.chronos.mcp.knowledge.DiagnosticKnowledge;
import com.genairus.chronos.mcp.response.Envelope;
import com.genairus.chronos.mcp.response.McpMeta;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

/**
 * MCP tool: {@code chronos.explain_diagnostic}
 *
 * <p>Returns human/LLM-readable documentation for a given CHR diagnostic code:
 * severity, title, description, likely causes, fixes, and code examples.
 * Coverage is build-enforced — the test suite verifies all 54 codes are present.
 */
public class ExplainDiagnosticTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "required": ["code"],
              "properties": {
                "code": {
                  "type": "string",
                  "description": "The diagnostic code to explain, e.g. 'CHR-001' or 'CHR-W001'"
                },
                "includeExamples": {
                  "type": "boolean",
                  "description": "Whether to include bad/good code examples. Default: true.",
                  "default": true
                }
              }
            }
            """;

    private final Gson gson = new Gson();

    /**
     * Executes the explain logic.  Callable from tests without a running MCP server.
     *
     * @param arguments  raw tool arguments (matches the JSON Schema above)
     * @return           complete envelope JSON (success or error)
     */
    public JsonObject execute(Map<String, Object> arguments) {
        var codeRaw = arguments.get("code");
        if (codeRaw == null) {
            return Envelope.error(McpMeta.TOOL_EXPLAIN_DIAGNOSTIC,
                    McpMeta.ErrorCode.INVALID_INPUT, "code is required", false);
        }
        String code = codeRaw.toString().trim().toUpperCase();

        boolean includeExamples = true;
        if (arguments.containsKey("includeExamples") && arguments.get("includeExamples") != null) {
            includeExamples = Boolean.parseBoolean(arguments.get("includeExamples").toString());
        }

        var entry = DiagnosticKnowledge.REGISTRY.get(code);
        if (entry == null) {
            return Envelope.error(McpMeta.TOOL_EXPLAIN_DIAGNOSTIC,
                    McpMeta.ErrorCode.INVALID_INPUT,
                    "Unknown diagnostic code: " + code + ". Known codes: CHR-001 through CHR-053 and CHR-W001.",
                    false);
        }

        var resultJson = new JsonObject();
        resultJson.addProperty("code", entry.code());
        resultJson.addProperty("severity", entry.severity().name());
        resultJson.addProperty("title", entry.title());
        resultJson.addProperty("description", entry.description());

        var causesArr = new JsonArray();
        entry.likelyCauses().forEach(causesArr::add);
        resultJson.add("likelyCauses", causesArr);

        var fixesArr = new JsonArray();
        entry.fixes().forEach(fixesArr::add);
        resultJson.add("fixes", fixesArr);

        if (includeExamples && !entry.examples().isEmpty()) {
            var examplesArr = new JsonArray();
            for (var ex : entry.examples()) {
                var exObj = new JsonObject();
                exObj.addProperty("label", ex.label());
                exObj.addProperty("bad", ex.bad());
                exObj.addProperty("good", ex.good());
                examplesArr.add(exObj);
            }
            resultJson.add("examples", examplesArr);
        } else {
            resultJson.add("examples", new JsonArray());
        }

        return Envelope.success(McpMeta.TOOL_EXPLAIN_DIAGNOSTIC, resultJson);
    }

    /** Returns the fully wired MCP tool specification. */
    public McpServerFeatures.SyncToolSpecification specification() {
        var mapper = McpJsonDefaults.getMapper();
        var tool = McpSchema.Tool.builder()
                .name(McpMeta.TOOL_EXPLAIN_DIAGNOSTIC)
                .description("Return documentation for a Chronos diagnostic code: severity, title, description, "
                        + "likely causes, fixes, and code examples. "
                        + "Call this whenever you see a CHR-xxx code in chronos.validate output.")
                .inputSchema(mapper, INPUT_SCHEMA)
                .build();

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
            var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
            var envelope = execute(args);
            return McpSchema.CallToolResult.builder()
                    .addTextContent(gson.toJson(envelope))
                    .isError(envelope.has("error"))
                    .build();
        });
    }
}
