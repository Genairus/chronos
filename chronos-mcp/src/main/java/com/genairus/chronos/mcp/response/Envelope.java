package com.genairus.chronos.mcp.response;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

/**
 * Response envelope for all Chronos MCP tools.
 *
 * <p>Every tool response is wrapped in this envelope. Two shapes exist:
 *
 * <p><b>Success:</b>
 * <pre>
 * {
 *   "schemaVersion": "1.0",
 *   "toolVersion": "validate@1.0",
 *   "result": { ... }
 * }
 * </pre>
 *
 * <p><b>Error:</b>
 * <pre>
 * {
 *   "schemaVersion": "1.0",
 *   "toolVersion": "validate@1.0",
 *   "error": {
 *     "code": "INVALID_INPUT",
 *     "message": "...",
 *     "retryable": false,
 *     "details": {}
 *   }
 * }
 * </pre>
 *
 * <p>Clients MUST check for {@code "error"} before reading {@code "result"}.
 * The {@code toolVersion} format is {@code "<toolName>@<semver>"}.
 */
public final class Envelope {

    private Envelope() {}

    /**
     * Wraps a successful result payload.
     *
     * @param toolName  the bare tool name (e.g. {@code "validate"}), not the full qualified name
     * @param result    the result JSON object
     * @return          complete envelope JSON object
     */
    public static JsonObject success(String toolName, JsonObject result) {
        var env = new JsonObject();
        env.addProperty("schemaVersion", McpMeta.SCHEMA_VERSION);
        env.addProperty("toolVersion", toolName + "@" + McpMeta.SCHEMA_VERSION);
        env.add("result", result);
        return env;
    }

    /**
     * Wraps an error.
     *
     * @param toolName  the bare tool name
     * @param code      error code from {@link McpMeta.ErrorCode}
     * @param message   human-readable description
     * @param retryable whether the caller may usefully retry
     * @param details   optional free-form JSON details, or {@code null}
     * @return          complete envelope JSON object
     */
    public static JsonObject error(String toolName, McpMeta.ErrorCode code,
                                   String message, boolean retryable,
                                   JsonElement details) {
        var env = new JsonObject();
        env.addProperty("schemaVersion", McpMeta.SCHEMA_VERSION);
        env.addProperty("toolVersion", toolName + "@" + McpMeta.SCHEMA_VERSION);
        var err = new JsonObject();
        err.addProperty("code", code.name());
        err.addProperty("message", message);
        err.addProperty("retryable", retryable);
        err.add("details", details != null ? details : JsonNull.INSTANCE);
        env.add("error", err);
        return env;
    }

    /** Convenience overload with no details object. */
    public static JsonObject error(String toolName, McpMeta.ErrorCode code,
                                   String message, boolean retryable) {
        return error(toolName, code, message, retryable, null);
    }
}
