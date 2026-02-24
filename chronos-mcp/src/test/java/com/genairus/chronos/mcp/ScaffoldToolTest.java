package com.genairus.chronos.mcp;

import com.genairus.chronos.compiler.ChronosCompiler;
import com.genairus.chronos.compiler.SourceUnit;
import com.genairus.chronos.mcp.tools.ScaffoldTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ScaffoldTool}.
 */
class ScaffoldToolTest {

    private final ScaffoldTool tool = new ScaffoldTool();
    private final ChronosCompiler compiler = new ChronosCompiler();

    // Shapes that should produce compilable=true scaffolds
    private static final List<String> COMPILABLE_SHAPES = List.of(
            "entity", "shape", "list", "map", "enum",
            "actor", "policy", "deny", "error", "role", "event");

    // Shapes that should produce compilable=false (require cross-references)
    private static final List<String> NON_COMPILABLE_SHAPES = List.of(
            "journey", "relationship", "invariant", "statemachine");

    @Test
    void compilableTrueShapesReturnCompiledFlag() {
        for (var shapeName : COMPILABLE_SHAPES) {
            var env = tool.execute(Map.of(
                    "namespace", "test.scaffold",
                    "shapes", List.of(shapeName)));
            assertFalse(env.has("error"),
                    "Expected success for shape=" + shapeName + ", got: " + env);
            var result = env.getAsJsonObject("result");
            assertTrue(result.get("compilable").getAsBoolean(),
                    "Expected compilable=true for shape=" + shapeName);
        }
    }

    @Test
    void compilableFalseShapesReturnFlagAndNotes() {
        for (var shapeName : NON_COMPILABLE_SHAPES) {
            var env = tool.execute(Map.of(
                    "namespace", "test.scaffold",
                    "shapes", List.of(shapeName)));
            assertFalse(env.has("error"),
                    "Expected success for shape=" + shapeName + ", got: " + env);
            var result = env.getAsJsonObject("result");
            assertFalse(result.get("compilable").getAsBoolean(),
                    "Expected compilable=false for shape=" + shapeName);
            assertFalse(result.getAsJsonArray("notes").isEmpty(),
                    "compilable=false shapes must have notes for: " + shapeName);
        }
    }

    @Test
    void compilableTrueScaffoldsActuallyCompile() {
        // Validate that entity, actor, enum, error, role scaffolds actually pass the compiler
        for (var shapeName : List.of("entity", "actor", "enum", "error", "role")) {
            var env = tool.execute(Map.of(
                    "namespace", "test.scaffold",
                    "shapes", List.of(shapeName)));
            assertFalse(env.has("error"), "scaffold failed for: " + shapeName);
            var content = env.getAsJsonObject("result").get("content").getAsString();

            // Run through the compiler — expect zero errors (warnings OK)
            var result = compiler.compileAll(List.of(new SourceUnit(shapeName + ".chronos", content)));
            var errors = result.errors();
            assertTrue(errors.isEmpty(),
                    "compilable=true scaffold for '" + shapeName + "' must pass validate with 0 errors. "
                            + "Got: " + errors);
        }
    }

    @Test
    void contentIncludesNamespaceHeader() {
        var env = tool.execute(Map.of(
                "namespace", "com.example.domain",
                "shapes", List.of("entity")));
        assertFalse(env.has("error"));
        var content = env.getAsJsonObject("result").get("content").getAsString();
        assertTrue(content.startsWith("namespace com.example.domain"),
                "Content must start with namespace declaration");
    }

    @Test
    void multipleShapesAllRendered() {
        var env = tool.execute(Map.of(
                "namespace", "test.multi",
                "shapes", List.of("entity", "enum", "actor")));
        assertFalse(env.has("error"));
        var content = env.getAsJsonObject("result").get("content").getAsString();
        assertTrue(content.contains("entity MyEntity"), "Must include entity scaffold");
        assertTrue(content.contains("enum MyEnum"), "Must include enum scaffold");
        assertTrue(content.contains("actor MyActor"), "Must include actor scaffold");
    }

    @Test
    void unknownShapeReturnsError() {
        var env = tool.execute(Map.of(
                "namespace", "test",
                "shapes", List.of("not_a_real_shape")));
        assertTrue(env.has("error"), "Unknown shape must return error");
        assertEquals("INVALID_INPUT",
                env.getAsJsonObject("error").get("code").getAsString());
    }

    @Test
    void missingNamespaceReturnsError() {
        var env = tool.execute(Map.of("shapes", List.of("entity")));
        assertTrue(env.has("error"));
        assertEquals("INVALID_INPUT",
                env.getAsJsonObject("error").get("code").getAsString());
    }

    @Test
    void mixedCompilabilitySetsFlagFalse() {
        // Combining a compilable=true and compilable=false shape → compilable=false overall
        var env = tool.execute(Map.of(
                "namespace", "test.mixed",
                "shapes", List.of("entity", "journey")));
        assertFalse(env.has("error"));
        var result = env.getAsJsonObject("result");
        assertFalse(result.get("compilable").getAsBoolean(),
                "Mixed compilable/non-compilable must set compilable=false");
    }

    @Test
    void includeCommentsFalseRemovesCommentLines() {
        var envWith = tool.execute(Map.of(
                "namespace", "test",
                "shapes", List.of("entity"),
                "includeComments", "true"));
        var envWithout = tool.execute(Map.of(
                "namespace", "test",
                "shapes", List.of("entity"),
                "includeComments", "false"));
        var contentWith = envWith.getAsJsonObject("result").get("content").getAsString();
        var contentWithout = envWithout.getAsJsonObject("result").get("content").getAsString();
        // entity scaffold has a // add fields here comment
        assertTrue(contentWith.contains("//"), "includeComments=true should retain // comments");
        assertFalse(contentWithout.contains("//"), "includeComments=false should remove // comment lines");
    }

    @Test
    void responseHasEnvelopeFields() {
        var env = tool.execute(Map.of(
                "namespace", "test",
                "shapes", List.of("entity")));
        assertTrue(env.has("schemaVersion"), "must have schemaVersion");
        assertTrue(env.has("toolVersion"), "must have toolVersion");
    }
}
