package com.genairus.chronos.cli;

import org.junit.jupiter.api.Test;

import static com.genairus.chronos.cli.CliTestSupport.run;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the v0.1 CLI surface area:
 * <ul>
 *   <li>Shipped: {@code prd}, {@code build}, {@code validate}, {@code generate}</li>
 *   <li>Not shipped: {@code init}, {@code select}, {@code diff}, {@code clean}</li>
 * </ul>
 */
class SurfaceAreaTest {

    @Test
    void help_listsAllShippedCommands() {
        var result = run("--help");
        assertEquals(0, result.exit(), "--help must exit 0; stderr: " + result.err());
        String out = result.out();
        assertTrue(out.contains("prd"),      "--help must list 'prd'");
        assertTrue(out.contains("build"),    "--help must list 'build'");
        assertTrue(out.contains("validate"), "--help must list 'validate'");
        assertTrue(out.contains("generate"), "--help must list 'generate'");
    }

    @Test
    void help_doesNotListUnshippedCommands() {
        var result = run("--help");
        String out = result.out();
        assertFalse(out.contains("init"),   "--help must NOT list 'init'");
        assertFalse(out.contains("select"), "--help must NOT list 'select'");
        assertFalse(out.contains("diff"),   "--help must NOT list 'diff'");
        assertFalse(out.contains("clean"),  "--help must NOT list 'clean'");
    }
}
