package com.genairus.chronos.mcp;

import com.genairus.chronos.mcp.tools.GenerateTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GenerateTool}.
 */
class GenerateToolTest {

    private final GenerateTool tool = new GenerateTool();

    // A minimal valid model with no errors (CHR-009 WARNING is acceptable)
    private static final String VALID_SOURCE = """
            namespace test.gen
            entity Order {
                id: String
            }
            """;

    // A model with CHR-001 ERROR (journey missing actor)
    private static final String INVALID_SOURCE = """
            namespace test.invalid
            actor Customer
            journey PlaceOrder {
                steps: [
                    step Submit { action: "submit" expectation: "done" }
                ]
                outcomes: { success: "placed" }
            }
            """;

    @Test
    void dryRunReturnsPlanWithoutWritingFiles(@TempDir Path tmpDir) throws IOException {
        var srcFile = tmpDir.resolve("valid.chronos");
        Files.writeString(srcFile, VALID_SOURCE);

        var env = tool.execute(Map.of(
                "inputPaths", List.of(srcFile.toString()),
                "workspaceRoot", tmpDir.toString(),
                "outDir", tmpDir.resolve("out").toString(),
                "target", "prd",
                "dryRun", "true"));

        assertFalse(env.has("error"), "Must not return error envelope: " + env);
        var result = env.getAsJsonObject("result");

        assertFalse(result.get("generated").getAsBoolean(), "generated must be false for dryRun");
        assertTrue(result.get("dryRun").getAsBoolean(), "dryRun must be true");
        assertTrue(result.getAsJsonArray("writtenFiles").isEmpty(),
                "writtenFiles must be empty for dryRun");
        assertFalse(result.getAsJsonArray("plannedFiles").isEmpty(),
                "plannedFiles must be non-empty for valid dryRun");

        // Verify no actual files were written
        assertFalse(Files.exists(tmpDir.resolve("out")),
                "Output directory must not be created in dryRun mode");
    }

    @Test
    void errorModelReturnsGeneratedFalseWithDiagnostics(@TempDir Path tmpDir) throws IOException {
        var srcFile = tmpDir.resolve("invalid.chronos");
        Files.writeString(srcFile, INVALID_SOURCE);

        var env = tool.execute(Map.of(
                "inputPaths", List.of(srcFile.toString()),
                "workspaceRoot", tmpDir.toString(),
                "outDir", tmpDir.resolve("out").toString(),
                "target", "prd"));

        assertFalse(env.has("error"), "Must not return error envelope: " + env);
        var result = env.getAsJsonObject("result");

        assertFalse(result.get("generated").getAsBoolean(),
                "generated must be false when errors present");
        assertFalse(result.get("dryRun").getAsBoolean(), "dryRun must be false");
        assertTrue(result.getAsJsonArray("writtenFiles").isEmpty(),
                "writtenFiles must be empty when errors present");
        assertFalse(result.getAsJsonArray("diagnostics").isEmpty(),
                "diagnostics must be non-empty for invalid model");

        // Verify no files written
        assertFalse(Files.exists(tmpDir.resolve("out")),
                "Output directory must not be created when errors present");
    }

    @Test
    void successfulGenerateWritesFilesWithAbsolutePaths(@TempDir Path tmpDir) throws IOException {
        var srcFile = tmpDir.resolve("valid.chronos");
        Files.writeString(srcFile, VALID_SOURCE);
        var outDir = tmpDir.resolve("out");

        var env = tool.execute(Map.of(
                "inputPaths", List.of(srcFile.toString()),
                "workspaceRoot", tmpDir.toString(),
                "outDir", outDir.toString(),
                "target", "prd"));

        assertFalse(env.has("error"), "Must not return error envelope: " + env);
        var result = env.getAsJsonObject("result");

        assertTrue(result.get("generated").getAsBoolean(),
                "generated must be true for valid model. diagnostics="
                        + result.getAsJsonArray("diagnostics"));
        assertFalse(result.getAsJsonArray("writtenFiles").isEmpty(),
                "writtenFiles must be non-empty after successful generation");
        assertTrue(result.getAsJsonArray("plannedFiles").isEmpty(),
                "plannedFiles must be empty when not in dryRun mode");

        // Verify written files exist and have absolute paths
        var writtenFiles = result.getAsJsonArray("writtenFiles");
        for (var file : writtenFiles) {
            var filePath = Path.of(file.getAsString());
            assertTrue(filePath.isAbsolute(), "writtenFile path must be absolute: " + filePath);
            assertTrue(Files.exists(filePath), "Written file must exist: " + filePath);
        }
    }

