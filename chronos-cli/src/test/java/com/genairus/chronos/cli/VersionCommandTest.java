package com.genairus.chronos.cli;

import org.junit.jupiter.api.Test;

import static com.genairus.chronos.cli.CliTestSupport.run;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@code chronos --version} / {@code chronos -V}.
 *
 * <p>PicoCLI's {@code mixinStandardHelpOptions = true} wires {@code -V, --version}
 * automatically. {@code -v} (lowercase) is already taken by {@code validate --verbose}
 * so the short form is {@code -V} (uppercase).
 */
class VersionCommandTest {

    @Test
    void versionLongFlag_exits0() {
        var result = run("--version");
        assertEquals(0, result.exit(),
                "--version must exit 0; stderr: " + result.err());
    }

    @Test
    void versionShortFlag_exits0() {
        var result = run("-V");
        assertEquals(0, result.exit(),
                "-V must exit 0; stderr: " + result.err());
    }

    @Test
    void versionOutput_containsToolName() {
        var result = run("--version");
        assertTrue(result.out().contains("chronos"),
                "--version output must contain 'chronos'; got: " + result.out());
    }

    @Test
    void versionOutput_containsVersionString() {
        var result = run("--version");
        // Version is a dotted numeric string, e.g. "0.1.0"
        assertTrue(result.out().matches("(?s).*\\d+\\.\\d+\\.\\d+.*"),
                "--version output must contain a semantic version (x.y.z); got: " + result.out());
    }

    @Test
    void versionOutput_matchesConstant() {
        var result = run("--version");
        assertTrue(result.out().contains(ChronosVersion.VERSION),
                "--version output must contain the version constant '"
                        + ChronosVersion.VERSION + "'; got: " + result.out());
    }

    @Test
    void shortAndLongFlags_produceIdenticalOutput() {
        var longResult  = run("--version");
        var shortResult = run("-V");
        assertEquals(longResult.out().strip(), shortResult.out().strip(),
                "--version and -V must produce identical output");
    }
}
