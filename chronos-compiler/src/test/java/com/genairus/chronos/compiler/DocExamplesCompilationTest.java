package com.genairus.chronos.compiler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Regression guard that compiles (and semantically validates) every complete Chronos
 * code block embedded in the documentation source files.
 *
 * <p>Unlike {@link QuickReferenceDocTest} (parser module, parse-only), this test
 * runs the full compilation pipeline — parser → IR → type-resolution → cross-link
 * → validation → finalize — so that diagnostic errors in documented code are caught
 * in CI, not only parse failures.
 *
 * <p>Docs covered:
 * <ul>
 *   <li>{@code docs/index.md} — the "In 30 seconds" self-contained block</li>
 *   <li>{@code docs/examples/getting-started.md} — domain + journeys pair</li>
 *   <li>{@code docs/quick-reference.md} — the minimal two-file example at the end</li>
 * </ul>
 *
 * <p>Requires the {@code chronos.rootDir} system property (set by Gradle's
 * {@code tasks.test} block in {@code chronos-compiler/build.gradle.kts}).
 */
class DocExamplesCompilationTest {

    private static Path docsDir;

    @BeforeAll
    static void resolveDocsDir() {
        String rootDir = System.getProperty("chronos.rootDir");
        assumeTrue(rootDir != null,
                "chronos.rootDir system property not set — skipping doc compilation tests");
        docsDir = Path.of(rootDir, "docs");
        assumeTrue(Files.isDirectory(docsDir),
                "docs/ directory not found at " + docsDir + " — skipping");
    }

    // ── docs/index.md ─────────────────────────────────────────────────────────

    /**
     * The "In 30 seconds" block in the homepage is the first thing a new user sees.
     * It must compile and validate without errors.
     */
    @Test
    void indexMd_inThirtySecondsBlock_compilesWithoutErrors() throws IOException {
        Path doc = docsDir.resolve("index.md");
        List<String> blocks = extractCompleteChronosBlocks(Files.readString(doc));
        assertFalse(blocks.isEmpty(), "docs/index.md must contain at least one complete chronos block");

        for (String block : blocks) {
            String name = namespaceLine(block);
            var result = new ChronosCompiler().compile(block, "docs/index.md");
            assertTrue(result.finalized(),
                    "docs/index.md block [" + name + "] must compile without errors; got: "
                            + result.diagnostics());
        }
    }

    // ── docs/examples/getting-started.md ─────────────────────────────────────

    /**
     * The getting-started guide contains a domain file and a journeys file that
     * must be compiled together (journeys imports from domain).
     * Both must compile and validate without errors.
     */
    @Test
    void gettingStartedMd_domainAndJourneysPair_compilesWithoutErrors() throws IOException {
        Path doc = docsDir.resolve("examples/getting-started.md");
        List<String> blocks = extractCompleteChronosBlocks(Files.readString(doc));
        assertEquals(2, blocks.size(),
                "docs/examples/getting-started.md must contain exactly 2 complete chronos blocks "
                        + "(domain + journeys); found " + blocks.size());

        List<SourceUnit> units = List.of(
                new SourceUnit("domain.chronos",   blocks.get(0)),
                new SourceUnit("journeys.chronos", blocks.get(1))
        );
        var result = new ChronosCompiler().compileAll(units);
        assertTrue(result.finalized(),
                "getting-started.md domain+journeys pair must compile without errors; got: "
                        + result.diagnostics());
    }

    // ── docs/quick-reference.md ───────────────────────────────────────────────

    /**
     * The quick-reference Multi-File Compilation section contains a minimal two-file
     * example (shop.domain + shop.journeys).  Both files must compile together without
     * errors — this is already parse-tested by {@code QuickReferenceDocTest} in the
     * parser module, but here we go further and semantically validate.
     */
    @Test
    void quickReferenceMd_twoFileExample_compilesWithoutErrors() throws IOException {
        Path doc = docsDir.resolve("quick-reference.md");
        List<String> blocks = extractCompleteChronosBlocks(Files.readString(doc));
        // The only complete namespace blocks in quick-reference.md are the two-file example
        // at the end of the "Multi-File Compilation" section.
        assertFalse(blocks.isEmpty(),
                "docs/quick-reference.md must contain at least one complete chronos block");

        // If there are multiple complete blocks, compile them all together as a project.
        if (blocks.size() == 1) {
            String name = namespaceLine(blocks.get(0));
            var result = new ChronosCompiler().compile(blocks.get(0), "docs/quick-reference.md");
            assertTrue(result.finalized(),
                    "docs/quick-reference.md block [" + name + "] must compile without errors; got: "
                            + result.diagnostics());
        } else {
            // Compile as a multi-file project
            List<SourceUnit> units = new ArrayList<>();
            for (int i = 0; i < blocks.size(); i++) {
                units.add(new SourceUnit("quick-reference-block-" + i + ".chronos", blocks.get(i)));
            }
            var result = new ChronosCompiler().compileAll(units);
            assertTrue(result.finalized(),
                    "docs/quick-reference.md complete blocks must compile without errors; got: "
                            + result.diagnostics());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Extracts all {@code ```chronos} fenced blocks that:
     * <ul>
     *   <li>contain a {@code namespace} declaration (complete files, not fragments)</li>
     *   <li>do <em>not</em> contain {@code ...} (placeholder used in illustrative fragments)</li>
     * </ul>
     */
    private static List<String> extractCompleteChronosBlocks(String markdown) {
        List<String> result = new ArrayList<>();
        String[] lines = markdown.split("\n", -1);
        boolean inBlock = false;
        StringBuilder current = new StringBuilder();

        for (String line : lines) {
            if (!inBlock && line.strip().equals("```chronos")) {
                inBlock = true;
                current = new StringBuilder();
            } else if (inBlock && line.strip().equals("```")) {
                inBlock = false;
                String block = current.toString();
                if (block.contains("namespace ") && !block.contains("...")) {
                    result.add(block);
                }
            } else if (inBlock) {
                current.append(line).append("\n");
            }
        }

        return result;
    }

    /** Returns the {@code namespace} line from a block, for use in assertion messages. */
    private static String namespaceLine(String block) {
        return block.lines()
                .filter(l -> l.startsWith("namespace "))
                .findFirst()
                .orElse("unknown-namespace")
                .trim();
    }
}
