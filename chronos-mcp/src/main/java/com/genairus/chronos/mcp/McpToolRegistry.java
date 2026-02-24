package com.genairus.chronos.mcp;

import com.genairus.chronos.mcp.tools.DescribeShapeTool;
import com.genairus.chronos.mcp.tools.DiscoverTool;
import com.genairus.chronos.mcp.tools.EmitIrBundleTool;
import com.genairus.chronos.mcp.tools.ExplainDiagnosticTool;
import com.genairus.chronos.mcp.tools.GenerateTool;
import com.genairus.chronos.mcp.tools.HealthTool;
import com.genairus.chronos.mcp.tools.ListSymbolsTool;
import com.genairus.chronos.mcp.tools.ScaffoldTool;
import com.genairus.chronos.mcp.tools.ValidateTool;
import io.modelcontextprotocol.server.McpServerFeatures;

import java.util.List;

/**
 * Registers all 9 Chronos MCP tools.
 *
 * <p>Each entry in {@link #toolSpecifications()} is a fully self-contained
 * {@link McpServerFeatures.SyncToolSpecification}: tool definition (name, description,
 * JSON Schema) paired with its handler lambda. Tools are wired incrementally
 * as phases 1-4 are implemented.
 *
 * <p>Tools: validate, explain_diagnostic, describe_shape, generate, emit_ir_bundle,
 * scaffold, list_symbols, discover, health.
 */
public class McpToolRegistry {

    /**
     * Returns the list of all tool specifications to register with the MCP server.
     * Populated incrementally as tools are implemented in Phases 1-4.
     */
    public List<McpServerFeatures.SyncToolSpecification> toolSpecifications() {
        return List.of(
                new ValidateTool().specification(),
                new DiscoverTool().specification(),
                new ExplainDiagnosticTool().specification(),
                new DescribeShapeTool().specification(),
                new ScaffoldTool().specification(),
                new ListSymbolsTool().specification(),
                new GenerateTool().specification(),
                new EmitIrBundleTool().specification(),
                new HealthTool().specification()
        );
    }
}
