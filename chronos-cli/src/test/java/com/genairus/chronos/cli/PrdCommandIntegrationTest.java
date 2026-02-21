package com.genairus.chronos.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.genairus.chronos.cli.CliTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@code chronos prd}.
 *
 * <p>Covers directory mode (multi-file), single-file mode, error cases, and the
 * diagnostic / success message output format.
 */
class PrdCommandIntegrationTest {

    // ── Directory mode ─────────────────────────────────────────────────────────

    @Test
    void validDirectory_writesCombinedPrd(@TempDir Path dir) throws Exception {
        writeChronos(dir, VALID_MODEL, "model.chronos");
        writeChronos(dir, """
                namespace com.example.other

                entity Product {
                    id: String
                    name: String
                }
                """, "other.chronos");

        Path outDir = dir.resolve("out");
        var result = run("prd", "--out", outDir.toString(), dir.toString());

        assertEquals(0, result.exit(), "Expected exit 0. stderr: " + result.err());
        assertTrue(Files.exists(outDir.resolve("chronos-prd.md")),
                "Expected chronos-prd.md in output dir");
        // Success message contains the filename and the file count
        assertTrue(result.out().contains("Generated chronos-prd.md"),
                "Expected 'Generated chronos-prd.md' in stdout but got: " + result.out());
        assertTrue(result.out().contains("2 files"),
                "Expected '2 files' in stdout but got: " + result.out());
    }

    @Test
    void customName_producesCorrectFilename(@TempDir Path dir) throws Exception {
        writeChronos(dir, VALID_MODEL);
        Path outDir = dir.resolve("out");

        var result = run("prd", "--out", outDir.toString(), "--name", "my-prd", dir.toString());

        assertEquals(0, result.exit(), "Expected exit 0. stderr: " + result.err());
        assertTrue(Files.exists(outDir.resolve("my-prd.md")),
                "Expected my-prd.md in output dir");
        assertTrue(result.out().contains("Generated my-prd.md"), result.out());
    }

    @Test
    void hiddenDirectories_areSkipped(@TempDir Path dir) throws Exception {
        // .git/ignored.chronos must NOT be included
        Path hidden = dir.resolve(".git");
        Files.createDirectories(hidden);
        writeChronos(hidden, INVALID_MODEL, "ignored.chronos");
        // Only valid model in the root dir
        writeChronos(dir, VALID_MODEL);

        var result = run("prd", dir.toString());

        assertEquals(0, result.exit(), "Expected exit 0 (hidden file ignored). stderr: " + result.err());
    }

    // ── Single-file mode ───────────────────────────────────────────────────────

    @Test
    void singleFile_writesPrd(@TempDir Path dir) throws Exception {
        Path file = writeChronos(dir, VALID_MODEL);
        Path outDir = dir.resolve("out");

        var result = run("prd", "--out", outDir.toString(), file.toString());

        assertEquals(0, result.exit(), "Expected exit 0. stderr: " + result.err());
        assertTrue(Files.exists(outDir.resolve("chronos-prd.md")),
                "Expected chronos-prd.md in output dir");
        assertTrue(result.out().contains("1 file."),
                "Expected '1 file.' in stdout but got: " + result.out());
    }

    @Test
    void singleFile_invalidModel_exitsNonZero(@TempDir Path dir) throws Exception {
        Path file = writeChronos(dir, INVALID_MODEL);

        var result = run("prd", file.toString());

        assertEquals(1, result.exit());
        assertFalse(result.err().isBlank(), "Expected error output");
    }

    // ── Error cases ────────────────────────────────────────────────────────────

    @Test
    void invalidChronosFile_exitsNonZero(@TempDir Path dir) throws Exception {
        writeChronos(dir, INVALID_MODEL);

        var result = run("prd", dir.toString());

        assertEquals(1, result.exit());
        assertFalse(result.err().isBlank(), "Expected error output but got none");
    }

    @Test
    void emptyDirectory_exitsNonZero(@TempDir Path dir) {
        var result = run("prd", dir.toString());
        assertEquals(1, result.exit());
        assertTrue(result.err().contains("No .chronos files"),
                "Expected 'No .chronos files' in stderr but got: " + result.err());
    }

    @Test
    void nonexistentInput_exitsNonZero(@TempDir Path dir) {
        var result = run("prd", dir.resolve("no-such").toString());
        assertEquals(1, result.exit());
        assertTrue(result.err().contains("not found") || result.err().contains("Input not found"),
                "Expected 'not found' in stderr but got: " + result.err());
    }

    // ── Diagnostic format ──────────────────────────────────────────────────────

    @Test
    void diagnostics_useCanonicalFormat(@TempDir Path dir) throws Exception {
        writeChronos(dir, INVALID_MODEL);

        var result = run("prd", dir.toString());

        // Format: "file:line:col [ERROR/WARNING CHR-XXX] message"
        // At least one error diagnostic should be in this format
        assertTrue(result.err().contains("[ERROR"),
                "Expected '[ERROR' in stderr but got: " + result.err());
        assertTrue(result.err().contains("CHR-"),
                "Expected 'CHR-' diagnostic code in stderr but got: " + result.err());
    }
}
