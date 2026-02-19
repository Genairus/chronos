package com.genairus.chronos.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.genairus.chronos.cli.CliTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class GenerateCommandTest {

    @TempDir
    Path tmp;

    @Test
    void happyPath_writesOutputFile_exits0() throws Exception {
        Path input  = writeChronos(tmp, VALID_MODEL);
        Path outDir = tmp.resolve("out");

        Result r = run("generate", input.toString(), "-t", "markdown", "-o", outDir.toString());

        assertEquals(0, r.exit(), "stdout: " + r.out() + "\nstderr: " + r.err());
        // MarkdownPrdGenerator writes <namespace>-prd.md
        Path expected = outDir.resolve("com-example-prd.md");
        assertTrue(Files.exists(expected), "Expected output file " + expected);
        assertTrue(r.out().contains("Wrote"), "Expected 'Wrote' in stdout");
    }

    @Test
    void validationErrors_exits1_issuesPrinted() throws Exception {
        Path input  = writeChronos(tmp, INVALID_MODEL);
        Path outDir = tmp.resolve("out");

        Result r = run("generate", input.toString(), "-o", outDir.toString());

        assertEquals(1, r.exit());
        assertTrue(r.err().contains("CHR-001"),
                "Expected CHR-001 on stderr, got: " + r.err());
        assertFalse(Files.exists(outDir), "Output dir should not be created on validation failure");
    }

    @Test
    void missingFile_exits1() {
        Result r = run("generate", tmp.resolve("missing.chronos").toString());
        assertEquals(1, r.exit());
        assertTrue(r.err().contains("not found") || r.err().contains("Error"),
                "Expected error message, got: " + r.err());
    }

    @Test
    void unknownTarget_exits1_helpfulMessage() throws Exception {
        Path input = writeChronos(tmp, VALID_MODEL);
        Result r = run("generate", input.toString(), "-t", "foo-unknown");
        assertEquals(1, r.exit());
        assertTrue(r.err().contains("foo-unknown"),
                "Expected unknown target name in error, got: " + r.err());
        assertTrue(r.err().contains("markdown") || r.err().contains("Known"),
                "Expected known targets hint, got: " + r.err());
    }

    @Test
    void generate_writesFileContent_isNonEmpty() throws Exception {
        Path input  = writeChronos(tmp, VALID_MODEL);
        Path outDir = tmp.resolve("out");

        run("generate", input.toString(), "-t", "prd", "-o", outDir.toString());

        try (var files = Files.list(outDir)) {
            var paths = files.toList();
            assertFalse(paths.isEmpty(), "Expected at least one output file");
            for (Path p : paths) {
                assertTrue(Files.size(p) > 0, "Output file should not be empty: " + p);
            }
        } catch (IOException e) {
            fail("Could not list output directory: " + e.getMessage());
        }
    }
}
