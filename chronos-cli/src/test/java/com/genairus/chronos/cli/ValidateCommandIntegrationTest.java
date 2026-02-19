package com.genairus.chronos.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static com.genairus.chronos.cli.CliTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@code chronos validate}.
 *
 * <p>Uses {@link CliTestSupport#run} to invoke the full PicoCLI pipeline
 * with captured stdout/stderr.
 */
class ValidateCommandIntegrationTest {

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
}