    @Test
    void unknownTargetReturnsError(@TempDir Path tmpDir) throws IOException {
        var srcFile = tmpDir.resolve("valid.chronos");
        Files.writeString(srcFile, VALID_SOURCE);

        var env = tool.execute(Map.of(
                "inputPaths", List.of(srcFile.toString()),
                "workspaceRoot", tmpDir.toString(),
                "outDir", tmpDir.toString(),
                "target", "not_a_real_target"));

        assertTrue(env.has("error"), "Unknown target must return error");
        assertEquals("INVALID_INPUT",
                env.getAsJsonObject("error").get("code").getAsString());
    }

    @Test
    void missingInputPathsReturnsError(@TempDir Path tmpDir) {
        var env = tool.execute(Map.of(
                "outDir", tmpDir.toString(),
                "target", "prd"));
        assertTrue(env.has("error"));
        assertEquals("INVALID_INPUT",
                env.getAsJsonObject("error").get("code").getAsString());
    }

    @Test
    void missingTargetReturnsError(@TempDir Path tmpDir) throws IOException {
        var srcFile = tmpDir.resolve("valid.chronos");
        Files.writeString(srcFile, VALID_SOURCE);

        var env = tool.execute(Map.of(
                "inputPaths", List.of(srcFile.toString()),
                "workspaceRoot", tmpDir.toString(),
                "outDir", tmpDir.toString()));
        assertTrue(env.has("error"));
        assertEquals("INVALID_INPUT",
                env.getAsJsonObject("error").get("code").getAsString());
    }

    @Test
    void diagnosticsAlwaysPresentEvenWhenEmpty(@TempDir Path tmpDir) throws IOException {
        var srcFile = tmpDir.resolve("valid.chronos");
        Files.writeString(srcFile, VALID_SOURCE);

        var env = tool.execute(Map.of(
                "inputPaths", List.of(srcFile.toString()),
                "workspaceRoot", tmpDir.toString(),
                "outDir", tmpDir.resolve("out").toString(),
                "target", "prd",
                "dryRun", "true"));

        assertFalse(env.has("error"));
        var result = env.getAsJsonObject("result");
        assertTrue(result.has("diagnostics"), "diagnostics must always be present");
        assertTrue(result.has("diagnosticSort"), "diagnosticSort must always be present");
        assertTrue(result.has("writtenFiles"), "writtenFiles must always be present");
    }

    @Test
    void responseHasEnvelopeFields(@TempDir Path tmpDir) throws IOException {
        var srcFile = tmpDir.resolve("valid.chronos");
        Files.writeString(srcFile, VALID_SOURCE);

        var env = tool.execute(Map.of(
                "inputPaths", List.of(srcFile.toString()),
                "workspaceRoot", tmpDir.toString(),
                "outDir", tmpDir.resolve("out").toString(),
                "target", "prd",
                "dryRun", "true"));

        assertTrue(env.has("schemaVersion"), "must have schemaVersion");
        assertTrue(env.has("toolVersion"), "must have toolVersion");
    }

    @Test
    void writeFailureReturnsErrorWithPartialWriteDetails(@TempDir Path tmpDir) throws IOException {
        var srcFile = tmpDir.resolve("valid.chronos");
        Files.writeString(srcFile, VALID_SOURCE);

        // Make outDir a regular file so directory creation for output paths fails.
        var outDirFile = tmpDir.resolve("out-as-file");
        Files.writeString(outDirFile, "not a directory");

        var env = tool.execute(Map.of(
                "inputPaths", List.of(srcFile.toString()),
                "workspaceRoot", tmpDir.toString(),
                "outDir", outDirFile.toString(),
                "target", "prd"));

        assertTrue(env.has("error"), "Write failure must return error envelope");
        var error = env.getAsJsonObject("error");
        assertEquals("INTERNAL_ERROR", error.get("code").getAsString());
        assertFalse(error.get("retryable").getAsBoolean(),
                "Write failure with possible partial output must be non-retryable");

        var details = error.getAsJsonObject("details");
        assertTrue(details.has("writtenFiles"), "details must include writtenFiles");
        assertTrue(details.has("plannedFiles"), "details must include plannedFiles");
        assertTrue(details.has("failedPath"), "details must include failedPath");
        assertTrue(details.has("diagnostics"), "details must include diagnostics");
        assertTrue(details.has("diagnosticSort"), "details must include diagnosticSort");
    }
}
