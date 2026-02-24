package com.genairus.chronos.mcp.tools;

import com.genairus.chronos.mcp.knowledge.ShapeKnowledge;
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
 * MCP tool: {@code chronos.describe_shape}
 *
 * <p>Returns documentation, field definitions, applicable validation rules, common
 * mistakes, and code examples for any of the 15 Chronos shape types. Data is served
 * from {@link ShapeKnowledge} — the build-time generated registry backed by YAML overlays.
 */
public class DescribeShapeTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "required": ["shape"],
              "properties": {
                "shape": {
                  "type": "string",
                  "description": "Shape type to describe. One of: entity, shape, list, map, enum, actor, policy, journey, relationship, invariant, deny, error, statemachine, role, event."
                },
                "includeExample": {
                  "type": "boolean",
                  "description": "Include code examples (minimalExample, fullExample, scaffoldTemplate). Defaults to true."
                }
              }
            }
            """;

    private final Gson gson = new Gson();

    /**
     * Executes the describe_shape logic. Callable from tests without a running MCP server.
     *
     * @param arguments raw tool arguments
     * @return complete envelope JSON (success or error)
     */
    public JsonObject execute(Map<String, Object> arguments) {
        try {
            var shapeRaw = arguments.get("shape");
            if (shapeRaw == null || shapeRaw.toString().isBlank()) {
                return Envelope.error(McpMeta.TOOL_DESCRIBE_SHAPE,
                        McpMeta.ErrorCode.INVALID_INPUT, "shape is required", false);
            }
            String shapeName = shapeRaw.toString().toLowerCase().trim();

            boolean includeExample = !arguments.containsKey("includeExample")
                    || Boolean.parseBoolean(arguments.get("includeExample").toString());

            var entry = ShapeKnowledge.REGISTRY.get(shapeName);
            if (entry == null) {
                var validTypes = ShapeKnowledge.REGISTRY.keySet().stream().sorted().toList();
                return Envelope.error(McpMeta.TOOL_DESCRIBE_SHAPE,
                        McpMeta.ErrorCode.INVALID_INPUT,
                        "Unknown shape type: '" + shapeName + "'. Valid types: "
                                + String.join(", ", validTypes),
                        false);
            }

            var result = new JsonObject();
            result.addProperty("shape", entry.shape());
            result.addProperty("description", entry.description());
            result.addProperty("compilable", entry.compilable());

            var rulesArr = new JsonArray();
            entry.applicableRules().forEach(rulesArr::add);
            result.add("applicableRules", rulesArr);

            var reqFields = new JsonArray();
            for (var f : entry.requiredFields()) {
                var fo = new JsonObject();
                fo.addProperty("name", f.name());
                fo.addProperty("type", f.type());
                fo.addProperty("notes", f.notes());
                reqFields.add(fo);
            }
            result.add("requiredFields", reqFields);

            var optFields = new JsonArray();
            for (var f : entry.optionalFields()) {
                var fo = new JsonObject();
                fo.addProperty("name", f.name());
                fo.addProperty("type", f.type());
                fo.addProperty("notes", f.notes());
                optFields.add(fo);
            }
            result.add("optionalFields", optFields);

            var mistakes = new JsonArray();
            entry.commonMistakes().forEach(mistakes::add);
            result.add("commonMistakes", mistakes);

            var notes = new JsonArray();
            entry.notes().forEach(notes::add);
            result.add("notes", notes);

            if (includeExample) {
                result.addProperty("minimalExample", entry.minimalExample());
                result.addProperty("fullExample", entry.fullExample());
                result.addProperty("scaffoldTemplate", entry.scaffoldTemplate());
            }

            return Envelope.success(McpMeta.TOOL_DESCRIBE_SHAPE, result);

        } catch (Exception e) {
            return Envelope.error(McpMeta.TOOL_DESCRIBE_SHAPE,
                    McpMeta.ErrorCode.INTERNAL_ERROR,
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), true);
        }
    }

    /** Returns the fully wired MCP tool specification. */
    public McpServerFeatures.SyncToolSpecification specification() {
        var mapper = McpJsonDefaults.getMapper();
        var tool = McpSchema.Tool.builder()
                .name(McpMeta.TOOL_DESCRIBE_SHAPE)
                .description("Get documentation, field definitions, validation rules, and code examples "
                        + "for any Chronos shape type (entity, journey, actor, enum, etc.). "
                        + "Call this before authoring a new shape to understand required fields and common mistakes.")
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
