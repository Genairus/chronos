package com.genairus.chronos.generators;

import java.util.Map;

/**
 * Maps target-name strings to {@link ChronosGenerator} instances.
 *
 * <p>Supported targets:
 * <ul>
 *   <li>{@code "markdown"} — Markdown PRD document</li>
 *   <li>{@code "prd"} — alias for {@code "markdown"}</li>
 * </ul>
 *
 * <p>The registry is stateless; all generators are singletons shared across calls.
 */
public final class GeneratorRegistry {

    private static final ChronosGenerator MARKDOWN = new MarkdownPrdGenerator();

    private static final Map<String, ChronosGenerator> REGISTRY = Map.of(
            "markdown", MARKDOWN,
            "prd",      MARKDOWN
    );

    private GeneratorRegistry() {}

    /**
     * Returns the generator for the given target name.
     *
     * @param target case-sensitive target name (e.g. {@code "markdown"})
     * @return the corresponding {@link ChronosGenerator}
     * @throws IllegalArgumentException if {@code target} is not a known generator
     */
    public static ChronosGenerator get(String target) {
        ChronosGenerator gen = REGISTRY.get(target);
        if (gen == null) {
            throw new IllegalArgumentException("Unknown generator target: " + target);
        }
        return gen;
    }

    /**
     * Returns the set of all known target names that can be passed to {@link #get(String)}.
     *
     * @return an unmodifiable view of the registered target names
     */
    public static java.util.Set<String> knownTargets() {
        return REGISTRY.keySet();
    }
}
