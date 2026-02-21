package com.genairus.chronos.cli;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.genairus.chronos.cli.CliTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Disabled("init not registered in ChronosCli.subcommands for v0.1 — re-enable when shipped")
class InitCommandTest {

    @TempDir
    Path tmp;

    @Test
    void init_createsFile_exits0() {
        Path dest = tmp.resolve("model.chronos");
        Result r = run("init", dest.toString());

        assertEquals(0, r.exit(), "stderr: " + r.err());
        assertTrue(Files.exists(dest), "Expected file to be created at " + dest);
        assertTrue(r.out().contains("Created"), "Expected 'Created' in stdout");
    }

    @Test
    void init_fileContainsNamespaceAndJourney() throws Exception {
        Path dest = tmp.resolve("model.chronos");
        run("init", dest.toString());

        String content = Files.readString(dest);
        assertTrue(content.contains("namespace"), "Expected namespace declaration");
        assertTrue(content.contains("journey"), "Expected journey definition");
        assertTrue(content.contains("actor"), "Expected actor declaration");
    }

    @Test
    void init_existingFile_withoutForce_exits1() throws Exception {
        Path dest = tmp.resolve("model.chronos");
        Files.writeString(dest, "existing content");

        Result r = run("init", dest.toString());

        assertEquals(1, r.exit());
        assertTrue(r.err().contains("already exists") || r.err().contains("Error"),
                "Expected 'already exists' message, got: " + r.err());
        // Original content must be untouched
        assertEquals("existing content", Files.readString(dest));
    }

    @Test
    void init_existingFile_withForce_overwrites_exits0() throws Exception {
        Path dest = tmp.resolve("model.chronos");
        Files.writeString(dest, "old content");

        Result r = run("init", "--force", dest.toString());

        assertEquals(0, r.exit(), "stderr: " + r.err());
        String content = Files.readString(dest);
        assertFalse(content.equals("old content"), "Expected file to be overwritten");
        assertTrue(content.contains("namespace"), "Expected namespace in new content");
    }
}
