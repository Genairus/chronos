package com.genairus.chronos.mcp;

import com.genairus.chronos.mcp.tools.ListSymbolsTool;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ListSymbolsTool}.
 *
 * <p>Uses the fixture files established in Phase 1:
 * <ul>
 *   <li>{@code unresolved-refs.chronos} — parses OK; CHR-013 error; partial=true</li>
 *   <li>{@code parse-fail.chronos}      — fails to parse; partial=true, symbols=[]</li>
 *   <li>{@code ordering-a.chronos}      — CHR-002/CHR-005 errors; partial=true</li>
 * </ul>
 * and a clean model (ordering-a and ordering-b both would fail; need the valid fixture).
 * A clean compile: create inline source for tests that need finalized=true.
 */
class ListSymbolsToolTest {

    private final ListSymbolsTool tool = new ListSymbolsTool();

    private static final Path FIXTURE_DIR = Path.of(
            "src/test/resources/fixtures").toAbsolutePath();

    private String fixturePath(String name) {
        return FIXTURE_DIR.resolve(name).toString();
    }

    @Test
    void unresolvedRefsReturnsPartialTrueWithEntity() {
        var env = tool.execute(Map.of(
                "inputPaths", java.util.List.of(fixturePath("unresolved-refs.chronos")),
                "workspaceRoot", FIXTURE_DIR.toString()));
        assertFalse(env.has("error"), "Must not return error envelope: " + env);

        var result = env.getAsJsonObject("result");
        assertTrue(result.get("partial").getAsBoolean(),
                "partial must be true for unresolved-refs.chronos");

        var symbols = result.getAsJsonArray("symbols");
        assertFalse(symbols.isEmpty(), "symbols must not be empty — entity was parsed");

        // Find the Order entity
        boolean foundOrder = false;
        for (var sym : symbols) {
            var s = sym.getAsJsonObject();
            if ("Order".equals(s.get("name").getAsString())
                    && "entity".equals(s.get("kind").getAsString())) {
                foundOrder = true;
                // sourcePath must be absolute
                var sourcePath = s.get("sourcePath").getAsString();
                assertTrue(Path.of(sourcePath).isAbsolute(),
                        "sourcePath must be absolute: " + sourcePath);
                // Must have fieldNames
                assertTrue(s.has("fieldNames"), "entity must have fieldNames");
            }
        }
        assertTrue(foundOrder, "Must find Order entity in symbols");
    }

    @Test
    void unresolvedRefsHasDiagnostics() {
        var env = tool.execute(Map.of(
                "inputPaths", java.util.List.of(fixturePath("unresolved-refs.chronos")),
                "workspaceRoot", FIXTURE_DIR.toString()));
        var result = env.getAsJsonObject("result");
        var diags = result.getAsJsonArray("diagnostics");
        assertFalse(diags.isEmpty(), "diagnostics must not be empty for unresolved-refs.chronos");

        // Check for CHR-013 (unresolved type)
        boolean hasChr013 = false;
        for (var d : diags) {
            if ("CHR-013".equals(d.getAsJsonObject().get("code").getAsString())) {
                hasChr013 = true;
            }
        }
        assertTrue(hasChr013, "Must have CHR-013 diagnostic for UnknownProduct reference");
    }

    @Test
    void parseFailReturnsPartialTrueWithEmptySymbols() {
        var env = tool.execute(Map.of(
                "inputPaths", java.util.List.of(fixturePath("parse-fail.chronos")),
                "workspaceRoot", FIXTURE_DIR.toString()));
        assertFalse(env.has("error"), "Must not return error envelope: " + env);

        var result = env.getAsJsonObject("result");
        assertTrue(result.get("partial").getAsBoolean(),
                "partial must be true for parse-fail.chronos");

        var symbols = result.getAsJsonArray("symbols");
        assertTrue(symbols.isEmpty(), "symbols must be empty when parse fails");

        var diags = result.getAsJsonArray("diagnostics");
        assertFalse(diags.isEmpty(), "diagnostics must be non-empty for parse-fail.chronos");
    }

