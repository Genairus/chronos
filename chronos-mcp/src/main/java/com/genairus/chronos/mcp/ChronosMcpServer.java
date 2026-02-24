package com.genairus.chronos.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Entry point for the Chronos MCP server.
 *
 * <p>Listens on stdin/stdout using the MCP JSON-RPC 2.0 protocol.
 * All 9 tools are registered via {@link McpToolRegistry}.
 *
 * <p>Usage:
 * <pre>
 *   java -jar chronos-mcp.jar
 * </pre>
 *
 * Configure in {@code ~/.claude/settings.json}:
 * <pre>
 * {
 *   "mcpServers": {
 *     "chronos": {
 *       "command": "java",
 *       "args": ["-jar", "/path/to/chronos-mcp.jar"],
 *       "env": { "CHRONOS_WORKSPACE": "/path/to/your/project" }
 *     }
 *   }
 * }
 * </pre>
 */
public class ChronosMcpServer {

    public static void main(String[] args) {
        var transportProvider = new StdioServerTransportProvider(McpJsonDefaults.getMapper());
        var registry = new McpToolRegistry();
        var specs = registry.toolSpecifications();

        McpSyncServer server = McpServer.sync(transportProvider)
                .serverInfo("chronos-mcp", McpVersion.VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(specs)
                .build();
        Runtime.getRuntime().addShutdownHook(new Thread(server::closeGracefully));

        // Block main thread — the MCP server runs on background threads spawned by the
        // transport provider.  Without this, main() returns and the JVM exits before any
        // client messages can be processed.  The process exits when:
        //   (a) the MCP client terminates the child process (typical production path), or
        //   (b) this thread is interrupted (e.g. the transport signals EOF on stdin).
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
