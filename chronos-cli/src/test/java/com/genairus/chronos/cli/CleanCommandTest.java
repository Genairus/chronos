package com.genairus.chronos.cli;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.genairus.chronos.cli.CliTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Disabled("clean not registered in ChronosCli.subcommands for v0.1 — re-enable when shipped")
class CleanCommandTest {

    @TempDir
    Path tmp;

    @Test
    void clean_existingDirectory_deletesItAndPrintsEntries() throws Exception {
        Path genDir = tmp.resolve("generated");
        Files.createDirectory(genDir);
        Path file1 = Files.writeString(genDir.resolve("output.md"), "content");
        Path subDir = Files.createDirectory(genDir.resolve("sub"));
        Files.writeString(subDir.resolve("nested.txt"), "nested");

        // System.console() == null in tests, so --force is not strictly needed,
        // but pass it to be explicit
        Result r = run("clean", "--force", "--output", genDir.toString());

        assertEquals(0, r.exit(), "stderr: " + r.err());
        assertFalse(Files.exists(genDir), "Generated dir should be deleted");
        // Should mention at least one deleted entry
        assertTrue(r.out().contains("Deleted") || r.out().contains("delete"),
                "Expected deletion message, got: " + r.out());
    }

    @Test
    void clean_nonExistentDirectory_noOps_exits0() {
        Path missing = tmp.resolve("does-not-exist");
        Result r = run("clean", "--output", missing.toString());

        assertEquals(0, r.exit());
        assertTrue(r.out().contains("Nothing") || r.out().contains("not exist"),
                "Expected no-op message, got: " + r.out());
    }

    @Test
    void clean_emptyDirectory_deletesDirectory_exits0() throws Exception {
        Path genDir = tmp.resolve("empty-gen");
        Files.createDirectory(genDir);

        Result r = run("clean", "--force", "--output", genDir.toString());

        assertEquals(0, r.exit(), "stderr: " + r.err());
        assertFalse(Files.exists(genDir), "Empty generated dir should be deleted");
    }

    @Test
    void clean_nonInteractiveContext_skipsPrompt() throws Exception {
        // System.console() is null in test JVM → confirmation is auto-skipped
        Path genDir = tmp.resolve("generated");
        Files.createDirectory(genDir);
        Files.writeString(genDir.resolve("file.md"), "data");

        // No --force flag: relies on non-interactive detection
        Result r = run("clean", "--output", genDir.toString());

        assertEquals(0, r.exit(), "Should skip prompt in non-interactive context");
        assertFalse(Files.exists(genDir));
    }
}
