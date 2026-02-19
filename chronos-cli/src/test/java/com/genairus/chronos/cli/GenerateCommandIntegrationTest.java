package com.genairus.chronos.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.genairus.chronos.cli.CliTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@code chronos generate}.
 */
class GenerateCommandIntegrationTest {

    @Test
    void generate_writesFileToOutputDir(@TempDir Path dir) throws Exception {
        Path input  = writeChronos(dir, VALID_MODEL);
        Path outDir = dir.resolve("out");
        var result = run("generate", "--output", outDir.toString(), input.toString());
        assertEquals(0, result.exit(), "Expected exit 0. stderr: " + result.err());
        // MarkdownPrdGenerator writes <namespace-with-hyphens>-prd.md
        Path expected = outDir.resolve("com-example-prd.md");
        assertTrue(Files.exists(expected),
                "Expected output file " + expected + " to exist");
    }

    @Test
    void unknownTarget_exits1_beforeParsing(@TempDir Path dir) throws Exception {
        // File doesn't even need to exist — unknown target should fail first
        Path input = dir.resolve("ignored.chronos");
        var result = run("generate", "--target", "unknown-format", input.toString());
        assertEquals(1, result.exit());
        assertTrue(result.err().contains("Unknown target"),
                "Expected 'Unknown target' in stderr but got: " + result.err());
    }

    @Test
    void errorModel_exits1_noOutputWritten(@TempDir Path dir) throws Exception {
        Path input  = writeChronos(dir, INVALID_MODEL);
        Path outDir = dir.resolve("out");
        var result = run("generate", "--output", outDir.toString(), input.toString());
        assertEquals(1, result.exit());
        // Output directory must not have been created (or be empty)
        assertFalse(Files.exists(outDir) && Files.list(outDir).findAny().isPresent(),
                "Output dir should be empty when validation fails");
    }

    @Test
    void nonexistentInput_exits1(@TempDir Path dir) {
        var result = run("generate", dir.resolve("no-such.chronos").toString());
        assertEquals(1, result.exit());
        assertTrue(result.err().toLowerCase().contains("not found"),
                "Expected 'not found' in stderr but got: " + result.err());
    }
}
