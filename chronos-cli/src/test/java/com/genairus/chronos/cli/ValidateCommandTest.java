package com.genairus.chronos.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static com.genairus.chronos.cli.CliTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ValidateCommandTest {

    @TempDir
    Path tmp;

    @Test
    void happyPath_cleanModel_exits0() throws Exception {
        Path file = writeChronos(tmp, VALID_MODEL);
        Result r = run("validate", file.toString());
        assertEquals(0, r.exit(), "Expected exit 0 for a valid model");
    }

    @Test
    void verbose_cleanModel_printsSuccess() throws Exception {
        Path file = writeChronos(tmp, VALID_MODEL);
        Result r = run("validate", "--verbose", file.toString());
        assertEquals(0, r.exit());
        assertTrue(r.out().contains("No issues found"),
                "Expected success message on stdout, got: " + r.out());
    }

    @Test
    void validationErrors_exits1_issuesPrinted() throws Exception {
        Path file = writeChronos(tmp, INVALID_MODEL);
        Result r = run("validate", file.toString());
        assertEquals(1, r.exit(), "Expected exit 1 when model has validation errors");
        assertTrue(r.err().contains("CHR-001"),
                "Expected CHR-001 error on stderr, got: " + r.err());
    }

    @Test
    void missingFile_exits1_errorPrinted() {
        Result r = run("validate", tmp.resolve("no-such-file.chronos").toString());
        assertEquals(1, r.exit());
        assertTrue(r.err().contains("not found") || r.err().contains("Error"),
                "Expected file-not-found message on stderr, got: " + r.err());
    }
}
