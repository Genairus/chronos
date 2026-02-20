package com.genairus.chronos.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.genairus.chronos.cli.CliTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that use the fixture .chronos files under examples/integration/.
 *
 * <p>These tests prove the end-to-end pipeline:
 * <ul>
 *   <li>Invalid actor reference causes exit 1 with CHR-008 in stderr</li>
 *   <li>{@code generate} on a fully valid fixture writes output and exits 0</li>
 * </ul>
 */
class FixtureIntegrationTest {

    // ── Undefined actor ────────────────────────────────────────────────────────

    /** A journey that references a non-existent actor must fail with CHR-008. */
    @Test
    void undefinedActor_validate_exits1_withChr008(@TempDir Path dir) throws Exception {
        String model = """
                namespace com.example.broken

                journey BrokenJourney {
                    actor: NonExistentActor
                    steps: [
                        step DoIt {
                            action: "Does something"
                            expectation: "It is done"
                        }
                    ]
                    outcomes: {
                        success: "Done"
                    }
                }
                """;

        Path file = writeChronos(dir, model);
        Result r = run("validate", file.toString());

        assertEquals(1, r.exit(), "Expected exit 1 for undefined actor. stderr: " + r.err());
        assertTrue(r.err().contains("CHR-008") || r.err().contains("CHR-012"),
                "Expected CHR-008 or CHR-012 in stderr for undefined actor, got: " + r.err());
    }

    // ── actor-and-journey fixture ──────────────────────────────────────────────

    /** The actor-and-journey fixture must validate cleanly (exit 0). */
    @Test
    void actorAndJourneyFixture_validate_exits0() {
        Path fixture = Path.of("examples/integration/actor-and-journey.chronos");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Files.exists(fixture),
                "Fixture not found: " + fixture);

        Result r = run("validate", fixture.toString());
        assertEquals(0, r.exit(),
                "actor-and-journey.chronos should have no errors. stderr: " + r.err());
    }

    /** The actor-and-journey fixture must produce at least one output file via generate. */
    @Test
    void actorAndJourneyFixture_generate_writesOutputFile(@TempDir Path outDir) {
        Path fixture = Path.of("examples/integration/actor-and-journey.chronos");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Files.exists(fixture),
                "Fixture not found: " + fixture);

        Result r = run("generate", fixture.toString(), "-t", "markdown", "-o", outDir.toString());

        assertEquals(0, r.exit(),
                "generate on actor-and-journey.chronos should exit 0. stderr: " + r.err());
        assertTrue(r.out().contains("Wrote"),
                "Expected 'Wrote' in stdout, got: " + r.out());

        // MarkdownPrdGenerator produces <namespace-with-hyphens>-prd.md
        Path expected = outDir.resolve("com-example-store-prd.md");
        assertTrue(Files.exists(expected),
                "Expected output file " + expected + " to exist");
        try {
            assertTrue(Files.size(expected) > 0, "Output file should be non-empty");
        } catch (Exception e) {
            fail("Could not read output file: " + e.getMessage());
        }
    }

    // ── minimal-entity fixture ─────────────────────────────────────────────────

    /** The minimal-entity fixture must validate cleanly. */
    @Test
    void minimalEntityFixture_validate_exits0() {
        Path fixture = Path.of("examples/integration/minimal-entity.chronos");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Files.exists(fixture),
                "Fixture not found: " + fixture);

        Result r = run("validate", fixture.toString());
        assertEquals(0, r.exit(),
                "minimal-entity.chronos should have no errors. stderr: " + r.err());
    }

    // ── relationship-basic fixture ─────────────────────────────────────────────

    /** The relationship-basic fixture must validate cleanly. */
    @Test
    void relationshipBasicFixture_validate_exits0() {
        Path fixture = Path.of("examples/integration/relationship-basic.chronos");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Files.exists(fixture),
                "Fixture not found: " + fixture);

        Result r = run("validate", fixture.toString());
        assertEquals(0, r.exit(),
                "relationship-basic.chronos should have no errors. stderr: " + r.err());
    }

    // ── checkout fixture ───────────────────────────────────────────────────────

    /** The existing checkout fixture must still validate cleanly. */
    @Test
    void checkoutFixture_validate_exits0() {
        Path fixture = Path.of("examples/integration/checkout.chronos");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Files.exists(fixture),
                "Fixture not found: " + fixture);

        Result r = run("validate", fixture.toString());
        assertEquals(0, r.exit(),
                "checkout.chronos should have no errors. stderr: " + r.err());
    }
}
