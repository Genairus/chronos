package com.genairus.chronos.compiler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Validates that every example in {@code examples/} compiles without errors.
 *
 * <p>Warnings are allowed; errors are not.  This test acts as a living proof
 * that the examples/ directory is always in sync with the compiler.
 *
 * <p>Requires the {@code chronos.rootDir} system property (set by Gradle's
 * {@code tasks.test} block in {@code chronos-compiler/build.gradle.kts}).
 */
class ExamplesValidationTest {

    private static Path examplesDir;

    @BeforeAll
    static void resolveExamplesDir() {
        String rootDir = System.getProperty("chronos.rootDir");
        assumeTrue(rootDir != null,
                "chronos.rootDir system property not set — skipping examples validation");
        examplesDir = Path.of(rootDir, "examples");
        assumeTrue(Files.isDirectory(examplesDir),
                "examples/ directory not found at " + examplesDir + " — skipping");
    }

    // ── Getting Started ───────────────────────────────────────────────────────

    @Test
    void gettingStarted_compilesWithoutErrors() throws IOException {
        assertNoErrors(compileDirectory("getting-started"), "getting-started/");
    }

    // ── Backlog Demo ──────────────────────────────────────────────────────────

    @Test
    void backlogDemo_compilesWithoutErrors() throws IOException {
        assertNoErrors(compileDirectory("backlog-demo"), "backlog-demo/");
    }

    // ── E-Commerce ────────────────────────────────────────────────────────────

    @Test
    void ecommerce_compilesWithoutErrors() throws IOException {
        assertNoErrors(compileDirectory("ecommerce"), "ecommerce/");
    }

    // ── Integration fixtures ──────────────────────────────────────────────────

    @Test
    void integration_singleFiles_compileWithoutErrors() throws IOException {
        for (String name : List.of(
                "minimal-entity.chronos",
                "actor-and-journey.chronos",
                "relationship-basic.chronos",
                "checkout.chronos")) {
            Path file = examplesDir.resolve("integration").resolve(name);
            var result = new ChronosCompiler().compile(
                    Files.readString(file), file.toString());
            assertTrue(result.success(),
                    name + " should compile without errors; errors: " + result.errors());
        }
    }

    @Test
    void integration_multiFile_compilesWithoutErrors() throws IOException {
        assertNoErrors(compileDirectory("integration/multi"), "integration/multi/");
    }

    // ── Single-feature showcase files ────────────────────────────────────────

    @Test
    void topLevel_showcaseFiles_compileWithoutErrors() throws IOException {
        for (String name : List.of(
                "deny-example.chronos",
                "error-example.chronos",
                "invariants-example.chronos",
                "my-journeys.chronos",
                "statemachine-example.chronos")) {
            Path file = examplesDir.resolve(name);
            var result = new ChronosCompiler().compile(
                    Files.readString(file), file.toString());
            assertTrue(result.success(),
                    name + " should compile without errors; errors: " + result.errors());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Reads all {@code .chronos} files under {@code subdir} (recursively),
     * compiles them together as a multi-file project, and returns the result.
     */
    private CompileAllResult compileDirectory(String subdir) throws IOException {
        Path dir = examplesDir.resolve(subdir);
        List<SourceUnit> units;
        try (Stream<Path> walk = Files.walk(dir)) {
            units = walk
                    .filter(p -> p.toString().endsWith(".chronos"))
                    .sorted()
                    .map(p -> {
                        try {
                            return new SourceUnit(p.toString(), Files.readString(p));
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to read " + p, e);
                        }
                    })
                    .toList();
        }
        assertFalse(units.isEmpty(), subdir + " contains no .chronos files");
        return new ChronosCompiler().compileAll(units);
    }

    private static void assertNoErrors(CompileAllResult result, String label) {
        assertTrue(result.success(),
                label + " should compile without errors; errors: " + result.errors());
    }
}
