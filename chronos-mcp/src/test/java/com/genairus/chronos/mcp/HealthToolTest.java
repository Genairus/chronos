package com.genairus.chronos.mcp;

import com.genairus.chronos.mcp.response.McpMeta;
import com.genairus.chronos.mcp.tools.HealthTool;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HealthTool}.
 */
class HealthToolTest {

    private final HealthTool tool = new HealthTool();

    @Test
    void returnsStatusOk() {
        var env = tool.execute(Map.of());
        assertFalse(env.has("error"), "health must never return error envelope");
        assertEquals("ok", env.getAsJsonObject("result").get("status").getAsString());
    }

    @Test
    void returnsAllNineTools() {
        var env = tool.execute(Map.of());
        var result = env.getAsJsonObject("result");
        var toolsArr = result.getAsJsonArray("tools");

        assertEquals(9, toolsArr.size(), "Must have exactly 9 tools");

        Set<String> returned = StreamSupport.stream(toolsArr.spliterator(), false)
                .map(e -> e.getAsString())
                .collect(Collectors.toSet());

        // Verify all expected tool names are present
        assertTrue(returned.contains(McpMeta.TOOL_VALIDATE));
        assertTrue(returned.contains(McpMeta.TOOL_EXPLAIN_DIAGNOSTIC));
        assertTrue(returned.contains(McpMeta.TOOL_DESCRIBE_SHAPE));
        assertTrue(returned.contains(McpMeta.TOOL_GENERATE));
        assertTrue(returned.contains(McpMeta.TOOL_EMIT_IR_BUNDLE));
        assertTrue(returned.contains(McpMeta.TOOL_SCAFFOLD));
        assertTrue(returned.contains(McpMeta.TOOL_LIST_SYMBOLS));
        assertTrue(returned.contains(McpMeta.TOOL_DISCOVER));
        assertTrue(returned.contains(McpMeta.TOOL_HEALTH));
    }

    @Test
    void compilerVersionIsNonBlank() {
        var env = tool.execute(Map.of());
        var version = env.getAsJsonObject("result").get("compilerVersion").getAsString();
        assertFalse(version.isBlank(), "compilerVersion must not be blank");
    }

    @Test
    void serverVersionIsNonBlank() {
        var env = tool.execute(Map.of());
        var version = env.getAsJsonObject("result").get("serverVersion").getAsString();
        assertFalse(version.isBlank(), "serverVersion must not be blank");
    }

    @Test
    void apiVersionPresent() {
        var env = tool.execute(Map.of());
        var result = env.getAsJsonObject("result");
        assertTrue(result.has("apiVersion"), "apiVersion must be present");
        assertFalse(result.get("apiVersion").getAsString().isBlank(),
                "apiVersion must not be blank");
    }

    @Test
    void toolCountMatchesAllToolsConstant() {
        var env = tool.execute(Map.of());
        var toolsArr = env.getAsJsonObject("result").getAsJsonArray("tools");
        assertEquals(McpMeta.ALL_TOOLS.size(), toolsArr.size(),
                "Tool count must match McpMeta.ALL_TOOLS.size()");
    }

    @Test
    void responseHasEnvelopeFields() {
        var env = tool.execute(Map.of());
        assertTrue(env.has("schemaVersion"), "must have schemaVersion");
        assertTrue(env.has("toolVersion"), "must have toolVersion");
    }

    @Test
    void noArgumentsRequired() {
        // Health must succeed even with completely empty arguments
        assertDoesNotThrow(() -> {
            var env = tool.execute(Map.of());
            assertFalse(env.has("error"));
        });
    }
}
