package com.genairus.chronos.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 0 go/no-go spike: verifies the MCP Java SDK (io.modelcontextprotocol.sdk:mcp:1.0.0)
 * resolves, compiles, and the core server/tool API works as expected.
 *
 * <p>These tests do NOT start the stdio transport (which would block on System.in).
 * Instead they verify:
 * <ol>
 *   <li>Core SDK classes are on the classpath.</li>
 *   <li>A {@link McpServerFeatures.SyncToolSpecification} can be constructed with the correct API.</li>
 *   <li>A {@link McpSyncServer} can be built and closed without touching stdin/stdout.</li>
 *   <li>Project Reactor is transitively available.</li>
 *   <li>{@link McpVersion} is generated correctly by the Gradle task.</li>
 * </ol>
 *
 * <p>If this test class fails to compile or all tests fail, stop Phase 1 and investigate
 * the SDK dependency before proceeding.
 */
class McpSdkSpikeTest {

    /** JSON Schema for a tool with no required inputs. */
    private static final String EMPTY_SCHEMA = """
            {"type":"object","properties":{}}
            """;

    // ── 1. Core classes are on the classpath ─────────────────────────────────

    @Test
    void mcpServerClassIsAvailable() {
        assertDoesNotThrow(() -> Class.forName("io.modelcontextprotocol.server.McpServer"),
                "McpServer must be on the classpath — check io.modelcontextprotocol.sdk:mcp:1.0.0");
    }

    @Test
    void stdioTransportClassIsAvailable() {
        assertDoesNotThrow(
                () -> Class.forName("io.modelcontextprotocol.server.transport.StdioServerTransportProvider"),
                "StdioServerTransportProvider must be on the classpath");
    }

    @Test
    void mcpSchemaClassIsAvailable() {
        assertDoesNotThrow(() -> Class.forName("io.modelcontextprotocol.spec.McpSchema"),
                "McpSchema must be on the classpath");
    }

    // ── 2. SyncToolSpecification API ─────────────────────────────────────────

    @Test
    void syncToolSpecificationCanBeConstructed() {
        var mapper = McpJsonDefaults.getMapper();
        var tool = McpSchema.Tool.builder()
                .name("chronos.health")
                .description("Health check")
                .inputSchema(mapper, EMPTY_SCHEMA)
                .build();

        var spec = new McpServerFeatures.SyncToolSpecification(
                tool,
                (exchange, request) -> McpSchema.CallToolResult.builder()
                        .addTextContent("ok")
                        .build()
        );

        assertNotNull(spec, "SyncToolSpecification must be constructable");
        assertNotNull(spec.tool(), "Tool definition must be accessible via spec.tool()");
        assertEquals("chronos.health", spec.tool().name(),
                "Tool name must round-trip through SyncToolSpecification");
        assertEquals("Health check", spec.tool().description());
    }

    @Test
    void callToolResultCanBeBuiltWithTextContent() {
        var result = McpSchema.CallToolResult.builder()
                .addTextContent("pong")
                .isError(false)
                .build();

        assertNotNull(result);
        assertEquals(Boolean.FALSE, result.isError(), "isError must be false for a successful result");
        assertEquals(1, result.content().size());
    }

    // ── 3. Server can be built (without stdio I/O) ───────────────────────────

    @Test
    void mcpSyncServerCanBeBuiltAndClosed() {
        // StdioServerTransportProvider wraps System.in/out but does NOT start
        // blocking reads until a client connects — safe to instantiate in tests.
        var transport = new StdioServerTransportProvider(McpJsonDefaults.getMapper());

        // Tools must be registered via the builder (.tools()), NOT via addTool()
        // after build. addTool() sends a notification to connected clients, which
        // fails with "Failed to enqueue message" when no client is connected.
        var mapper = McpJsonDefaults.getMapper();
        var noOpSpec = new McpServerFeatures.SyncToolSpecification(
                McpSchema.Tool.builder()
                        .name("test.ping")
                        .description("No-op ping")
                        .inputSchema(mapper, EMPTY_SCHEMA)
                        .build(),
                (exchange, request) -> McpSchema.CallToolResult.builder()
                        .addTextContent("pong")
                        .build()
        );

        McpSyncServer server = assertDoesNotThrow(
                () -> McpServer.sync(transport)
                        .serverInfo("chronos-mcp-test", "0.0.0")
                        .capabilities(McpSchema.ServerCapabilities.builder()
                                .tools(true)
                                .build())
                        .tools(noOpSpec)
                        .build(),
                "McpSyncServer must be buildable with a registered tool"
        );

        assertNotNull(server, "McpSyncServer must be created successfully");

        // Close gracefully — completes immediately since no client is connected
        assertDoesNotThrow(server::closeGracefully, "closeGracefully() must not throw");
    }

    // ── 4. Project Reactor is transitively available ──────────────────────────

    @Test
    void projectReactorIsAvailable() {
        assertDoesNotThrow(() -> Class.forName("reactor.core.publisher.Mono"),
                "Project Reactor (reactor.core) must be a transitive dep of mcp:1.0.0");
    }

    // ── 5. McpVersion generated by Gradle task ────────────────────────────────

    @Test
    void mcpVersionIsGenerated() {
        assertNotNull(McpVersion.VERSION, "McpVersion.VERSION must be generated");
        assertFalse(McpVersion.VERSION.isBlank(), "McpVersion.VERSION must not be blank");
        assertEquals("0.1.0", McpVersion.VERSION,
                "McpVersion.VERSION must match project.version from root build.gradle.kts");
    }
}
