package com.genairus.chronos.cli;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.genairus.chronos.cli.CliTestSupport.run;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Asserts that commands not registered for v0.1 ({@code init}, {@code select},
 * {@code diff}, {@code clean}) are not silently accepted — PicoCLI returns a
 * non-zero exit code and writes an "Unmatched argument" message to stderr.
 */
class UnshippedCommandsTest {

    @ParameterizedTest
    @ValueSource(strings = {"init", "select", "diff", "clean"})
    void unshippedCommand_returnsNonZero(String command) {
        var result = run(command);
        assertNotEquals(0, result.exit(),
                "'" + command + "' must not exit 0 (not registered for v0.1)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"init", "select", "diff", "clean"})
    void unshippedCommand_writesErrorToStderr(String command) {
        var result = run(command);
        assertFalse(result.err().isBlank(),
                "'" + command + "' must write to stderr; got empty stderr");
    }
}
