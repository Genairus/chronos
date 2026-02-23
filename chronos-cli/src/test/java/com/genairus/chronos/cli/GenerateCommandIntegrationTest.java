package com.genairus.chronos.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.genairus.chronos.cli.CliTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@code chronos generate}.
 *
 * <p>Covers single-file mode, directory mode (multi-file), error cases, and
 * the diagnostic output format.
 */
class GenerateCommandIntegrationTest {

    // ── Single-file mode ───────────────────────────────────────────────────────

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
        assertFalse(Files.isDirectory(outDir),
                "Output dir should not be created when validation fails");
    }

    @Test
    void nonexistentInput_exits1(@TempDir Path dir) {
        var result = run("generate", dir.resolve("no-such.chronos").toString());
        assertEquals(1, result.exit());
        assertTrue(result.err().toLowerCase().contains("not found"),
                "Expected 'not found' in stderr but got: " + result.err());
    }

    // ── Directory mode ─────────────────────────────────────────────────────────

    @Test
    void directoryInput_writesOutputForEachModel(@TempDir Path dir) throws Exception {
        writeChronos(dir, VALID_MODEL, "domain.chronos");
        writeChronos(dir, """
                namespace com.example.catalog

                entity Product {
                    id: String
                    name: String
                }
                """, "catalog.chronos");
        Path outDir = dir.resolve("out");

        var result = run("generate", "--target", "markdown", "--output", outDir.toString(), dir.toString());

        assertEquals(0, result.exit(), "Expected exit 0. stderr: " + result.err());
        assertTrue(Files.exists(outDir.resolve("com-example-prd.md")),
                "Expected com-example-prd.md for com.example namespace");
        assertTrue(Files.exists(outDir.resolve("com-example-catalog-prd.md")),
                "Expected com-example-catalog-prd.md for com.example.catalog namespace");
        assertTrue(result.out().contains("Wrote"),
                "Expected 'Wrote' in stdout but got: " + result.out());
    }

    @Test
    void directoryInput_withCompileErrors_exits1(@TempDir Path dir) throws Exception {
        writeChronos(dir, VALID_MODEL, "good.chronos");
        writeChronos(dir, INVALID_MODEL, "bad.chronos");
        Path outDir = dir.resolve("out");

        var result = run("generate", "--output", outDir.toString(), dir.toString());

        assertEquals(1, result.exit());
        assertTrue(result.err().contains("CHR-"),
                "Expected a diagnostic code in stderr but got: " + result.err());
        assertFalse(Files.isDirectory(outDir),
                "Output dir should not be created when compilation fails");
    }

    @Test
    void directoryInput_emptyDirectory_exits1(@TempDir Path dir) {
        var result = run("generate", dir.toString());
        assertEquals(1, result.exit());
        assertTrue(result.err().contains("No .chronos files"),
                "Expected 'No .chronos files' in stderr but got: " + result.err());
    }

    @Test
    void directoryInput_hiddenFilesSkipped(@TempDir Path dir) throws Exception {
        // .hidden/ignored.chronos must NOT be included
        Path hidden = dir.resolve(".hidden");
        Files.createDirectories(hidden);
        writeChronos(hidden, INVALID_MODEL, "ignored.chronos");
        // Only the valid model in the root dir
        writeChronos(dir, VALID_MODEL);
        Path outDir = dir.resolve("out");

        var result = run("generate", "--output", outDir.toString(), dir.toString());

        assertEquals(0, result.exit(),
                "Expected exit 0 (hidden file skipped). stderr: " + result.err());
        assertTrue(Files.exists(outDir.resolve("com-example-prd.md")),
                "Expected output for valid model");
    }

    // ── Diagnostic format ──────────────────────────────────────────────────────

    @Test
    void diagnostics_useCanonicalFormat(@TempDir Path dir) throws Exception {
        writeChronos(dir, INVALID_MODEL);

        var result = run("generate", dir.toString());

        // Format: "file:line:col [ERROR/WARNING CHR-XXX] message"
        assertTrue(result.err().contains("[ERROR"),
                "Expected '[ERROR' in stderr but got: " + result.err());
        assertTrue(result.err().contains("CHR-"),
                "Expected 'CHR-' diagnostic code in stderr but got: " + result.err());
    }

    // ── Bundle: --emit-bundle flag ─────────────────────────────────────────────

    @Test
    void emitBundle_writesBundleFile(@TempDir Path dir) throws Exception {
        Path input  = writeChronos(dir, VALID_MODEL);
        Path outDir = dir.resolve("out");

        var result = run("generate", "--emit-bundle", "--output", outDir.toString(),
                input.toString());

        assertEquals(0, result.exit(), "Expected exit 0. stderr: " + result.err());
        assertTrue(Files.exists(outDir.resolve("ir-bundle.json")),
                "Expected ir-bundle.json in output dir");
        assertTrue(Files.exists(outDir.resolve("com-example-prd.md")),
                "Expected prd output alongside bundle");
        assertTrue(result.out().contains("Bundle:"),
                "Expected 'Bundle:' in stdout but got: " + result.out());
    }

    // ── Bundle: --from-ir-bundle flag ──────────────────────────────────────────

    @Test
    void fromIrBundle_producesEquivalentOutput(@TempDir Path dir) throws Exception {
        Path input  = writeChronos(dir, VALID_MODEL);
        Path outDir1 = dir.resolve("out1");
        Path outDir2 = dir.resolve("out2");

        // First pass: compile sources, emit bundle alongside output
        var r1 = run("generate", "--emit-bundle", "--output", outDir1.toString(),
                input.toString());
        assertEquals(0, r1.exit(), "First pass must succeed. stderr: " + r1.err());

        Path bundle = outDir1.resolve("ir-bundle.json");
        assertTrue(Files.exists(bundle), "Bundle must exist after --emit-bundle");

        // Second pass: generate from bundle only
        var r2 = run("generate", "--from-ir-bundle", bundle.toString(),
                "--output", outDir2.toString());
        assertEquals(0, r2.exit(), "Bundle pass must succeed. stderr: " + r2.err());

        // Output content from bundle must match output from source
        Path prd1 = outDir1.resolve("com-example-prd.md");
        Path prd2 = outDir2.resolve("com-example-prd.md");
        assertTrue(Files.exists(prd2), "Expected prd output from bundle");
        assertEquals(Files.readString(prd1), Files.readString(prd2),
                "PRD content from bundle must match PRD content from source");
    }

    @Test
    void fromIrBundle_missingFile_exits1(@TempDir Path dir) {
        Path missing = dir.resolve("no-bundle.json");
        var result = run("generate", "--from-ir-bundle", missing.toString(),
                "--output", dir.resolve("out").toString());

        assertEquals(1, result.exit());
        assertTrue(result.err().toLowerCase().contains("not found")
                        || result.err().contains("Bundle"),
                "Expected error about missing bundle, got: " + result.err());
    }

    @Test
    void fromIrBundle_invalidFormat_exits1(@TempDir Path dir) throws Exception {
        Path bad = dir.resolve("bad.json");
        Files.writeString(bad, "{\"format\":\"wrong\",\"version\":\"1\",\"entries\":[]}");

        var result = run("generate", "--from-ir-bundle", bad.toString(),
                "--output", dir.resolve("out").toString());

        assertEquals(1, result.exit());
        assertTrue(result.err().contains("Invalid bundle") || result.err().contains("format"),
                "Expected invalid bundle error, got: " + result.err());
    }
}
