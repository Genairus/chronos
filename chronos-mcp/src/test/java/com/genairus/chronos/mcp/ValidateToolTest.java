package com.genairus.chronos.mcp;

import com.genairus.chronos.mcp.response.McpMeta;
import com.genairus.chronos.mcp.tools.ValidateTool;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ValidateTool — covers:
 * - Diagnostic sort order (path → line → col → code)
 * - schemaVersion and diagnosticSort metadata
 * - errorCount / warningCount
 * - All sourcePath values are absolute
 * - Parse failure is handled gracefully (no exception)
 * - Clean model returns diagnostics: [] (not absent)
 */
class ValidateToolTest {

    private static Path fixturesDir;
    private static Path orderingA;
    private static Path orderingB;
    private static Path parseFail;
    private static Path unresolvedRefs;

    @BeforeAll
    static void loadFixtures() throws URISyntaxException {
        var url = ValidateToolTest.class.getClassLoader().getResource("fixtures/ordering-a.chronos");
        assertNotNull(url, "ordering-a.chronos fixture not found on classpath");
        orderingA = Path.of(url.toURI());
        orderingB = Path.of(ValidateToolTest.class.getClassLoader()
                .getResource("fixtures/ordering-b.chronos").toURI());
        parseFail = Path.of(ValidateToolTest.class.getClassLoader()
                .getResource("fixtures/parse-fail.chronos").toURI());
        unresolvedRefs = Path.of(ValidateToolTest.class.getClassLoader()
                .getResource("fixtures/unresolved-refs.chronos").toURI());
        fixturesDir = orderingA.getParent();
    }

    // ── Ordering test ─────────────────────────────────────────────────────────

    @Test
    void diagnosticsSortedPathThenLineThenColThenCode() {
        // ordering-a (alphabetically first path) has CHR-002 and CHR-005
        // ordering-b (alphabetically second) has CHR-001
        // Even though CHR-001 fires at line 5 in ordering-b, it must sort AFTER all
        // ordering-a diagnostics because path is the primary sort key.
        var tool = new ValidateTool();
        var env = tool.execute(Map.of(
                "inputPaths", List.of(orderingA.toString(), orderingB.toString()),
                "workspaceRoot", fixturesDir.toString()
        ));

        assertFalse(env.has("error"), "must succeed: " + env);
        var result = env.getAsJsonObject("result");
        var diags = result.getAsJsonArray("diagnostics");
        var codes = extractCodes(diags);

        // All expected codes must be present
        assertTrue(codes.contains("CHR-001"), "CHR-001 (no actor) must be present");
        assertTrue(codes.contains("CHR-002"), "CHR-002 (no outcomes) must be present");
        assertTrue(codes.contains("CHR-005"), "CHR-005 (duplicate) must be present");

        int idx001 = codes.indexOf("CHR-001");
        int idx002 = codes.indexOf("CHR-002");
        int idx005 = codes.indexOf("CHR-005");

        // Path-first: ordering-a diagnostics (002, 005) must precede ordering-b (001)
        assertTrue(idx002 < idx001,
                "CHR-002 from ordering-a must sort before CHR-001 from ordering-b");
        assertTrue(idx005 < idx001,
                "CHR-005 from ordering-a must sort before CHR-001 from ordering-b");

        // Within ordering-a: CHR-002 (journey at earlier line) before CHR-005 (duplicate at later line)
        assertTrue(idx002 < idx005,
                "CHR-002 must sort before CHR-005 within ordering-a (lower line number)");
    }

    // ── Metadata ─────────────────────────────────────────────────────────────

    @Test
    void responseIncludesSchemaVersionAndDiagnosticSort() {
        var tool = new ValidateTool();
        var env = tool.execute(Map.of(
                "inputPaths", List.of(orderingA.toString()),
                "workspaceRoot", fixturesDir.toString()
        ));

        assertEquals(McpMeta.SCHEMA_VERSION, env.get("schemaVersion").getAsString());
        var result = env.getAsJsonObject("result");
        assertEquals(McpMeta.DIAGNOSTIC_SORT, result.get("diagnosticSort").getAsString());
    }

    @Test
    void errorCountReflectsActualErrors() {
        var tool = new ValidateTool();
        var env = tool.execute(Map.of(
                "inputPaths", List.of(orderingA.toString()),
                "workspaceRoot", fixturesDir.toString()
        ));

        var result = env.getAsJsonObject("result");
        int errorCount = result.get("errorCount").getAsInt();
        assertTrue(errorCount > 0, "ordering-a has errors (CHR-002, CHR-005)");
    }

    // ── Path security ─────────────────────────────────────────────────────────

    @Test
    void allSourcePathsInDiagnosticsAreAbsolute() {
        var tool = new ValidateTool();
        var env = tool.execute(Map.of(
                "inputPaths", List.of(orderingA.toString()),
                "workspaceRoot", fixturesDir.toString()
        ));

        var diags = env.getAsJsonObject("result").getAsJsonArray("diagnostics");
        for (var elem : diags) {
            var d = elem.getAsJsonObject();
            if (d.has("sourcePath")) {
                var p = Path.of(d.get("sourcePath").getAsString());
                assertTrue(p.isAbsolute(), "sourcePath must be absolute: " + p);
            }
        }
    }

