package com.genairus.chronos.mcp;

import com.genairus.chronos.mcp.response.Envelope;
import com.genairus.chronos.mcp.response.McpMeta;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the Envelope contract:
 * - schemaVersion and toolVersion always present
 * - success has "result", not "error"
 * - error has "error", not "result"
 * - error fields: code, message, retryable
 */
class EnvelopeTest {

    @Test
    void successEnvelopeHasSchemaVersionAndToolVersion() {
        var result = new JsonObject();
        result.addProperty("foo", "bar");
        var env = Envelope.success(McpMeta.TOOL_VALIDATE, result);

        assertEquals(McpMeta.SCHEMA_VERSION, env.get("schemaVersion").getAsString());
        assertTrue(env.has("toolVersion"), "toolVersion must be present");
        assertTrue(env.get("toolVersion").getAsString().contains("@"),
                "toolVersion must follow <name>@<version> format");
    }

    @Test
    void successEnvelopeHasResultNotError() {
        var result = new JsonObject();
        result.addProperty("count", 42);
        var env = Envelope.success(McpMeta.TOOL_VALIDATE, result);

        assertTrue(env.has("result"), "success envelope must have 'result'");
        assertFalse(env.has("error"), "success envelope must NOT have 'error'");
        assertEquals(42, env.getAsJsonObject("result").get("count").getAsInt());
    }

    @Test
    void errorEnvelopeHasSchemaVersionAndToolVersion() {
        var env = Envelope.error(McpMeta.TOOL_VALIDATE,
                McpMeta.ErrorCode.INVALID_INPUT, "bad input", false);

        assertEquals(McpMeta.SCHEMA_VERSION, env.get("schemaVersion").getAsString());
        assertTrue(env.has("toolVersion"), "toolVersion must be present");
    }

    @Test
    void errorEnvelopeHasErrorNotResult() {
        var env = Envelope.error(McpMeta.TOOL_VALIDATE,
                McpMeta.ErrorCode.INVALID_INPUT, "bad input", false);

        assertTrue(env.has("error"), "error envelope must have 'error'");
        assertFalse(env.has("result"), "error envelope must NOT have 'result'");
    }

    @Test
    void errorEnvelopeHasCorrectFields() {
        var env = Envelope.error(McpMeta.TOOL_VALIDATE,
                McpMeta.ErrorCode.PATH_OUTSIDE_WORKSPACE, "path traversal detected", false);

        var err = env.getAsJsonObject("error");
        assertNotNull(err, "error object must not be null");
        assertEquals("PATH_OUTSIDE_WORKSPACE", err.get("code").getAsString());
        assertEquals("path traversal detected", err.get("message").getAsString());
        assertFalse(err.get("retryable").getAsBoolean(), "retryable must be false");
    }

    @Test
    void retryableTrueIsPreserved() {
        var env = Envelope.error(McpMeta.TOOL_VALIDATE,
                McpMeta.ErrorCode.INTERNAL_ERROR, "server error", true);

        assertTrue(env.getAsJsonObject("error").get("retryable").getAsBoolean());
    }

    @Test
    void allToolNamesProduceConsistentToolVersion() {
        for (var toolName : McpMeta.ALL_TOOLS) {
            var env = Envelope.success(toolName, new JsonObject());
            var tv = env.get("toolVersion").getAsString();
            assertTrue(tv.startsWith(toolName), "toolVersion must start with tool name");
            assertTrue(tv.contains("@"), "toolVersion must contain @");
        }
    }
}
