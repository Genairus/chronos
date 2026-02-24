package com.genairus.chronos.mcp;

import com.genairus.chronos.mcp.knowledge.ShapeKnowledge;
import com.genairus.chronos.mcp.tools.DescribeShapeTool;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DescribeShapeTool}.
 */
class DescribeShapeToolTest {

    private final DescribeShapeTool tool = new DescribeShapeTool();

    @Test
    void allShapeTypesReturnSuccessResult() {
        for (var shapeName : ShapeKnowledge.REGISTRY.keySet()) {
            var env = tool.execute(Map.of("shape", shapeName));
            assertFalse(env.has("error"),
                    "Expected success for shape=" + shapeName + ", got error: " + env);
            var result = env.getAsJsonObject("result");
            assertEquals(shapeName, result.get("shape").getAsString());
            assertFalse(result.get("description").getAsString().isBlank(),
                    "description must not be blank for: " + shapeName);
        }
    }

    @Test
    void unknownShapeReturnsErrorEnvelope() {
        var env = tool.execute(Map.of("shape", "unknown_shape_xyz"));
        assertTrue(env.has("error"), "Unknown shape must return error envelope");
        assertEquals("INVALID_INPUT",
                env.getAsJsonObject("error").get("code").getAsString());
    }

    @Test
    void missingShapeArgumentReturnsError() {
        var env = tool.execute(Map.of());
        assertTrue(env.has("error"));
        assertEquals("INVALID_INPUT",
                env.getAsJsonObject("error").get("code").getAsString());
    }

    @Test
    void resultIncludesExamplesByDefault() {
        var env = tool.execute(Map.of("shape", "entity"));
        assertFalse(env.has("error"));
        var result = env.getAsJsonObject("result");
        assertTrue(result.has("minimalExample"), "minimalExample must be present by default");
        assertTrue(result.has("fullExample"), "fullExample must be present by default");
        assertTrue(result.has("scaffoldTemplate"), "scaffoldTemplate must be present by default");
        assertFalse(result.get("minimalExample").getAsString().isBlank());
    }

    @Test
    void includeExampleFalseOmitsExamples() {
        var env = tool.execute(Map.of("shape", "entity", "includeExample", "false"));
        assertFalse(env.has("error"));
        var result = env.getAsJsonObject("result");
        assertFalse(result.has("minimalExample"), "minimalExample must be absent when includeExample=false");
        assertFalse(result.has("fullExample"), "fullExample must be absent when includeExample=false");
    }

    @Test
    void resultIncludesApplicableRulesAndMistakes() {
        var env = tool.execute(Map.of("shape", "journey"));
        assertFalse(env.has("error"));
        var result = env.getAsJsonObject("result");
        assertTrue(result.has("applicableRules"), "applicableRules must be present");
        assertTrue(result.has("commonMistakes"), "commonMistakes must be present");
        assertTrue(result.has("requiredFields"), "requiredFields must be present");
        // journey requires actor and outcomes
        var rules = result.getAsJsonArray("applicableRules");
        boolean hasChr001 = false;
        for (var r : rules) {
            if ("CHR-001".equals(r.getAsString())) hasChr001 = true;
        }
        assertTrue(hasChr001, "journey must list CHR-001 as an applicable rule");
    }

    @Test
    void shapeIsCaseInsensitive() {
        var envLower = tool.execute(Map.of("shape", "entity"));
        var envUpper = tool.execute(Map.of("shape", "ENTITY"));
        var envMixed = tool.execute(Map.of("shape", "Entity"));
        assertEquals(envLower.has("result"), envUpper.has("result"),
                "Case should be normalized consistently");
        assertEquals(envLower.has("result"), envMixed.has("result"),
                "Case should be normalized consistently");
    }

    @Test
    void compilableFlagPresent() {
        // entity is compilable=true; journey is compilable=false
        var entityEnv = tool.execute(Map.of("shape", "entity"));
        var journeyEnv = tool.execute(Map.of("shape", "journey"));
        assertTrue(entityEnv.getAsJsonObject("result").get("compilable").getAsBoolean(),
                "entity must have compilable=true");
        assertFalse(journeyEnv.getAsJsonObject("result").get("compilable").getAsBoolean(),
                "journey must have compilable=false");
    }

    @Test
    void responseHasEnvelopeFields() {
        var env = tool.execute(Map.of("shape", "entity"));
        assertTrue(env.has("schemaVersion"), "must have schemaVersion");
        assertTrue(env.has("toolVersion"), "must have toolVersion");
    }
}
