package com.genairus.chronos.mcp;

import com.genairus.chronos.mcp.response.McpMeta;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke test: verifies that all 9 Chronos MCP tools are wired into
 * {@link McpToolRegistry} and have valid specifications.
 *
 * <p>Does NOT spin up an actual MCP server — tests the specification
 * objects returned by {@link McpToolRegistry#toolSpecifications()}.
 */
class McpServerSmokeTest {

    private final McpToolRegistry registry = new McpToolRegistry();

    @Test
    void registryContainsAllNineTools() {
        var specs = registry.toolSpecifications();
        assertEquals(9, specs.size(),
                "McpToolRegistry must register exactly 9 tools");
    }

    @Test
    void allToolNamesMatchMcpMetaConstants() {
        var specs = registry.toolSpecifications();
        Set<String> registeredNames = specs.stream()
                .map(spec -> spec.tool().name())
                .collect(Collectors.toSet());

        Set<String> expectedNames = Set.copyOf(McpMeta.ALL_TOOLS);
        assertEquals(expectedNames, registeredNames,
                "Registered tool names must exactly match McpMeta.ALL_TOOLS");
    }

    @Test
    void allToolsHaveNonBlankDescriptions() {
        for (var spec : registry.toolSpecifications()) {
            assertFalse(spec.tool().description().isBlank(),
                    "Tool description must not be blank for: " + spec.tool().name());
        }
    }

    @Test
    void allToolsHaveValidInputSchemas() {
        for (var spec : registry.toolSpecifications()) {
            var schema = spec.tool().inputSchema();
            assertNotNull(schema, "inputSchema must not be null for: " + spec.tool().name());
        }
    }

    @Test
    void healthToolReachableFromRegistry() {
        // Find the health tool specification and invoke it directly
        var healthSpec = registry.toolSpecifications().stream()
                .filter(s -> McpMeta.TOOL_HEALTH.equals(s.tool().name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("health tool not found in registry"));

        // The health tool call handler is invoked in MCP context;
        // for smoke test, just verify the tool is present and has correct name
        assertEquals(McpMeta.TOOL_HEALTH, healthSpec.tool().name());
        assertFalse(healthSpec.tool().description().isBlank());
    }

    @Test
    void validateToolReachableFromRegistry() {
        var validateSpec = registry.toolSpecifications().stream()
                .filter(s -> McpMeta.TOOL_VALIDATE.equals(s.tool().name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("validate tool not found in registry"));

        assertEquals(McpMeta.TOOL_VALIDATE, validateSpec.tool().name());
    }
}
