package com.genairus.chronos.mcp.tools;

import java.util.ArrayList;
import java.util.List;

/**
 * Defensive argument coercion helpers for MCP tool implementations.
 *
 * <p>MCP clients (and proxies like Agent Gateway) may serialize array arguments
 * as a plain String when there is only one element, or as a comma-separated
 * String when there are multiple elements. All helpers normalise to a
 * {@code List<Object>}, preventing {@link ClassCastException} in tool execute
 * methods.
 *
 * <p>Two helpers are provided because comma-splitting is unsafe for some
 * argument types:
 * <ul>
 *   <li>{@link #toList(Object)} wraps a String value as a single-element list
 *       <em>without</em> splitting on commas. Use for path-typed arrays whose
 *       values may legitimately contain commas. Multi-element calls from
 *       comma-joining proxies must be sent as a JSON array to be parsed
 *       correctly.</li>
 *   <li>{@link #toCommaSplitList(Object)} splits a String value on commas.
 *       Use for identifier-typed arrays (shape names, kind filters) whose
 *       values cannot contain commas.</li>
 * </ul>
 */
final class ToolArgs {

    private ToolArgs() {}

    /**
     * Coerces a raw argument value to a list. A single String is wrapped as-is.
     * Use for path-typed arrays where values may legitimately contain commas.
     *
     * @param value the raw value (may be a {@code List<?>}, a {@code String}, or {@code null})
     * @return a {@code List<Object>}, or {@code null} if value is null
     */
    @SuppressWarnings("unchecked")
    static List<Object> toList(Object value) {
        if (value == null) return null;
        if (value instanceof List<?> list) return (List<Object>) list;
        return List.of(value);
    }

    /**
     * Coerces a raw argument value to a list, splitting comma-separated strings.
     * Use for identifier-typed arrays (shape names, kind filters) where values
     * cannot contain commas.
     *
     * @param value the raw value (may be a {@code List<?>}, a {@code String}, or {@code null})
     * @return a {@code List<Object>}, or {@code null} if value is null
     */
    @SuppressWarnings("unchecked")
    static List<Object> toCommaSplitList(Object value) {
        if (value == null) return null;
        if (value instanceof List<?> list) return (List<Object>) list;
        var parts = value.toString().split(",");
        var result = new ArrayList<Object>(parts.length);
        for (var p : parts) {
            var trimmed = p.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }
}