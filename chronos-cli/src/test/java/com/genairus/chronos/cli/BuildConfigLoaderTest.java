package com.genairus.chronos.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuildConfigLoaderTest {

    @TempDir
    Path tmp;

    // ── Round-trip ─────────────────────────────────────────────────────────────

    @Test
    void load_validJson_roundTrip() throws Exception {
        Path cfg = writeConfig("""
                {
                    "sources": ["**/*.chronos", "extras/*.chronos"],
                    "targets": [
                        {
                            "name": "docs",
                            "generator": "markdown",
                            "output": "out",
                            "include": ["journey:*"],
                            "exclude": ["entity:Internal"]
                        }
                    ],
                    "credentials": {
                        "apiKey": "abc123"
                    }
                }
                """);

        BuildConfig config = BuildConfigLoader.load(cfg);

        assertEquals(List.of("**/*.chronos", "extras/*.chronos"), config.sources());
        assertEquals(1, config.targets().size());

        BuildTarget t = config.targets().get(0);
        assertEquals("docs",     t.name());
        assertEquals("markdown", t.generator());
        assertEquals("out",      t.output());
        assertEquals(List.of("journey:*"),       t.include());
        assertEquals(List.of("entity:Internal"), t.exclude());

        assertEquals("abc123", config.credentials().get("apiKey"));
    }

    @Test
    void load_missingOptionalFields_defaultsToEmpty() throws Exception {
        Path cfg = writeConfig("""
                {
                    "sources": ["*.chronos"],
                    "targets": [
                        { "name": "t", "generator": "markdown", "output": "out" }
                    ]
                }
                """);

        BuildConfig config = BuildConfigLoader.load(cfg);

        assertTrue(config.credentials().isEmpty());
        BuildTarget t = config.targets().get(0);
        assertTrue(t.include().isEmpty(), "include should default to empty list");
        assertTrue(t.exclude().isEmpty(), "exclude should default to empty list");
    }

    // ── Env-var expansion ──────────────────────────────────────────────────────

    @Test
    void load_envVarExpansion() throws Exception {
        Path cfg = writeConfig("""
                {
                    "sources": ["*.chronos"],
                    "targets": [
                        {
                            "name": "t",
                            "generator": "${GENERATOR}",
                            "output":    "${OUT_DIR}"
                        }
                    ],
                    "credentials": {
                        "key": "${API_KEY}"
                    }
                }
                """);

        BuildConfig config = BuildConfigLoader.load(cfg, name -> switch (name) {
            case "GENERATOR" -> "markdown";
            case "OUT_DIR"   -> "generated";
            case "API_KEY"   -> "secret";
            default          -> null;
        });

        assertEquals("markdown",  config.targets().get(0).generator());
        assertEquals("generated", config.targets().get(0).output());
        assertEquals("secret",    config.credentials().get("key"));
    }

    @Test
    void load_defaultValue_whenVarAbsent() throws Exception {
        Path cfg = writeConfig("""
                {
                    "sources": ["${SRC_DIR:-src}/**/*.chronos"],
                    "targets": []
                }
                """);

        BuildConfig config = BuildConfigLoader.load(cfg, name -> null); // all absent

        assertEquals("src/**/*.chronos", config.sources().get(0));
    }

    @Test
    void load_envVarPresentOverridesDefault() throws Exception {
        Path cfg = writeConfig("""
                {
                    "sources": ["${SRC_DIR:-src}/**/*.chronos"],
                    "targets": []
                }
                """);

        BuildConfig config = BuildConfigLoader.load(cfg, name -> "custom");

        assertEquals("custom/**/*.chronos", config.sources().get(0));
    }

    @Test
    void load_undefinedVar_throwsBuildConfigException() throws Exception {
        Path cfg = writeConfig("""
                {
                    "sources": ["${UNDEFINED_VARIABLE_XYZ_123}/*.chronos"],
                    "targets": []
                }
                """);

        var ex = assertThrows(BuildConfigException.class,
                () -> BuildConfigLoader.load(cfg, name -> null));
        assertTrue(ex.getMessage().contains("UNDEFINED_VARIABLE_XYZ_123"),
                "Exception message should name the missing variable");
    }

    // ── Error cases ────────────────────────────────────────────────────────────

    @Test
    void load_missingFile_throwsBuildConfigException() {
        Path missing = tmp.resolve("no-file.json");
        var ex = assertThrows(BuildConfigException.class,
                () -> BuildConfigLoader.load(missing));
        assertTrue(ex.getMessage().contains("not found") || ex.getMessage().contains("Config"),
                "Message should mention missing file: " + ex.getMessage());
    }

    @Test
    void load_malformedJson_throwsBuildConfigException() throws Exception {
        Path cfg = writeConfig("{ this is: not json }");
        assertThrows(BuildConfigException.class, () -> BuildConfigLoader.load(cfg));
    }

    // ── resolveSourceFiles ────────────────────────────────────────────────────

    @Test
    void resolveSourceFiles_flatGlob_findsFiles() throws Exception {
        Files.writeString(tmp.resolve("a.chronos"), "namespace a");
        Files.writeString(tmp.resolve("b.chronos"), "namespace b");
        Files.writeString(tmp.resolve("ignored.txt"), "not chronos");

        var files = BuildConfigLoader.resolveSourceFiles(tmp, List.of("*.chronos"));

        assertEquals(2, files.size());
        assertTrue(files.stream().allMatch(p -> p.toString().endsWith(".chronos")));
    }

    @Test
    void resolveSourceFiles_recursiveGlob_findsNestedFiles() throws Exception {
        // Java glob: **/*.chronos requires at least one directory component, so both
        // files are placed in subdirectories to exercise true recursive traversal.
        Path subA = Files.createDirectory(tmp.resolve("a"));
        Path subB = Files.createDirectory(tmp.resolve("b"));
        Files.writeString(subA.resolve("first.chronos"),  "namespace a");
        Files.writeString(subB.resolve("second.chronos"), "namespace b");
        Files.writeString(tmp.resolve("ignored.txt"), "not chronos");

        var files = BuildConfigLoader.resolveSourceFiles(tmp, List.of("**/*.chronos"));

        assertEquals(2, files.size());
        assertTrue(files.stream().allMatch(p -> p.toString().endsWith(".chronos")));
    }

    @Test
    void resolveSourceFiles_noMatch_returnsEmpty() throws Exception {
        Files.writeString(tmp.resolve("model.txt"), "not chronos");

        var files = BuildConfigLoader.resolveSourceFiles(tmp, List.of("*.chronos"));

        assertTrue(files.isEmpty());
    }

    @Test
    void resolveSourceFiles_resultIsSorted() throws Exception {
        Files.writeString(tmp.resolve("z.chronos"), "namespace z");
        Files.writeString(tmp.resolve("a.chronos"), "namespace a");
        Files.writeString(tmp.resolve("m.chronos"), "namespace m");

        var files = BuildConfigLoader.resolveSourceFiles(tmp, List.of("*.chronos"));

        assertEquals(3, files.size());
        for (int i = 0; i < files.size() - 1; i++) {
            assertTrue(files.get(i).compareTo(files.get(i + 1)) < 0,
                    "Expected sorted paths");
        }
    }

    // ── expand unit tests ──────────────────────────────────────────────────────

    @Test
    void expand_noPlaceholder_returnsOriginal() {
        assertEquals("hello world",
                BuildConfigLoader.expand("hello world", name -> null));
    }

    @Test
    void expand_null_returnsNull() {
        assertNull(BuildConfigLoader.expand(null, name -> null));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Path writeConfig(String json) throws Exception {
        Path cfg = tmp.resolve("chronos-build.json");
        Files.writeString(cfg, json);
        return cfg;
    }
}
