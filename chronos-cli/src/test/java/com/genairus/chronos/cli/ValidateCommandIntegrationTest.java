package com.genairus.chronos.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.genairus.chronos.cli.CliTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@code chronos validate}.
 *
 * <p>Covers single-file mode, directory mode (multi-file with cross-file
 * references), error cases, and the verbose flag.
 */
class ValidateCommandIntegrationTest {

    // ── Single-file mode ───────────────────────────────────────────────────────

    @Test
    void cleanFile_exits0(@TempDir Path dir) throws Exception {
        Path file = writeChronos(dir, VALID_MODEL);
        var result = run("validate", file.toString());
        assertEquals(0, result.exit(), "Valid model should exit 0. stderr: " + result.err());
    }

    @Test
    void errorModel_exits1_withChr001InStderr(@TempDir Path dir) throws Exception {
        Path file = writeChronos(dir, INVALID_MODEL);
        var result = run("validate", file.toString());
        assertEquals(1, result.exit());
        assertTrue(result.err().contains("CHR-001"),
                "Expected CHR-001 in stderr but got: " + result.err());
    }

    @Test
    void verboseOnCleanFile_printsSuccessLine(@TempDir Path dir) throws Exception {
        Path file = writeChronos(dir, VALID_MODEL);
        var result = run("validate", "--verbose", file.toString());
        assertEquals(0, result.exit());
        assertTrue(result.out().contains("No issues"),
                "Expected success line in stdout but got: " + result.out());
    }

    @Test
    void missingFile_exits1(@TempDir Path dir) {
        var result = run("validate", dir.resolve("does-not-exist.chronos").toString());
        assertEquals(1, result.exit());
        assertTrue(result.err().toLowerCase().contains("not found"),
                "Expected 'not found' in stderr but got: " + result.err());
    }

    @Test
    void syntaxError_exits1(@TempDir Path dir) throws Exception {
        Path file = writeChronos(dir, "this is not valid chronos !!!");
        var result = run("validate", file.toString());
        assertEquals(1, result.exit());
        assertFalse(result.err().isBlank(), "Expected error message in stderr");
    }

    // ── Directory mode ─────────────────────────────────────────────────────────

    @Test
    void validDirectory_exits0(@TempDir Path dir) throws Exception {
        writeChronos(dir, VALID_MODEL, "model.chronos");
        writeChronos(dir, """
                namespace com.example.support

                entity Ticket {
                    id: String
                    title: String
                }
                """, "support.chronos");

        var result = run("validate", dir.toString());

        assertEquals(0, result.exit(), "Expected exit 0 for valid directory. stderr: " + result.err());
    }

    @Test
    void directoryWithCrossFileImport_exits0(@TempDir Path dir) throws Exception {
        // File 1: declares a shared entity in its own namespace
        writeChronos(dir, """
                namespace shop.domain

                entity Product {
                    id: String
                    name: String
                }
                """, "domain.chronos");

        // File 2: imports and uses the entity from file 1
        writeChronos(dir, """
                namespace shop.catalog

                use shop.domain#Product

                actor Customer

                journey BrowseCatalog {
                    actor: Customer
                    steps: [
                        step search {
                            action: "Customer searches for products"
                            expectation: "Matching results returned"
                        }
                    ]
                    outcomes: {
                        success: "Customer found items"
                    }
                }
                """, "catalog.chronos");

        var result = run("validate", dir.toString());

        assertEquals(0, result.exit(),
                "Cross-file imports should resolve when compiled together. stderr: " + result.err());
    }

    @Test
    void directoryWithErrors_exits1(@TempDir Path dir) throws Exception {
        writeChronos(dir, VALID_MODEL, "good.chronos");
        writeChronos(dir, INVALID_MODEL, "bad.chronos");

        var result = run("validate", dir.toString());

        assertEquals(1, result.exit());
        assertTrue(result.err().contains("CHR-"),
                "Expected a diagnostic code in stderr but got: " + result.err());
    }

    @Test
    void emptyDirectory_exits1(@TempDir Path dir) {
        var result = run("validate", dir.toString());
        assertEquals(1, result.exit());
        assertTrue(result.err().contains("No .chronos files"),
                "Expected 'No .chronos files' in stderr but got: " + result.err());
    }

    @Test
    void hiddenDirectoriesSkipped(@TempDir Path dir) throws Exception {
        // .git/bad.chronos must NOT be included
        Path hidden = dir.resolve(".git");
        Files.createDirectories(hidden);
        writeChronos(hidden, INVALID_MODEL, "bad.chronos");
        // Only the valid model is in the root
        writeChronos(dir, VALID_MODEL);

        var result = run("validate", dir.toString());

        assertEquals(0, result.exit(),
                "Expected exit 0 (hidden file skipped). stderr: " + result.err());
    }

    @Test
    void verbose_directory_printsFileCount(@TempDir Path dir) throws Exception {
        writeChronos(dir, VALID_MODEL, "a.chronos");
        writeChronos(dir, """
                namespace com.example.extra

                entity Note {
                    id: String
                }
                """, "b.chronos");

        var result = run("validate", "--verbose", dir.toString());

        assertEquals(0, result.exit(), "Expected exit 0. stderr: " + result.err());
        assertTrue(result.out().contains("No issues"),
                "Expected success message in stdout but got: " + result.out());
        assertTrue(result.out().contains("2 files"),
                "Expected file count in success message but got: " + result.out());
    }

    @Test
    void diagnostics_useCanonicalFormat(@TempDir Path dir) throws Exception {
        writeChronos(dir, INVALID_MODEL);

        var result = run("validate", dir.toString());

        // Format: "file:line:col [ERROR/WARNING CHR-XXX] message"
        assertTrue(result.err().contains("[ERROR"),
                "Expected '[ERROR' in stderr but got: " + result.err());
        assertTrue(result.err().contains("CHR-"),
                "Expected 'CHR-' diagnostic code in stderr but got: " + result.err());
    }
}
