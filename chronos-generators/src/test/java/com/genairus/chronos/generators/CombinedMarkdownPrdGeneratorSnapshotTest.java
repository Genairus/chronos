package com.genairus.chronos.generators;

import com.genairus.chronos.compiler.ChronosCompiler;
import com.genairus.chronos.compiler.SourceUnit;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Snapshot test for {@link MarkdownPrdGenerator#generateCombined}.
 *
 * <p>Parses the two fixture files in {@code examples/integration/multi/}, compiles them
 * together via {@code compileAll}, generates the combined PRD, and compares it
 * character-for-character against the committed golden file
 * {@code examples/integration/multi-file-prd.golden.md}.
 *
 * <p>If the golden file does not yet exist it is written on the first run.
 * On subsequent runs the output must match exactly; the failure message
 * includes the first line that differs.
 */
class CombinedMarkdownPrdGeneratorSnapshotTest {

    private static final Path DOMAIN_FIXTURE =
            Path.of("../examples/integration/multi/domain.chronos").toAbsolutePath();
    private static final Path JOURNEYS_FIXTURE =
            Path.of("../examples/integration/multi/journeys.chronos").toAbsolutePath();
    private static final Path GOLDEN =
            Path.of("../examples/integration/multi-file-prd.golden.md").toAbsolutePath();

    @Test
    void combinedPrdMatchesGoldenFile() throws Exception {
        assertTrue(Files.exists(DOMAIN_FIXTURE),   "Fixture not found: " + DOMAIN_FIXTURE);
        assertTrue(Files.exists(JOURNEYS_FIXTURE), "Fixture not found: " + JOURNEYS_FIXTURE);

        var sources = List.of(
                new SourceUnit(DOMAIN_FIXTURE.toString(),   Files.readString(DOMAIN_FIXTURE)),
                new SourceUnit(JOURNEYS_FIXTURE.toString(), Files.readString(JOURNEYS_FIXTURE)));

        var result = new ChronosCompiler().compileAll(sources);
        assertTrue(result.parsed(),    "Fixtures must parse: "    + result.diagnostics());
        assertTrue(result.finalized(), "Fixtures must finalize: " + result.diagnostics());

        var output = new MarkdownPrdGenerator()
                .generateCombined(result.unitOrNull().models(), "multi-file-prd");
        String actual = output.content();

        if (!Files.exists(GOLDEN)) {
            Files.writeString(GOLDEN, actual);
            fail("Golden file did not exist — written to " + GOLDEN
                    + ". Commit it and re-run the test.");
        }

        String expected = Files.readString(GOLDEN);

        if (!expected.equals(actual)) {
            String[] expectedLines = expected.split("\n", -1);
            String[] actualLines   = actual.split("\n", -1);
            int minLen = Math.min(expectedLines.length, actualLines.length);
            int firstDiff = minLen;
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
            fail("Generated combined PRD does not match golden file.\n" + hint);
        }
    }
}
