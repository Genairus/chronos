package com.genairus.chronos.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.genairus.chronos.cli.CliTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@code chronos build}.
 *
 * <p>Each test writes a temporary {@code chronos-build.json} and one or more
 * {@code .chronos} sources to a {@link TempDir}, then invokes the CLI.
 *
 * <p>Key behavioral contract: all source files are compiled together as one unit
 * via {@code compileAll}. Any error in any file causes the entire build to fail
 * atomically — no output is written.
 */
class BuildCommandIntegrationTest {

    /** Minimal valid build config JSON. */
    private static String buildConfig(String sourcePattern, String generator, String output) {
        return """
                {
                  "sources": ["%s"],
                  "targets": [
                    {
                      "name": "docs",
                      "generator": "%s",
                      "output": "%s"
                    }
                  ]
                }
                """.formatted(sourcePattern, generator, output);
    }

    // ── Error / config cases ───────────────────────────────────────────────────

    @Test
    void missingConfig_exits1(@TempDir Path dir) {
        var result = run("build", "--config", dir.resolve("missing.json").toString());
        assertEquals(1, result.exit());
        assertTrue(result.err().toLowerCase().contains("not found")
                || result.err().toLowerCase().contains("config"),
                "Expected config-not-found message in stderr but got: " + result.err());
    }

    @Test
    void unknownGenerator_exits1(@TempDir Path dir) throws Exception {
        writeChronos(dir, VALID_MODEL);
        Path config = dir.resolve("chronos-build.json");
        Files.writeString(config, buildConfig("*.chronos", "nonexistent-gen", "generated"));
        var result = run("build", "--config", config.toString());
        assertEquals(1, result.exit());
        assertTrue(result.err().contains("nonexistent-gen"),
                "Expected unknown generator name in stderr but got: " + result.err());
    }

    // ── Single-file build ──────────────────────────────────────────────────────

    @Test
    void validBuild_writesFiles(@TempDir Path dir) throws Exception {
        // Use a self-contained fixture (no cross-file imports)
        Path fixture = Path.of("../examples/integration/actor-and-journey.chronos").toAbsolutePath();
        Files.copy(fixture, dir.resolve("actor-and-journey.chronos"));

        Path config = dir.resolve("chronos-build.json");
        Files.writeString(config, buildConfig("*.chronos", "prd", "generated"));

        var result = run("build", "--config", config.toString());
        assertEquals(0, result.exit(), "Expected exit 0. stderr: " + result.err());

        // MarkdownPrdGenerator produces <namespace-with-hyphens>-prd.md
        Path outFile = dir.resolve("generated/com-example-store-prd.md");
        assertTrue(Files.exists(outFile),
                "Expected output file " + outFile + " to exist");
        assertTrue(result.out().contains("Wrote"),
                "Expected 'Wrote' in stdout but got: " + result.out());
    }

    // ── Multi-file / cross-file build ──────────────────────────────────────────

    @Test
    void multiFileBuild_crossFileReference_exits0(@TempDir Path dir) throws Exception {
        // File 1: declares a shared type
        writeChronos(dir, """
                namespace shop.types

                entity Product {
                    id: String
                    name: String
                }
                """, "types.chronos");

        // File 2: imports and uses the type from file 1 via compileAll cross-file resolution
        writeChronos(dir, """
                namespace shop.orders

                use shop.types#Product

                actor Customer

                journey PlaceOrder {
                    actor: Customer
                    steps: [
                        step addToCart {
                            action: "Customer adds product to cart"
                            expectation: "Product appears in cart"
                        }
                    ]
                    outcomes: {
                        success: "Order placed successfully"
                    }
                }
                """, "orders.chronos");

        Path config = dir.resolve("chronos-build.json");
        Files.writeString(config, buildConfig("*.chronos", "markdown", "out"));

        var result = run("build", "--config", config.toString());

        assertEquals(0, result.exit(),
                "Cross-file imports must resolve when compiled together. stderr: " + result.err());
        assertTrue(Files.exists(dir.resolve("out/shop-types-prd.md")),
                "Expected shop-types-prd.md for shop.types namespace");
        assertTrue(Files.exists(dir.resolve("out/shop-orders-prd.md")),
                "Expected shop-orders-prd.md for shop.orders namespace");
    }

    @Test
    void multiFileBuild_recursiveGlob_picksUpSubdirectory(@TempDir Path dir) throws Exception {
        // Source files in a subdirectory — exercises the src/*.chronos glob pattern
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.copy(
                Path.of("../examples/integration/multi/domain.chronos").toAbsolutePath(),
                src.resolve("domain.chronos"));
        Files.copy(
                Path.of("../examples/integration/multi/journeys.chronos").toAbsolutePath(),
                src.resolve("journeys.chronos"));

        Path config = dir.resolve("chronos-build.json");
        Files.writeString(config, buildConfig("src/*.chronos", "markdown", "out"));

        var result = run("build", "--config", config.toString());

        assertEquals(0, result.exit(), "Expected exit 0. stderr: " + result.err());
        assertTrue(Files.exists(dir.resolve("out/shop-domain-prd.md")),
                "Expected shop-domain-prd.md");
        assertTrue(Files.exists(dir.resolve("out/shop-journeys-prd.md")),
                "Expected shop-journeys-prd.md");
        assertTrue(result.out().contains("Build complete"),
                "Expected 'Build complete' in stdout but got: " + result.out());
    }

    // ── Compile errors → atomic failure ───────────────────────────────────────

    @Test
    void compileErrors_failBuild_noOutputWritten(@TempDir Path dir) throws Exception {
        writeChronos(dir, VALID_MODEL, "good.chronos");
        writeChronos(dir, INVALID_MODEL, "bad.chronos");

        Path config = dir.resolve("chronos-build.json");
        Files.writeString(config, buildConfig("*.chronos", "prd", "generated"));

        var result = run("build", "--config", config.toString());

        assertEquals(1, result.exit(), "Build must fail when any source has errors");
        assertTrue(result.err().contains("CHR-"),
                "Expected a diagnostic code in stderr but got: " + result.err());
        assertFalse(Files.isDirectory(dir.resolve("generated")),
                "Output dir must not be created when build fails");
    }

    @Test
    void parseError_failsBuild_noPartialOutput(@TempDir Path dir) throws Exception {
        writeChronos(dir, VALID_MODEL, "good.chronos");
        writeChronos(dir, "this is not valid chronos !!!", "bad.chronos");

        Path config = dir.resolve("chronos-build.json");
        Files.writeString(config, buildConfig("*.chronos", "prd", "generated"));

        var result = run("build", "--config", config.toString());

        assertEquals(1, result.exit(), "Build must fail when any source fails to parse");
        assertFalse(Files.isDirectory(dir.resolve("generated")),
                "No output should be written when any source fails to parse");
    }

    // ── Diagnostic format ──────────────────────────────────────────────────────

    @Test
    void diagnostics_useCanonicalFormat(@TempDir Path dir) throws Exception {
        writeChronos(dir, INVALID_MODEL);
        Path config = dir.resolve("chronos-build.json");
        Files.writeString(config, buildConfig("*.chronos", "prd", "generated"));

        var result = run("build", "--config", config.toString());

        assertTrue(result.err().contains("[ERROR"),
                "Expected '[ERROR' in stderr but got: " + result.err());
        assertTrue(result.err().contains("CHR-"),
                "Expected 'CHR-' in stderr but got: " + result.err());
    }
}
