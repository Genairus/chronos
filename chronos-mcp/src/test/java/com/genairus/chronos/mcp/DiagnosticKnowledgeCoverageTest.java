package com.genairus.chronos.mcp;

import com.genairus.chronos.core.diagnostics.DiagnosticCodeRegistry;
import com.genairus.chronos.mcp.knowledge.DiagnosticKnowledge;
import com.genairus.chronos.mcp.tools.ExplainDiagnosticTool;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Build gate: verifies DiagnosticKnowledge.REGISTRY covers exactly all known CHR codes.
 *
 * <p>This test is the enforcement point for the coverage contract:
 * adding a new CHR code to DiagnosticCodeRegistry without adding a corresponding
 * entry to DiagnosticKnowledge causes this test to fail, blocking the build.
 */
class DiagnosticKnowledgeCoverageTest {

    @Test
    void registryCoversAllKnownCodes() {
        var known = DiagnosticCodeRegistry.ALL_KNOWN_CODES;
        var covered = DiagnosticKnowledge.REGISTRY.keySet();

        var missing = known.stream()
                .filter(c -> !covered.contains(c))
                .sorted()
                .toList();
        var extra = covered.stream()
                .filter(c -> !known.contains(c))
                .sorted()
                .toList();

        assertTrue(missing.isEmpty(),
                "DiagnosticKnowledge is missing entries for: " + missing
                        + "\nAdd entries to DiagnosticKnowledge.java for each missing code.");
        assertTrue(extra.isEmpty(),
                "DiagnosticKnowledge has entries for unknown codes: " + extra
                        + "\nRemove or update DiagnosticCodeRegistry.ALL_KNOWN_CODES.");
    }

    @Test
    void everyEntryHasNonBlankRequiredFields() {
        for (var entry : DiagnosticKnowledge.REGISTRY.values()) {
            assertFalse(entry.code().isBlank(), "code must not be blank");
            assertNotNull(entry.severity(), "severity must not be null for " + entry.code());
            assertFalse(entry.title().isBlank(), "title must not be blank for " + entry.code());
            assertFalse(entry.description().isBlank(), "description must not be blank for " + entry.code());
            assertNotNull(entry.likelyCauses(), "likelyCauses must not be null for " + entry.code());
            assertNotNull(entry.fixes(), "fixes must not be null for " + entry.code());
            assertNotNull(entry.examples(), "examples must not be null for " + entry.code());
        }
    }

    @Test
    void explainDiagnosticReturnsEntryForEveryCode() {
        var tool = new ExplainDiagnosticTool();
        for (var code : DiagnosticCodeRegistry.ALL_KNOWN_CODES) {
            var env = tool.execute(Map.of("code", code));
            assertFalse(env.has("error"),
                    "ExplainDiagnosticTool must return a result for code " + code + ", got error: " + env);
            var result = env.getAsJsonObject("result");
            assertEquals(code, result.get("code").getAsString());
            assertFalse(result.get("title").getAsString().isBlank());
        }
    }

    @Test
    void explainDiagnosticUnknownCodeReturnsError() {
        var tool = new ExplainDiagnosticTool();
        var env = tool.execute(Map.of("code", "CHR-999"));
        assertTrue(env.has("error"), "Unknown code must return error envelope");
        assertEquals("INVALID_INPUT",
                env.getAsJsonObject("error").get("code").getAsString());
    }

    @Test
    void explainDiagnosticIsCaseInsensitive() {
        var tool = new ExplainDiagnosticTool();
        var envUpper = tool.execute(Map.of("code", "CHR-001"));
        var envLower = tool.execute(Map.of("code", "chr-001"));
        // Both should succeed or both should fail consistently
        assertEquals(envUpper.has("result"), envLower.has("result"),
                "Case sensitivity should be consistent");
    }

    @Test
    void priorityCodesHaveCodeExamples() {
        // The 9 priority codes must have at least one bad/good example
        var priorityCodes = new String[]{"CHR-001", "CHR-002", "CHR-003", "CHR-008",
                "CHR-020", "CHR-025", "CHR-027", "CHR-034", "CHR-041"};
        for (var code : priorityCodes) {
            var entry = DiagnosticKnowledge.REGISTRY.get(code);
            assertNotNull(entry, "Priority code must be in registry: " + code);
            assertFalse(entry.examples().isEmpty(),
                    "Priority code " + code + " must have at least one code example");
            var ex = entry.examples().get(0);
            assertFalse(ex.bad().isBlank(), "bad example must not be blank for " + code);
            assertFalse(ex.good().isBlank(), "good example must not be blank for " + code);
        }
    }

    @Test
    void explainDiagnosticResponseHasEnvelopeFields() {
        var tool = new ExplainDiagnosticTool();
        var env = tool.execute(Map.of("code", "CHR-002", "includeExamples", "true"));

        assertFalse(env.has("error"), "must succeed");
        assertTrue(env.has("schemaVersion"));
        assertTrue(env.has("toolVersion"));

        var result = env.getAsJsonObject("result");
        assertTrue(result.has("code"));
        assertTrue(result.has("severity"));
        assertTrue(result.has("title"));
        assertTrue(result.has("description"));
        assertTrue(result.has("likelyCauses"));
        assertTrue(result.has("fixes"));
        assertTrue(result.has("examples"));
    }
}
