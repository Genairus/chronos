package com.genairus.chronos.mcp.response;

import java.util.List;

/**
 * Constants for the MCP response envelope contract.
 *
 * <p>{@link #SCHEMA_VERSION} is bumped when any response shape changes in a breaking way.
 * Clients should reject responses with unknown {@code schemaVersion}.
 */
public final class McpMeta {

    /** Current contract version — included in every response envelope. */
    public static final String SCHEMA_VERSION = "1.0";

    /** Sort order applied to every {@code diagnostics[]} array in tool responses. */
    public static final String DIAGNOSTIC_SORT = "path,line,col,code";

    // ── Tool name constants ────────────────────────────────────────────────

    public static final String TOOL_VALIDATE          = "chronos.validate";
    public static final String TOOL_EXPLAIN_DIAGNOSTIC = "chronos.explain_diagnostic";
    public static final String TOOL_DESCRIBE_SHAPE    = "chronos.describe_shape";
    public static final String TOOL_GENERATE          = "chronos.generate";
    public static final String TOOL_EMIT_IR_BUNDLE    = "chronos.emit_ir_bundle";
    public static final String TOOL_SCAFFOLD          = "chronos.scaffold";
    public static final String TOOL_LIST_SYMBOLS      = "chronos.list_symbols";
    public static final String TOOL_DISCOVER          = "chronos.discover";
    public static final String TOOL_HEALTH            = "chronos.health";

    public static final List<String> ALL_TOOLS = java.util.List.of(
            TOOL_VALIDATE, TOOL_EXPLAIN_DIAGNOSTIC, TOOL_DESCRIBE_SHAPE,
            TOOL_GENERATE, TOOL_EMIT_IR_BUNDLE, TOOL_SCAFFOLD,
            TOOL_LIST_SYMBOLS, TOOL_DISCOVER, TOOL_HEALTH
    );

    // ── Error code constants ───────────────────────────────────────────────

    public enum ErrorCode {
        INVALID_INPUT,
        PATH_OUTSIDE_WORKSPACE,
        COMPILE_ERROR,
        INTERNAL_ERROR
    }

    private McpMeta() {}
}
