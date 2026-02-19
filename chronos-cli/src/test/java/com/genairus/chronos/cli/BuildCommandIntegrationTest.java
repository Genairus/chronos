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

    @Test
    void missingConfig_exits1(@TempDir Path dir) {
        // No chronos-build.json in cwd, no --config flag → error
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

    @Test
    void validBuild_writesFiles(@TempDir Path dir) throws Exception {
        // Copy the integration fixture into the temp dir so the glob can find it
        Path fixture = Path.of("../examples/integration/checkout.chronos").toAbsolutePath();
        Path source  = dir.resolve("checkout.chronos");
        Files.copy(fixture, source);

        Path config = dir.resolve("chronos-build.json");
        Files.writeString(config, buildConfig("*.chronos", "prd", "generated"));

        var result = run("build", "--config", config.toString());
        assertEquals(0, result.exit(), "Expected exit 0. stderr: " + result.err());

        // MarkdownPrdGenerator produces <namespace-with-hyphens>-prd.md
        Path outFile = dir.resolve("generated/com-example-checkout-prd.md");
        assertTrue(Files.exists(outFile),
                "Expected output file " + outFile + " to exist");
    }

    @Test
    void sourceWithParseError_skippedButOtherSourcesProcessed(@TempDir Path dir) throws Exception {
        // valid source: will be processed
        writeChronos(dir, VALID_MODEL, "good.chronos");
        // invalid source: will be skipped
        writeChronos(dir, "not valid chronos !!!", "bad.chronos");

        Path config = dir.resolve("chronos-build.json");
        Files.writeString(config, buildConfig("*.chronos", "prd", "generated"));

        var result = run("build", "--config", config.toString());
        assertEquals(1, result.exit(), "anySourceFailed should cause exit 1");

        // The good source should still have produced its output
        Path goodOutput = dir.resolve("generated/com-example-prd.md");
        assertTrue(Files.exists(goodOutput),
                "Good source should still generate output. outDir: "
                        + dir.resolve("generated"));
    }
}
