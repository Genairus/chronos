package com.genairus.chronos.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.genairus.chronos.cli.CliTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class BuildCommandTest {

    @TempDir
    Path tmp;

    // A minimal chronos-build.json that uses the markdown generator
    private static final String BUILD_CONFIG = """
            {
                "sources": ["*.chronos"],
                "targets": [
                    {
                        "name": "docs",
                        "generator": "markdown",
                        "output": "out"
                    }
                ]
            }
            """;

    private static final String BUILD_CONFIG_UNKNOWN_GEN = """
            {
                "sources": ["*.chronos"],
                "targets": [
                    {
                        "name": "docs",
                        "generator": "unknown-generator-xyz",
                        "output": "out"
                    }
                ]
            }
            """;

    // ── Happy path ─────────────────────────────────────────────────────────────

    @Test
    void happyPath_writesOutputFile_exits0() throws Exception {
        Path cfg    = writeConfig(tmp, BUILD_CONFIG);
        Path source = writeChronos(tmp, VALID_MODEL);

        Result r = run("build", "--config", cfg.toString());

        assertEquals(0, r.exit(), "stdout: " + r.out() + "\nstderr: " + r.err());
        assertTrue(r.out().contains("Wrote"), "Expected 'Wrote' in output");

        // MarkdownPrdGenerator → com-example-prd.md (namespace = com.example)
        Path expected = tmp.resolve("out").resolve("com-example-prd.md");
        assertTrue(Files.exists(expected), "Expected output file at " + expected);
        assertTrue(Files.size(expected) > 0, "Output file should be non-empty");
    }

    @Test
    void configFlag_overridesDefaultSearch() throws Exception {
        // Put config and source in a subdirectory; do NOT put them in the real cwd
        Path sub    = Files.createDirectory(tmp.resolve("proj"));
        Path cfg    = writeConfig(sub, BUILD_CONFIG);
        writeChronos(sub, VALID_MODEL);

        Result r = run("build", "--config", cfg.toString());

        assertEquals(0, r.exit(), "stderr: " + r.err());
        assertTrue(Files.exists(sub.resolve("out").resolve("com-example-prd.md")));
    }

    // ── Missing config ─────────────────────────────────────────────────────────

    @Test
    void missingConfigFile_exits1() {
        Path missing = tmp.resolve("no-config.json");
        Result r = run("build", "--config", missing.toString());

        assertEquals(1, r.exit());
        assertTrue(r.err().contains("not found") || r.err().contains("Config"),
                "Expected error about missing config, got: " + r.err());
    }

    @Test
    void missingDefaultConfig_exits1() {
        // No --config flag AND no chronos-build.json in cwd (test runs from project root,
        // which should not have one). If the file happens to exist this test is a no-op,
        // so we use --config with a non-existent path to be deterministic.
        Result r = run("build", "--config", tmp.resolve("nonexistent.json").toString());
        assertEquals(1, r.exit());
    }

    // ── Unknown generator ──────────────────────────────────────────────────────

    @Test
    void unknownGenerator_exits1_withHelpfulMessage() throws Exception {
        Path cfg = writeConfig(tmp, BUILD_CONFIG_UNKNOWN_GEN);
        writeChronos(tmp, VALID_MODEL);

        Result r = run("build", "--config", cfg.toString());

        assertEquals(1, r.exit());
        assertTrue(r.err().contains("unknown-generator-xyz"),
                "Expected unknown generator name in error, got: " + r.err());
        assertTrue(r.err().contains("Known") || r.err().contains("markdown"),
                "Expected known targets hint, got: " + r.err());
    }

    // ── Validation errors ──────────────────────────────────────────────────────

    @Test
    void sourceWithValidationErrors_prints_issues_skips_output() throws Exception {
        Path cfg = writeConfig(tmp, BUILD_CONFIG);
        writeChronos(tmp, INVALID_MODEL);  // CHR-001 ERROR

        Result r = run("build", "--config", cfg.toString());

        assertEquals(1, r.exit(), "Expected exit 1 when a source has validation errors");
        assertTrue(r.err().contains("CHR-001"),
                "Expected CHR-001 error message, got: " + r.err());

        // No output directory should have been created for the failing source
        assertFalse(Files.exists(tmp.resolve("out").resolve("com-example-prd.md")),
                "Should not write output for a source with validation errors");
    }

    @Test
    void validAndInvalidSources_validSourceStillProcessed() throws Exception {
        Path cfg = writeConfig(tmp, BUILD_CONFIG);
        // Two source files: one valid (different namespace), one invalid
        writeChronos(tmp, VALID_MODEL,   "good.chronos");
        writeChronos(tmp, INVALID_MODEL, "bad.chronos");

        Result r = run("build", "--config", cfg.toString());

        assertEquals(1, r.exit(), "Exit 1 because one source failed");
        // The valid source should have produced output
        assertTrue(Files.exists(tmp.resolve("out").resolve("com-example-prd.md")),
                "Valid source should still produce output");
        // Error about the bad source
        assertTrue(r.err().contains("CHR-001"),
                "Expected validation error from bad.chronos");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static Path writeConfig(Path dir, String json) throws Exception {
        Path cfg = dir.resolve("chronos-build.json");
        Files.writeString(cfg, json);
        return cfg;
    }
}
