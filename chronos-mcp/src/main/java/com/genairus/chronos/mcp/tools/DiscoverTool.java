package com.genairus.chronos.mcp.tools;

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
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * MCP tool: {@code chronos.discover}
 *
 * <p>Walks the workspace directory tree and lists all {@code .chronos} files,
 * extracting the namespace declaration from each (first 100 lines). Skips
 * hidden directories and symbolic links.
 *
 * <p>Agents should call this before creating or editing any files to understand
 * what already exists in the workspace.
 */
public class DiscoverTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "workspaceRoot": {
                  "type": "string",
                  "description": "Root directory to search. Defaults to CHRONOS_WORKSPACE env var or CWD."
                },
                "maxDepth": {
                  "type": "integer",
                  "description": "Maximum directory recursion depth. Default: 10.",
                  "default": 10
                }
              }
            }
            """;

    private static final Pattern NAMESPACE_PATTERN =
            Pattern.compile("^\\s*namespace\\s+(\\S+)", Pattern.MULTILINE);

    private final Gson gson = new Gson();

    /**
     * Executes the discover logic.  Callable from tests without a running MCP server.
     *
     * @param arguments  raw tool arguments (matches the JSON Schema above)
     * @return           complete envelope JSON (success or error)
     */
    public JsonObject execute(Map<String, Object> arguments) {
        try {
            Path workspaceRoot = resolveWorkspaceRoot(arguments);
            int maxDepth = 10;
            if (arguments.containsKey("maxDepth") && arguments.get("maxDepth") != null) {
                maxDepth = ((Number) arguments.get("maxDepth")).intValue();
            }

            var files = discoverFiles(workspaceRoot, maxDepth);

            var resultJson = new JsonObject();
            resultJson.addProperty("workspaceRoot", workspaceRoot.toString());
            resultJson.add("files", files);
            return Envelope.success(McpMeta.TOOL_DISCOVER, resultJson);

        } catch (Exception e) {
            return Envelope.error(McpMeta.TOOL_DISCOVER,
                    McpMeta.ErrorCode.INTERNAL_ERROR,
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), true);
        }
    }

    /** Returns the fully wired MCP tool specification. */
    public McpServerFeatures.SyncToolSpecification specification() {
        var mapper = McpJsonDefaults.getMapper();
        var tool = McpSchema.Tool.builder()
                .name(McpMeta.TOOL_DISCOVER)
                .description("Walk the workspace and list all .chronos files with their namespaces. "
                        + "Skips hidden directories and symbolic links. "
                        + "Call this before creating or editing any files.")
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

    private JsonArray discoverFiles(Path workspaceRoot, int maxDepth) throws IOException {
        var files = new JsonArray();
        try (var walk = Files.walk(workspaceRoot, maxDepth)) {
            walk
                .filter(p -> !isHiddenOrUnderHiddenDir(p, workspaceRoot))
                .filter(p -> !Files.isSymbolicLink(p))
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".chronos"))
                .sorted()
                .forEach(p -> {
                    var absPath = p.toAbsolutePath().normalize();
                    var entry = new JsonObject();
                    entry.addProperty("path", absPath.toString());
                    var ns = extractNamespace(absPath);
                    if (ns != null) {
                        entry.addProperty("namespace", ns);
                    } else {
                        entry.add("namespace", com.google.gson.JsonNull.INSTANCE);
                    }
                    try {
                        entry.addProperty("sizeBytes", Files.size(absPath));
                    } catch (IOException e) {
                        entry.addProperty("sizeBytes", -1L);
                    }
                    files.add(entry);
                });
        }
        return files;
    }

    /**
     * Returns {@code true} if any component of the path (relative to root) starts with {@code '.'}.
     * This skips both hidden directories and files inside hidden directories.
     */
    private boolean isHiddenOrUnderHiddenDir(Path path, Path root) {
        try {
            Path relative = root.relativize(path.toAbsolutePath().normalize());
            for (int i = 0; i < relative.getNameCount(); i++) {
                if (relative.getName(i).toString().startsWith(".")) {
                    return true;
                }
            }
        } catch (IllegalArgumentException e) {
            return true;
        }
        return false;
    }

    /** Scans up to the first 100 lines for a {@code namespace} declaration. */
    private String extractNamespace(Path path) {
        try (var lines = Files.lines(path)) {
            var content = lines.limit(100).collect(Collectors.joining("\n"));
            var matcher = NAMESPACE_PATTERN.matcher(content);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (IOException e) {
            // ignore — return null
        }
        return null;
    }

    private Path resolveWorkspaceRoot(Map<String, Object> arguments) {
        return PathSecurity.resolveWorkspaceRoot(arguments);
    }
}