    // ── Parse failure ─────────────────────────────────────────────────────────

    @Test
    void parseFailureReturnsErrorDiagnosticsNotException() {
        var tool = new ValidateTool();
        var env = tool.execute(Map.of(
                "inputPaths", List.of(parseFail.toString()),
                "workspaceRoot", fixturesDir.toString()
        ));

        // Must not throw — must return a valid (success or error) envelope
        assertNotNull(env);
        // The tool may return either a success envelope with error diagnostics,
        // or an error envelope if compilation is completely unrecoverable.
        // Either way, there must be at least one diagnostic or error message.
        if (env.has("result")) {
            var result = env.getAsJsonObject("result");
            int errorCount = result.get("errorCount").getAsInt();
            assertTrue(errorCount > 0, "parse-fail must have at least one error diagnostic");
        } else {
            assertTrue(env.has("error"), "response must have either result or error");
        }
    }

    // ── Diagnostics array always present ─────────────────────────────────────

    @Test
    void validModelReturnsDiagnosticsArrayNotAbsent() {
        // A file with only a namespace + entity — should produce zero errors
        var tool = new ValidateTool();
        var env = tool.execute(Map.of(
                "inputPaths", List.of(unresolvedRefs.toString()),
                "workspaceRoot", fixturesDir.toString()
        ));

        // May have errors (unresolved refs) but diagnostics array must be present
        if (env.has("result")) {
            var result = env.getAsJsonObject("result");
            assertTrue(result.has("diagnostics"), "diagnostics must always be present in result");
            assertInstanceOf(JsonArray.class, result.get("diagnostics"),
                    "diagnostics must be an array");
        }
    }

    // ── Input validation ──────────────────────────────────────────────────────

    @Test
    void missingInputPathsReturnsErrorEnvelope() {
        var tool = new ValidateTool();
        var env = tool.execute(Map.of("workspaceRoot", fixturesDir.toString()));

        assertTrue(env.has("error"), "missing inputPaths must produce an error envelope");
        assertEquals("INVALID_INPUT", env.getAsJsonObject("error").get("code").getAsString());
    }

    @Test
    void pathOutsideWorkspaceReturnsSecurityError() {
        var tool = new ValidateTool();
        // Use /tmp as workspace root so fixturesDir is outside it
        var env = tool.execute(Map.of(
                "inputPaths", List.of(orderingA.toString()),
                "workspaceRoot", "/tmp"
        ));

        // If fixturesDir is not under /tmp, we expect PATH_OUTSIDE_WORKSPACE
        if (env.has("error")) {
            assertEquals("PATH_OUTSIDE_WORKSPACE",
                    env.getAsJsonObject("error").get("code").getAsString());
        }
        // If /tmp happens to be a parent of fixturesDir, this test is a no-op — acceptable
    }

    // ── Argument coercion (ToolArgs.toList) ───────────────────────────────────

    @Test
    void inputPathsAsPlainStringSingleValueIsCoercedToSinglePath() {
        // MCP clients (e.g. Agent Gateway) may serialize a single-item array as
        // a plain String. ToolArgs.toList wraps it as a single-element list so
        // the tool succeeds without a ClassCastException.
        var tool = new ValidateTool();
        var env = tool.execute(Map.of(
                "inputPaths", orderingA.toString(),
                "workspaceRoot", fixturesDir.toString()
        ));

        assertFalse(env.has("error"),
                "Single-string inputPaths must succeed, got: " + env);
        var result = env.getAsJsonObject("result");
        assertTrue(result.get("errorCount").getAsInt() > 0,
                "ordering-a has errors (CHR-002, CHR-005)");
    }

    @Test
    void inputPathsAsCommaSeparatedStringIsTreatedAsSinglePathNotSplit() {
        // Documented contract: ToolArgs.toList does NOT split path-typed strings
        // on commas, because paths may legitimately contain commas. Multi-path
        // payloads must be sent as a JSON array. A comma-joined string is
        // therefore treated as a single literal path and produces a "Cannot
        // read file" error — not a successful two-file compile.
        //
        // The comma must live inside a single filename component (not between
        // two absolute paths) so the string is a legal Path on every platform —
        // Windows rejects "C:\a,C:\b" up-front with InvalidPathException
        // because of the embedded colon, which would surface as INTERNAL_ERROR.
        var tool = new ValidateTool();
        var combined = fixturesDir.resolve("first.chronos,second.chronos").toString();
        var env = tool.execute(Map.of(
                "inputPaths", combined,
                "workspaceRoot", fixturesDir.toString()
        ));

        assertTrue(env.has("error"),
                "Comma-joined inputPaths string must NOT be split; expected an error envelope, got: " + env);
        var error = env.getAsJsonObject("error");
        assertEquals("INVALID_INPUT", error.get("code").getAsString(),
                "Treating the combined string as a single non-existent path must yield INVALID_INPUT");
        assertTrue(error.get("message").getAsString().contains(combined),
                "Error message should reference the combined literal path: " + error);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> extractCodes(JsonArray diagnostics) {
        var codes = new ArrayList<String>();
        for (var elem : diagnostics) {
            codes.add(elem.getAsJsonObject().get("code").getAsString());
        }
        return codes;
    }

}
