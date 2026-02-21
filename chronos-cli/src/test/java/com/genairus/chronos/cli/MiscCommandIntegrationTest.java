package com.genairus.chronos.cli;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.genairus.chronos.cli.CliTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@code chronos init}, {@code select}, {@code diff},
 * and {@code clean}.
 */
@Disabled("init/select/diff/clean not registered in ChronosCli.subcommands for v0.1 — re-enable when shipped")
class MiscCommandIntegrationTest {

    // ── init ──────────────────────────────────────────────────────────────────

    @Test
    void init_createsParseableFile(@TempDir Path dir) throws Exception {
        Path output = dir.resolve("starter.chronos");
        var result = run("init", output.toString());
        assertEquals(0, result.exit(), "init should exit 0. stderr: " + result.err());
        assertTrue(Files.exists(output), "init should create the file");

        // The created file must parse without error
        var text = Files.readString(output);
        var compileResult = new ChronosCompiler().compile(text, output.toString());
        assertTrue(compileResult.parsed(), "init-created file should parse cleanly");
    }

    // ── select ────────────────────────────────────────────────────────────────

    @Test
    void select_patternMatchesJourneyName(@TempDir Path dir) throws Exception {
        Path file = writeChronos(dir, VALID_MODEL);
        // VALID_MODEL contains "ExampleJourney"
        var result = run("select", "--pattern", "Journey", file.toString());
        assertEquals(0, result.exit(), "select should exit 0. stderr: " + result.err());
        assertTrue(result.out().contains("ExampleJourney"),
                "Expected 'ExampleJourney' in stdout but got: " + result.out());
        assertTrue(result.out().contains("journey"),
                "Expected shape type 'journey' in output but got: " + result.out());
    }

    // ── diff ──────────────────────────────────────────────────────────────────

    @Test
    void diff_identicalFiles_exits0(@TempDir Path dir) throws Exception {
        Path file1 = writeChronos(dir, VALID_MODEL, "a.chronos");
        Path file2 = writeChronos(dir, VALID_MODEL, "b.chronos");
        var result = run("diff", file1.toString(), file2.toString());
        assertEquals(0, result.exit(), "diff of identical files should exit 0");
        assertTrue(result.out().isBlank(), "diff of identical files should produce no output");
    }

    @Test
    void diff_modifiedCopy_exits1(@TempDir Path dir) throws Exception {
        Path file1 = writeChronos(dir, VALID_MODEL, "base.chronos");
        // HEAD adds an entity that BASE does not have
        String modified = VALID_MODEL + "\nentity NewOrder {\n    id: String\n}\n";
        Path file2 = writeChronos(dir, modified, "head.chronos");
        var result = run("diff", file1.toString(), file2.toString());
        assertEquals(1, result.exit(), "diff of different files should exit 1");
        assertTrue(result.out().contains("NewOrder"),
                "Expected added shape 'NewOrder' in diff output but got: " + result.out());
    }

    // ── clean ─────────────────────────────────────────────────────────────────

    @Test
    void clean_deletesDirectory(@TempDir Path dir) throws Exception {
        Path toDelete = dir.resolve("generated");
        Files.createDirectories(toDelete);
        Files.writeString(toDelete.resolve("file.txt"), "content");

        var result = run("clean", "--output", toDelete.toString(), "--force");
        assertEquals(0, result.exit(), "clean should exit 0. stderr: " + result.err());
        assertFalse(Files.exists(toDelete), "clean should have deleted the directory");
    }

    @Test
    void clean_absentDirectory_noopWithMessage(@TempDir Path dir) {
        Path absent = dir.resolve("nonexistent");
        var result = run("clean", "--output", absent.toString());
        assertEquals(0, result.exit(), "clean on absent dir should exit 0");
        assertTrue(result.out().contains("Nothing to clean"),
                "Expected 'Nothing to clean' in stdout but got: " + result.out());
    }
}
