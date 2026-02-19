package com.genairus.chronos.generators;

import java.util.Map;

/**
 * Maps target-name strings to {@link ChronosGenerator} instances.
 *
 * <p>Supported targets:
 * <ul>
 *   <li>{@code "markdown"} — Markdown PRD document</li>
 *   <li>{@code "prd"} — alias for {@code "markdown"}</li>
 *   <li>{@code "test-scaffold"} — JUnit test scaffolding for invariants</li>
 *   <li>{@code "typescript"} — TypeScript type definitions (.d.ts)</li>
 *   <li>{@code "mermaid-state"} — Mermaid state diagrams for statemachines</li>
 *   <li>{@code "statemachine-tests"} — JUnit test scaffolding for state machine transitions</li>
 * </ul>
 *
 * <p>The registry is stateless; all generators are singletons shared across calls.
 */
public final class GeneratorRegistry {

    private static final ChronosGenerator MARKDOWN = new MarkdownPrdGenerator();
    private static final ChronosGenerator TEST_SCAFFOLD = new TestScaffoldGenerator();
    private static final ChronosGenerator TYPESCRIPT = new TypeScriptTypesGenerator();
    private static final ChronosGenerator MERMAID_STATE = new MermaidStateDiagramGenerator();
    private static final ChronosGenerator STATEMACHINE_TESTS = new StateMachineTestGenerator();

    private static final Map<String, ChronosGenerator> REGISTRY = Map.ofEntries(
            Map.entry("markdown", MARKDOWN),
            Map.entry("prd", MARKDOWN),
            Map.entry("test-scaffold", TEST_SCAFFOLD),
            Map.entry("typescript", TYPESCRIPT),
            Map.entry("mermaid-state", MERMAID_STATE),
            Map.entry("statemachine-tests", STATEMACHINE_TESTS)
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