    @Test
    void cleanModelReturnsPartialFalseWithFullSymbols() {
        // Use a minimal valid model that compiles cleanly (CHR-009 WARNING is OK)
        String source = """
                namespace test.clean
                entity Product {
                    id: String
                    price: Float
                }
                enum ProductStatus {
                    ACTIVE
                    INACTIVE
                }
                actor Admin
                """;

        // Write to a temp file the tool can read
        var tmpPath = writeTempFile("clean.chronos", source);
        var env = tool.execute(Map.of(
                "inputPaths", java.util.List.of(tmpPath),
                "workspaceRoot", Path.of(tmpPath).getParent().toString()));
        assertFalse(env.has("error"), "Must not return error envelope: " + env);

        var result = env.getAsJsonObject("result");
        assertFalse(result.get("partial").getAsBoolean(),
                "partial must be false for clean model");

        var symbols = result.getAsJsonArray("symbols");
        // Expect Product (entity), ProductStatus (enum), Admin (actor) = 3
        assertEquals(3, symbols.size(), "Must have 3 symbols: Product, ProductStatus, Admin");
    }

    @Test
    void filterKindReturnsOnlyMatchingSymbols() {
        String source = """
                namespace test.filter
                entity MyEntity { id: String }
                enum MyEnum { A }
                actor MyActor
                """;
        var tmpPath = writeTempFile("filter.chronos", source);
        var env = tool.execute(Map.of(
                "inputPaths", java.util.List.of(tmpPath),
                "workspaceRoot", Path.of(tmpPath).getParent().toString(),
                "filterKind", "entity"));
        assertFalse(env.has("error"));
        var symbols = env.getAsJsonObject("result").getAsJsonArray("symbols");
        assertEquals(1, symbols.size(), "filterKind=entity must return only 1 symbol");
        assertEquals("entity", symbols.get(0).getAsJsonObject().get("kind").getAsString());
    }

    @Test
    void journeySymbolIncludesStepNames() {
        String source = """
                namespace test.orders
                actor Customer
                journey PlaceOrder {
                    actor: Customer
                    steps: [
                        step AddItem { action: "Add item" expectation: "Item added" },
                        step Checkout { action: "Checkout" expectation: "Order placed" }
                    ]
                    outcomes: { success: "Order placed" }
                }
                """;
        var tmpPath = writeTempFile("journey-sym.chronos", source);
        var env = tool.execute(Map.of(
                "inputPaths", java.util.List.of(tmpPath),
                "workspaceRoot", Path.of(tmpPath).getParent().toString(),
                "filterKind", "journey"));
        assertFalse(env.has("error"), "Got error: " + env);
        var result = env.getAsJsonObject("result");
        assertFalse(result.get("partial").getAsBoolean(),
                "partial should be false for valid journey. diagnostics="
                        + result.getAsJsonArray("diagnostics"));
        var symbols = result.getAsJsonArray("symbols");
        assertFalse(symbols.isEmpty(),
                "Must find journey symbol. partial=" + result.get("partial")
                        + " diagnostics=" + result.getAsJsonArray("diagnostics"));
        var journeySym = symbols.get(0).getAsJsonObject();
        assertEquals("PlaceOrder", journeySym.get("name").getAsString());
        assertEquals("Customer", journeySym.get("actorName").getAsString());
        var stepNames = journeySym.getAsJsonArray("stepNames");
        assertEquals(2, stepNames.size());
        assertEquals("AddItem", stepNames.get(0).getAsString());
        assertEquals("Checkout", stepNames.get(1).getAsString());
    }

    @Test
    void missingInputPathsReturnsError() {
        var env = tool.execute(Map.of());
        assertTrue(env.has("error"));
        assertEquals("INVALID_INPUT",
                env.getAsJsonObject("error").get("code").getAsString());
    }

    @Test
    void allSourcePathsInSymbolsAreAbsolute() {
        var env = tool.execute(Map.of(
                "inputPaths", java.util.List.of(fixturePath("unresolved-refs.chronos")),
                "workspaceRoot", FIXTURE_DIR.toString()));
        var symbols = env.getAsJsonObject("result").getAsJsonArray("symbols");
        for (var sym : symbols) {
            var sp = sym.getAsJsonObject().get("sourcePath").getAsString();
            assertTrue(Path.of(sp).isAbsolute(), "sourcePath must be absolute: " + sp);
        }
    }

    @Test
    void responseHasEnvelopeFields() {
        var env = tool.execute(Map.of(
                "inputPaths", java.util.List.of(fixturePath("unresolved-refs.chronos")),
                "workspaceRoot", FIXTURE_DIR.toString()));
        assertTrue(env.has("schemaVersion"));
        assertTrue(env.has("toolVersion"));
    }

    /** Writes source to a temp file and returns the absolute path string. */
    private String writeTempFile(String filename, String content) {
        try {
            var tmp = java.nio.file.Files.createTempFile("chronos-test-", "-" + filename);
            java.nio.file.Files.writeString(tmp, content);
            tmp.toFile().deleteOnExit();
            return tmp.toAbsolutePath().toString();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }
}
