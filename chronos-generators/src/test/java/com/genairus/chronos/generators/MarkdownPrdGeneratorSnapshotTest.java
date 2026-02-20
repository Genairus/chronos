package com.genairus.chronos.generators;

import com.genairus.chronos.compiler.ChronosCompiler;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Snapshot test for {@link MarkdownPrdGenerator}.
 *
 * <p>Parses {@code examples/integration/checkout.chronos} from disk, generates
 * Markdown output, and compares it character-for-character against the committed
 * golden file {@code examples/integration/checkout-prd.golden.md}.
 *
 * <p>If the golden file does not yet exist it is written on the first run.
 * On subsequent runs the output must match exactly; the failure message
 * includes the first line that differs.
 */
class MarkdownPrdGeneratorSnapshotTest {

    private static final Path FIXTURE =
            Path.of("../examples/integration/checkout.chronos").toAbsolutePath();

    private static final Path GOLDEN =
            Path.of("../examples/integration/checkout-prd.golden.md").toAbsolutePath();

    @Test
    void generatedMarkdownMatchesGoldenFile() throws Exception {
        assertTrue(Files.exists(FIXTURE),
                "Fixture not found: " + FIXTURE);

        var src = Files.readString(FIXTURE);
        var result = new ChronosCompiler().compile(src, FIXTURE.toString());
        assertNotNull(result.modelOrNull(),
                "Fixture compiled with errors: " + result.diagnostics());
        var output = new MarkdownPrdGenerator().generate(result.modelOrNull());
        String actual = output.content();

        if (!Files.exists(GOLDEN)) {
            Files.writeString(GOLDEN, actual);
            fail("Golden file did not exist — written to " + GOLDEN
                    + ". Commit it and re-run the test.");
        }

        String expected = Files.readString(GOLDEN);

        if (!expected.equals(actual)) {
            // Build a first-differing-line hint for easier debugging
            String[] expectedLines = expected.split("\n", -1);
            String[] actualLines   = actual.split("\n", -1);
            int minLen = Math.min(expectedLines.length, actualLines.length);
            int firstDiff = minLen; // default: length difference
            for (int i = 0; i < minLen; i++) {
                if (!expectedLines[i].equals(actualLines[i])) {
                    firstDiff = i;
                    break;
                }
            }
            String hint = firstDiff < minLen
                    ? String.format("First difference at line %d:%n  expected: %s%n  actual:   %s",
                            firstDiff + 1, expectedLines[firstDiff], actualLines[firstDiff])
                    : String.format("Line count differs: expected %d, actual %d",
                            expectedLines.length, actualLines.length);
            fail("Generated Markdown does not match golden file.\n" + hint);
        }
    }
}
