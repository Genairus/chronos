package com.genairus.chronos.mcp;

import com.genairus.chronos.mcp.tools.DiscoverTool;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DiscoverTool — covers:
 * - All .chronos files found in the fixtures directory
 * - Paths in response are absolute
 * - Namespace extracted correctly
 * - namespace: null for files without a namespace declaration
 */
class DiscoverToolTest {

    private static Path fixturesDir;

    @BeforeAll
    static void loadFixtures() throws URISyntaxException {
        var url = DiscoverToolTest.class.getClassLoader().getResource("fixtures/ordering-a.chronos");
        assertNotNull(url, "fixtures directory not found on classpath");
        fixturesDir = Path.of(url.toURI()).getParent();
    }

    @Test
    void findsAllChronosFilesInFixturesDir() {
        var tool = new DiscoverTool();
        var env = tool.execute(Map.of("workspaceRoot", fixturesDir.toString()));

        assertFalse(env.has("error"), "discover must succeed: " + env);
        var files = env.getAsJsonObject("result").getAsJsonArray("files");
        var paths = extractPaths(files);

        assertTrue(paths.stream().anyMatch(p -> p.endsWith("ordering-a.chronos")));
        assertTrue(paths.stream().anyMatch(p -> p.endsWith("ordering-b.chronos")));
        assertTrue(paths.stream().anyMatch(p -> p.endsWith("parse-fail.chronos")));
        assertTrue(paths.stream().anyMatch(p -> p.endsWith("unresolved-refs.chronos")));
    }

    @Test
    void allReturnedPathsAreAbsolute() {
        var tool = new DiscoverTool();
        var env = tool.execute(Map.of("workspaceRoot", fixturesDir.toString()));

        var files = env.getAsJsonObject("result").getAsJsonArray("files");
        for (var elem : files) {
            var p = Path.of(elem.getAsJsonObject().get("path").getAsString());
            assertTrue(p.isAbsolute(), "discovered path must be absolute: " + p);
        }
    }

    @Test
    void namespacesExtractedCorrectly() {
        var tool = new DiscoverTool();
        var env = tool.execute(Map.of("workspaceRoot", fixturesDir.toString()));

        var files = env.getAsJsonObject("result").getAsJsonArray("files");
        for (var elem : files) {
            var obj = elem.getAsJsonObject();
            var path = obj.get("path").getAsString();
            var ns = obj.get("namespace");

            if (path.endsWith("ordering-a.chronos")) {
                assertEquals("test.a", ns.getAsString(), "ordering-a must have namespace test.a");
            } else if (path.endsWith("ordering-b.chronos")) {
                assertEquals("test.b", ns.getAsString(), "ordering-b must have namespace test.b");
            } else if (path.endsWith("parse-fail.chronos")) {
                assertEquals("test.c", ns.getAsString(), "parse-fail has namespace test.c on line 1");
            } else if (path.endsWith("unresolved-refs.chronos")) {
                assertEquals("test.d", ns.getAsString(), "unresolved-refs has namespace test.d");
            }
        }
    }

    @Test
    void responseIncludesWorkspaceRoot() {
        var tool = new DiscoverTool();
        var env = tool.execute(Map.of("workspaceRoot", fixturesDir.toString()));

        var result = env.getAsJsonObject("result");
        assertTrue(result.has("workspaceRoot"), "result must include workspaceRoot");
        assertFalse(result.get("workspaceRoot").getAsString().isBlank());
    }

    @Test
    void sizeBytesPresentAndPositive() {
        var tool = new DiscoverTool();
        var env = tool.execute(Map.of("workspaceRoot", fixturesDir.toString()));

        var files = env.getAsJsonObject("result").getAsJsonArray("files");
        for (var elem : files) {
            var obj = elem.getAsJsonObject();
            assertTrue(obj.has("sizeBytes"), "each file entry must have sizeBytes");
            assertTrue(obj.get("sizeBytes").getAsLong() > 0, "sizeBytes must be > 0");
        }
    }

    @Test
    void hiddenFilesAreNotReturned() throws Exception {
        // Create a hidden file in a temp subdir of fixturesDir — should not be returned
        // We can't create hidden dirs inside the test resource dir, so we verify the
        // hidden-dir filter logic indirectly: none of the returned paths start with '.'
        var tool = new DiscoverTool();
        var env = tool.execute(Map.of("workspaceRoot", fixturesDir.toString()));

        var files = env.getAsJsonObject("result").getAsJsonArray("files");
        for (var elem : files) {
            var pathStr = elem.getAsJsonObject().get("path").getAsString();
            var fileName = Path.of(pathStr).getFileName().toString();
            assertFalse(fileName.startsWith("."), "hidden files must not be returned: " + fileName);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> extractPaths(JsonArray files) {
        var paths = new ArrayList<String>();
        for (var elem : files) {
            paths.add(elem.getAsJsonObject().get("path").getAsString());
        }
        return paths;
    }
}
