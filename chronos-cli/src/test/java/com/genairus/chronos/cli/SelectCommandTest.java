package com.genairus.chronos.cli;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static com.genairus.chronos.cli.CliTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Disabled("select not registered in ChronosCli.subcommands for v0.1 — re-enable when shipped")
class SelectCommandTest {

    @TempDir
    Path tmp;

    // Model with two shapes for filtering tests
    private static final String MULTI_SHAPE_MODEL = """
            namespace com.example

            actor Customer

            entity Order {
                id: String
            }

            journey CheckoutJourney {
                actor: Customer
                steps: [
                    step pay {
                        action: "Customer pays"
                        expectation: "Payment is recorded"
                    }
                ]
                outcomes: {
                    success: "Order is confirmed"
                }
            }
            """;

    @Test
    void matchingPattern_printsMatchingShapes() throws Exception {
        Path file = writeChronos(tmp, MULTI_SHAPE_MODEL);
        Result r = run("select", file.toString(), "--pattern", "Journey");

        assertEquals(0, r.exit());
        assertTrue(r.out().contains("CheckoutJourney"),
                "Expected CheckoutJourney in output, got: " + r.out());
        assertTrue(r.out().contains("journey"),
                "Expected type 'journey' in output, got: " + r.out());
    }

    @Test
    void caseInsensitiveMatch() throws Exception {
        Path file = writeChronos(tmp, MULTI_SHAPE_MODEL);
        // 'checkout' (lowercase) should match 'CheckoutJourney'
        Result r = run("select", file.toString(), "--pattern", "checkout");

        assertEquals(0, r.exit());
        assertTrue(r.out().contains("CheckoutJourney"),
                "Expected case-insensitive match, got: " + r.out());
    }

    @Test
    void noMatch_producesNoOutput_exits0() throws Exception {
        Path file = writeChronos(tmp, MULTI_SHAPE_MODEL);
        Result r = run("select", file.toString(), "--pattern", "zzz-no-match-zzz");

        assertEquals(0, r.exit());
        // stdout should have no shape lines (may have trailing newline from println, check trimmed)
        assertTrue(r.out().isBlank(),
                "Expected no output for non-matching pattern, got: " + r.out());
    }

    @Test
    void partialMatch_onlyMatchingShapesListed() throws Exception {
        Path file = writeChronos(tmp, MULTI_SHAPE_MODEL);
        // 'Order' matches 'Order' entity but not 'Customer' or 'CheckoutJourney'
        Result r = run("select", file.toString(), "--pattern", "Order");

        assertEquals(0, r.exit());
        assertTrue(r.out().contains("Order"), "Expected Order in output");
        assertFalse(r.out().contains("Customer"), "Did not expect Customer in output");
        assertFalse(r.out().contains("CheckoutJourney"), "Did not expect CheckoutJourney in output");
    }

    @Test
    void outputFormat_containsTypeNameAndLocation() throws Exception {
        Path file = writeChronos(tmp, MULTI_SHAPE_MODEL);
        Result r = run("select", file.toString(), "--pattern", "Customer");

        assertEquals(0, r.exit());
        String line = r.out().strip();
        assertTrue(line.contains("actor"), "Expected 'actor' type in output: " + line);
        assertTrue(line.contains("Customer"), "Expected 'Customer' name: " + line);
        // SourceLocation format is file:line — check a colon is present
        assertTrue(line.contains(":"), "Expected location with ':' in output: " + line);
    }
}
