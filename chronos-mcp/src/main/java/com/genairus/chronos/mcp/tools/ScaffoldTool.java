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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP tool: {@code chronos.scaffold}
 *
 * <p>Generates a {@code .chronos} file scaffold for one or more shape types.
 * Template content comes from {@link ShapeKnowledge} — built-time generated
 * from YAML overlays. When {@code compilable=true}, the content is expected to
 * pass {@code chronos.validate} with zero errors after the user fills in
 * referenced type names. When {@code compilable=false}, {@code notes[]} explains
 * what cross-references must be supplied.
 */
public class ScaffoldTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "required": ["namespace", "shapes"],
              "properties": {
                "namespace": {
                  "type": "string",
                  "description": "Namespace for the generated file (e.g. com.example.domain)."
                },
                "shapes": {
                  "type": "array",
                  "items": {"type": "string"},
                  "description": "Shape types to scaffold. Each item is a shape type name: entity, shape, list, map, enum, actor, policy, journey, relationship, invariant, deny, error, statemachine, role, event."
                },
                "includeComments": {
                  "type": "boolean",
                  "description": "Include inline // comments in the scaffold. Defaults to true."
                }
              }
            }
            """;

    private final Gson gson = new Gson();

    /**
     * Executes the scaffold logic. Callable from tests without a running MCP server.
     *
     * @param arguments raw tool arguments
     * @return complete envelope JSON (success or error)
     */
    public JsonObject execute(Map<String, Object> arguments) {
        try {
            var nsRaw = arguments.get("namespace");
            if (nsRaw == null || nsRaw.toString().isBlank()) {
                return Envelope.error(McpMeta.TOOL_SCAFFOLD,
                        McpMeta.ErrorCode.INVALID_INPUT, "namespace is required", false);
            }
            String namespace = nsRaw.toString().trim();

            @SuppressWarnings("unchecked")
            var shapesRaw = (List<Object>) arguments.get("shapes");
            if (shapesRaw == null || shapesRaw.isEmpty()) {
                return Envelope.error(McpMeta.TOOL_SCAFFOLD,
                        McpMeta.ErrorCode.INVALID_INPUT, "shapes is required and must not be empty", false);
            }

            boolean includeComments = !arguments.containsKey("includeComments")
                    || Boolean.parseBoolean(arguments.get("includeComments").toString());

            // Validate all shape types first
            var unknownShapes = new ArrayList<String>();
            for (var raw : shapesRaw) {
                String s = raw.toString().toLowerCase().trim();
                if (!ShapeKnowledge.REGISTRY.containsKey(s)) {
                    unknownShapes.add(raw.toString());
                }
            }
            if (!unknownShapes.isEmpty()) {
                return Envelope.error(McpMeta.TOOL_SCAFFOLD,
                        McpMeta.ErrorCode.INVALID_INPUT,
                        "Unknown shape types: " + unknownShapes + ". Valid types: "
                                + String.join(", ", ShapeKnowledge.REGISTRY.keySet().stream().sorted().toList()),
                        false);
            }

            var sb = new StringBuilder();
            sb.append("namespace ").append(namespace).append("\n");

            boolean compilable = true;
            var allNotes = new ArrayList<String>();

            for (var raw : shapesRaw) {
                String shapeName = raw.toString().toLowerCase().trim();
                var entry = ShapeKnowledge.REGISTRY.get(shapeName);

                sb.append("\n");
                String rendered = renderTemplate(entry, includeComments);
                sb.append(rendered);
                if (!rendered.endsWith("\n")) {
                    sb.append("\n");
                }

                if (!entry.compilable()) {
                    compilable = false;
                }
                for (var note : entry.notes()) {
                    if (!allNotes.contains(note)) {
                        allNotes.add(note);
                    }
                }
            }

            if (!compilable) {
                String crossRefNote = "This scaffold requires cross-references — "
                        + "use chronos.list_symbols to find existing shapes to reference.";
                if (!allNotes.contains(crossRefNote)) {
                    allNotes.add(crossRefNote);
                }
            }

            var result = new JsonObject();
            result.addProperty("content", sb.toString());
            result.addProperty("compilable", compilable);
            var notesArr = new JsonArray();
            allNotes.forEach(notesArr::add);
            result.add("notes", notesArr);

            return Envelope.success(McpMeta.TOOL_SCAFFOLD, result);

        } catch (Exception e) {
            return Envelope.error(McpMeta.TOOL_SCAFFOLD,
                    McpMeta.ErrorCode.INTERNAL_ERROR,
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), true);
        }
    }

    private String renderTemplate(ShapeKnowledge.ShapeEntry entry, boolean includeComments) {
        String template = entry.scaffoldTemplate();
        String name = "My" + capitalize(entry.shape());

        template = template
                .replace("{{Name}}", name)
                .replace("{{ActorName}}", "MyActor")
                .replace("{{EntityName}}", "MyEntity")
                .replace("{{fieldName}}", "myStatus")
                .replace("{{FromEntity}}", "FromEntity")
                .replace("{{ToEntity}}", "ToEntity")
                .replace("{{KeyType}}", "String")
                .replace("{{ElementType}}", "String")
                .replace("{{ValueType}}", "String")
                .replace("{{permission_name}}", "read")
                .replace("{{ERROR_CODE}}", "MY_ERROR")
                .replace("{{StepName}}", "DoSomething");

        if (!includeComments) {
            // Remove lines that are only // comments
            template = template.lines()
                    .filter(line -> !line.trim().startsWith("//"))
                    .collect(java.util.stream.Collectors.joining("\n"));
        }

        return template;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Returns the fully wired MCP tool specification. */
    public McpServerFeatures.SyncToolSpecification specification() {
        var mapper = McpJsonDefaults.getMapper();
        var tool = McpSchema.Tool.builder()
                .name(McpMeta.TOOL_SCAFFOLD)
                .description("Generate a .chronos file scaffold for one or more shape types. "
                        + "Returns compilable=true when the content can pass chronos.validate with zero errors. "
                        + "When compilable=false, notes[] explains what cross-references must be added. "
                        + "Always run chronos.validate after editing the scaffold.")
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
