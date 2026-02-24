package com.genairus.chronos.mcp;

import com.genairus.chronos.mcp.tools.EmitIrBundleTool;
import com.google.gson.JsonArray;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EmitIrBundleTool}.
 */
class EmitIrBundleToolTest {

    private final EmitIrBundleTool tool = new EmitIrBundleTool();

    private static final String VALID_SOURCE = """
            namespace test.bundle
            entity Product {
                id: String
            }
            """;

    // Triggers CHR-007 and CHR-009 (both warnings, no errors) — bundle must still be emitted
    private static final String WARN_ONLY_SOURCE = """
            namespace test.bundle.warn

            actor User

            journey ExampleJourney {
                actor: User
                steps: [
                    step doSomething {
                        action: "Actor performs an action"
                        expectation: "System responds appropriately"
                    }
                ]
                outcomes: {
                    success: "Journey completed successfully"
                }
            }
            """;

    @Test
    void successfulEmitWritesBundleFile(@TempDir Path tmpDir) throws IOException {
        var srcFile = tmpDir.resolve("valid.chronos");
        Files.writeString(srcFile, VALID_SOURCE);
        var outDir = tmpDir.resolve("out");

        var env = tool.execute(Map.of(
                "inputPaths", List.of(srcFile.toString()),
                "workspaceRoot", tmpDir.toString(),
                "outDir", outDir.toString()));

        assertFalse(env.has("error"), "Must not return error envelope: " + env);
        var result = env.getAsJsonObject("result");

        // bundlePath must be absolute
        var bundlePath = Path.of(result.get("bundlePath").getAsString());
        assertTrue(bundlePath.isAbsolute(), "bundlePath must be absolute: " + bundlePath);
        assertTrue(Files.exists(bundlePath), "Bundle file must exist on disk: " + bundlePath);
        assertTrue(bundlePath.getFileName().toString().equals("ir-bundle.json"),
                "Bundle file must be named ir-bundle.json");

        // format and version
        assertEquals("chronos-ir-bundle", result.get("format").getAsString());
        assertEquals("1", result.get("version").getAsString());

        // modelCount
        assertEquals(1, result.get("modelCount").getAsInt());

        // diagnostics always present (empty for a clean model)
        assertTrue(result.has("diagnostics"), "result must have diagnostics array");
        assertTrue(result.has("diagnosticSort"), "result must have diagnosticSort");
        assertEquals(0, result.getAsJsonArray("diagnostics").size(),
                "clean model must have empty diagnostics");
    }

    @Test
    void bundleFileContainsExpectedJson(@TempDir Path tmpDir) throws IOException {
        var srcFile = tmpDir.resolve("valid.chronos");
        Files.writeString(srcFile, VALID_SOURCE);
        var outDir = tmpDir.resolve("out");

        var env = tool.execute(Map.of(
                "inputPaths", List.of(srcFile.toString()),
                "workspaceRoot", tmpDir.toString(),
                "outDir", outDir.toString()));

        assertFalse(env.has("error"));
        var bundlePath = Path.of(env.getAsJsonObject("result").get("bundlePath").getAsString());
        var content = Files.readString(bundlePath);

        assertTrue(content.contains("\"format\""), "bundle must have format field");
        assertTrue(content.contains("chronos-ir-bundle"), "bundle must have correct format value");
        assertTrue(content.contains("\"entries\""), "bundle must have entries array");
    }

    @Test
    void parseFailReturnsCompileError(@TempDir Path tmpDir) throws IOException {
        var srcFile = tmpDir.resolve("broken.chronos");
        Files.writeString(srcFile, "namespace test.broken entity { broken syntax {{{");
        var outDir = tmpDir.resolve("out");

        var env = tool.execute(Map.of(
                "inputPaths", List.of(srcFile.toString()),
                "workspaceRoot", tmpDir.toString(),
                "outDir", outDir.toString()));

        assertTrue(env.has("error"), "Parse failure must return error envelope");
        var error = env.getAsJsonObject("error");
        assertEquals("COMPILE_ERROR", error.get("code").getAsString());

        // diagnostics must be present in the error details
        assertTrue(error.has("details"), "COMPILE_ERROR must include details");
        var details = error.getAsJsonObject("details");
        assertTrue(details.has("diagnostics"), "details must have diagnostics array");
        assertTrue(details.has("diagnosticSort"), "details must have diagnosticSort");
        assertTrue(details.getAsJsonArray("diagnostics").size() > 0,
                "parse failure must report at least one diagnostic");
    }

    @Test
    void warningsOnlyEmitsBundleAndIncludesDiagnostics(@TempDir Path tmpDir) throws IOException {
        var srcFile = tmpDir.resolve("warn.chronos");
        Files.writeString(srcFile, WARN_ONLY_SOURCE);
        var outDir = tmpDir.resolve("out");

        var env = tool.execute(Map.of(
                "inputPaths", List.of(srcFile.toString()),
                "workspaceRoot", tmpDir.toString(),
                "outDir", outDir.toString()));

        // warnings must not block bundle emission
        assertFalse(env.has("error"), "Warnings-only model must not return error envelope: " + env);
        var result = env.getAsJsonObject("result");

        // bundle must exist
        var bundlePath = Path.of(result.get("bundlePath").getAsString());
        assertTrue(Files.exists(bundlePath), "Bundle file must exist for warnings-only model");

        // diagnostics array must be present and contain at least one WARNING
        assertTrue(result.has("diagnostics"), "result must have diagnostics array");
        JsonArray diags = result.getAsJsonArray("diagnostics");
        assertTrue(diags.size() > 0, "warnings-only model must report diagnostics");
        boolean hasWarning = false;
        for (var elem : diags) {
            if ("WARNING".equals(elem.getAsJsonObject().get("severity").getAsString())) {
                hasWarning = true;
                break;
            }
        }
        assertTrue(hasWarning, "diagnostics must contain at least one WARNING entry");
    }

    @Test
    void missingInputPathsReturnsError(@TempDir Path tmpDir) {
        var env = tool.execute(Map.of(
                "outDir", tmpDir.toString()));
        assertTrue(env.has("error"));
        assertEquals("INVALID_INPUT",
                env.getAsJsonObject("error").get("code").getAsString());
    }

    @Test
    void missingOutDirReturnsError(@TempDir Path tmpDir) throws IOException {
        var srcFile = tmpDir.resolve("valid.chronos");
        Files.writeString(srcFile, VALID_SOURCE);

        var env = tool.execute(Map.of(
                "inputPaths", List.of(srcFile.toString()),
                "workspaceRoot", tmpDir.toString()));
        assertTrue(env.has("error"));
        assertEquals("INVALID_INPUT",
                env.getAsJsonObject("error").get("code").getAsString());
    }

    @Test
    void responseHasEnvelopeFields(@TempDir Path tmpDir) throws IOException {
        var srcFile = tmpDir.resolve("valid.chronos");
        Files.writeString(srcFile, VALID_SOURCE);

        var env = tool.execute(Map.of(
                "inputPaths", List.of(srcFile.toString()),
                "workspaceRoot", tmpDir.toString(),
                "outDir", tmpDir.resolve("out").toString()));

        assertTrue(env.has("schemaVersion"), "must have schemaVersion");
        assertTrue(env.has("toolVersion"), "must have toolVersion");
    }
}
