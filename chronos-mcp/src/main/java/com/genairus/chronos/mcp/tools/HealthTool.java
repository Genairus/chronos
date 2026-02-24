package com.genairus.chronos.mcp.tools;

import com.genairus.chronos.mcp.McpVersion;
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
 * MCP tool: {@code chronos.health}
 *
 * <p>Returns server status, API version, compiler version, and the list of all
 * available tool names. Call this first to confirm the server is reachable and
 * to enumerate available tools when the system prompt is limited.
 *
 * <p>This tool always succeeds — it never returns an error envelope.
 */
public class HealthTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {}
            }
            """;

    private final Gson gson = new Gson();

    /**
     * Executes the health check. Callable from tests without a running MCP server.
     *
     * @param arguments raw tool arguments (ignored — no required inputs)
     * @return success envelope with status, versions, and tool list
     */
    public JsonObject execute(Map<String, Object> arguments) {
        var toolsArr = new JsonArray();
        for (var name : McpMeta.ALL_TOOLS) {
            toolsArr.add(name);
        }

        var resultJson = new JsonObject();
        resultJson.addProperty("status", "ok");
        resultJson.addProperty("apiVersion", McpMeta.SCHEMA_VERSION);
        resultJson.addProperty("compilerVersion", resolveCompilerVersion());
        resultJson.addProperty("serverVersion", McpVersion.VERSION);
        resultJson.add("tools", toolsArr);

        return Envelope.success(McpMeta.TOOL_HEALTH, resultJson);
    }

    private String resolveCompilerVersion() {
        var pkg = com.genairus.chronos.compiler.ChronosCompiler.class.getPackage();
        if (pkg != null) {
            var implVersion = pkg.getImplementationVersion();
            if (implVersion != null && !implVersion.isBlank()) {
                return implVersion;
            }
        }
        return McpVersion.VERSION;
    }

    /** Returns the fully wired MCP tool specification. */
    public McpServerFeatures.SyncToolSpecification specification() {
        var mapper = McpJsonDefaults.getMapper();
        var tool = McpSchema.Tool.builder()
                .name(McpMeta.TOOL_HEALTH)
                .description("Returns server status, API version, compiler version, and available tool list. "
                        + "Call this first to confirm the server is reachable and to enumerate available tools.")
                .inputSchema(mapper, INPUT_SCHEMA)
                .build();

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
            var envelope = execute(request.arguments() != null ? request.arguments() : Map.of());
            return McpSchema.CallToolResult.builder()
                    .addTextContent(gson.toJson(envelope))
                    .isError(false)
                    .build();
        });
    }
}
