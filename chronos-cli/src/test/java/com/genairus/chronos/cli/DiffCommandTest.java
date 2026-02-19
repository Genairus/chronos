package com.genairus.chronos.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static com.genairus.chronos.cli.CliTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class DiffCommandTest {

    @TempDir
    Path tmp;

    private static final String BASE_MODEL = """
            namespace com.example

            actor Customer

            entity Order {
                id: String
            }
            """;

    // HEAD adds a new shape and removes entity Order
    private static final String HEAD_WITH_ADDITION = """
            namespace com.example

            actor Customer

            entity Order {
                id: String
            }

            actor Operator
            """;

    private static final String HEAD_WITHOUT_ORDER = """
            namespace com.example

            actor Customer
            """;

    // Type change: Customer was an actor, now it's an entity
    private static final String HEAD_TYPE_CHANGE = """
            namespace com.example

            entity Customer {
                id: String
            }

            entity Order {
                id: String
            }
            """;

    @Test
    void identicalFiles_exits0_noOutput() throws Exception {
        Path base = writeChronos(tmp, BASE_MODEL, "base.chronos");
        Path head = writeChronos(tmp, BASE_MODEL, "head.chronos");

        Result r = run("diff", base.toString(), head.toString());

        assertEquals(0, r.exit(), "Expected exit 0 for identical models");
        assertTrue(r.out().isBlank(), "Expected no diff output for identical models");
    }

    @Test
    void addition_exits1_printsPlusLine() throws Exception {
        Path base = writeChronos(tmp, BASE_MODEL, "base.chronos");
        Path head = writeChronos(tmp, HEAD_WITH_ADDITION, "head.chronos");

        Result r = run("diff", base.toString(), head.toString());

        assertEquals(1, r.exit(), "Expected exit 1 for models with differences");
        assertTrue(r.out().contains("+ ") && r.out().contains("Operator"),
                "Expected '+ actor Operator' in output, got: " + r.out());
    }

    @Test
    void removal_exits1_printsMinusLine() throws Exception {
        Path base = writeChronos(tmp, BASE_MODEL, "base.chronos");
        Path head = writeChronos(tmp, HEAD_WITHOUT_ORDER, "head.chronos");

        Result r = run("diff", base.toString(), head.toString());

        assertEquals(1, r.exit());
        assertTrue(r.out().contains("- ") && r.out().contains("Order"),
                "Expected '- entity Order' in output, got: " + r.out());
    }

    @Test
    void typeChange_exits1_printsTildeLine() throws Exception {
        Path base = writeChronos(tmp, BASE_MODEL, "base.chronos");
        Path head = writeChronos(tmp, HEAD_TYPE_CHANGE, "head.chronos");

        Result r = run("diff", base.toString(), head.toString());

        assertEquals(1, r.exit());
        assertTrue(r.out().contains("~ ") && r.out().contains("Customer"),
                "Expected '~ entity Customer' in output, got: " + r.out());
    }

    @Test
    void missingBaseFile_exits1() {
        Path missing = tmp.resolve("no-base.chronos");
        Path head    = tmp.resolve("head.chronos");
        Result r = run("diff", missing.toString(), head.toString());
        assertEquals(1, r.exit());
    }
}
