import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

plugins {
    java
    application
}

// ── McpVersion constant generation ─────────────────────────────────────────
// Emits McpVersion.java so chronos.health can return the server version
// without depending on ChronosVersion from chronos-cli (boundary violation).
//
// Uses the same abstract-task pattern as chronos-cli for configuration-cache
// compatibility.

abstract class GenerateVersionSourceTask : DefaultTask() {
    @get:Input
    abstract val versionValue: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val ver = versionValue.get()
        val pkgDir = outputDir.get().file("com/genairus/chronos/mcp").asFile
        pkgDir.mkdirs()
        File(pkgDir, "McpVersion.java").writeText(
            """
            package com.genairus.chronos.mcp;

            /** Generated at build time from the Gradle project version — do not edit. */
            public final class McpVersion {
                public static final String VERSION = "$ver";
                private McpVersion() {}
            }
            """.trimIndent() + "\n"
        )
    }
}

val generatedVersionDir = layout.buildDirectory.dir("generated/sources/version")

val generateMcpVersionSource by tasks.registering(GenerateVersionSourceTask::class) {
    versionValue.set(project.version.toString())
    outputDir.set(generatedVersionDir)
}

// ── ShapeKnowledge code generation ─────────────────────────────────────────
// Reads YAML overlays from src/main/resources/shape-overlays/ and emits
// ShapeKnowledge.java at build time.  SnakeYAML is declared only in the
// buildscript classpath so it is NOT on the server runtime classpath.

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.yaml:snakeyaml:2.2")
    }
}

abstract class GenerateShapeKnowledgeTask : DefaultTask() {
    @get:InputDirectory
    abstract val overlaysDir: DirectoryProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    @Suppress("UNCHECKED_CAST")
    fun generate() {
        val outFile = outputFile.get().asFile
        outFile.parentFile.mkdirs()

        val yaml = org.yaml.snakeyaml.Yaml()
        val overlayDir = overlaysDir.get().asFile
        val entries = mutableListOf<String>()

        overlayDir.listFiles { f -> f.extension == "yaml" }
            ?.sortedBy { it.nameWithoutExtension }
            ?.forEach { file ->
                val data = file.inputStream().use { yaml.load<Map<String, Any>>(it) }
                val shape          = (data["shape"] as? String)?.let { escapeJava(it) } ?: ""
                val description    = (data["description"] as? String)?.let { escapeJava(it) } ?: ""
                val minimalExample = (data["minimal_example"] as? String)?.let { escapeJava(it) } ?: ""
                val fullExample    = (data["full_example"] as? String)?.let { escapeJava(it) } ?: ""
                val scaffold       = (data["scaffold_template"] as? String)?.let { escapeJava(it) } ?: ""
                val compilable     = (data["compilable"] as? Boolean) ?: true
                val applicableRules = ((data["applicable_rules"] as? List<*>) ?: emptyList<Any>())
                    .joinToString(", ") { "\"${escapeJava(it.toString())}\"" }
                val requiredFields  = buildFieldList(data["required_fields"])
                val optionalFields  = buildFieldList(data["optional_fields"])
                val commonMistakes  = buildStringList(data["common_mistakes"])
                val notes           = buildStringList(data["notes"])

                entries += """
                    |        map.put("$shape", new ShapeEntry(
                    |            "$shape",
                    |            "$description",
                    |            $compilable,
                    |            java.util.List.of($applicableRules),
                    |            $requiredFields,
                    |            $optionalFields,
                    |            $commonMistakes,
                    |            $notes,
                    |            "$minimalExample",
                    |            "$fullExample",
                    |            "$scaffold"
                    |        ));
                """.trimMargin()
            }

        outFile.writeText(
            """
            package com.genairus.chronos.mcp.knowledge;

            import java.util.HashMap;
            import java.util.List;
            import java.util.Map;

            /**
             * Build-time generated shape knowledge.
             * Source: chronos-mcp/src/main/resources/shape-overlays/*.yaml
             * Do not edit this file directly — edit the YAML overlays and rebuild.
             */
            public final class ShapeKnowledge {

                public record FieldEntry(String name, String type, String notes) {}

                public record ShapeEntry(
                    String shape,
                    String description,
                    boolean compilable,
                    List<String> applicableRules,
                    List<FieldEntry> requiredFields,
                    List<FieldEntry> optionalFields,
                    List<String> commonMistakes,
                    List<String> notes,
                    String minimalExample,
                    String fullExample,
                    String scaffoldTemplate
                ) {}

                public static final Map<String, ShapeEntry> REGISTRY;

                static {
                    var map = new HashMap<String, ShapeEntry>();
            ${entries.joinToString("\n")}
                    REGISTRY = java.util.Collections.unmodifiableMap(map);
                }

                private ShapeKnowledge() {}
            }
            """.trimIndent() + "\n"
        )
    }

    private fun escapeJava(s: String): String =
        s.replace("\\", "\\\\")
         .replace("\"", "\\\"")
         .replace("\n", "\\n")
         .replace("\r", "\\r")
         .replace("\t", "\\t")

    @Suppress("UNCHECKED_CAST")
    private fun buildFieldList(raw: Any?): String {
        val list = (raw as? List<Map<String, Any>>) ?: return "java.util.List.of()"
        val items = list.joinToString(", ") { m ->
            val n = escapeJava((m["name"] as? String) ?: "")
            val t = escapeJava((m["type"] as? String) ?: "")
            val notes = escapeJava((m["notes"] as? String) ?: "")
            "new FieldEntry(\"$n\", \"$t\", \"$notes\")"
        }
        return "java.util.List.of($items)"
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildStringList(raw: Any?): String {
        val list = (raw as? List<*>) ?: return "java.util.List.of()"
        val items = list.joinToString(", ") { "\"${escapeJava(it.toString())}\"" }
        return "java.util.List.of($items)"
    }
}

val generatedKnowledgeDir = layout.buildDirectory.dir("generated/sources/mcp")

val generateShapeKnowledge by tasks.registering(GenerateShapeKnowledgeTask::class) {
    overlaysDir.set(layout.projectDirectory.dir("src/main/resources/shape-overlays"))
    outputFile.set(generatedKnowledgeDir.map { it.file("com/genairus/chronos/mcp/knowledge/ShapeKnowledge.java") })
}

sourceSets.main {
    java.srcDir(generatedVersionDir)
    java.srcDir(generatedKnowledgeDir)
}

tasks.compileJava {
    dependsOn(generateMcpVersionSource, generateShapeKnowledge)
}

application {
    mainClass.set("com.genairus.chronos.mcp.ChronosMcpServer")
}

dependencies {
    implementation(project(":chronos-core"))
    implementation(project(":chronos-ir"))
    implementation(project(":chronos-compiler"))
    implementation(project(":chronos-generators"))
    implementation(project(":chronos-artifacts"))
    implementation("io.modelcontextprotocol.sdk:mcp:1.0.0")  // pulls in mcp-core + mcp-json-jackson3
    implementation("com.google.code.gson:gson:2.10.1")

    // mcp-test:1.0.0 pulls in JUnit 6.0.2, incompatible with the project-wide JUnit 5.10.0.
    // Add back when upgrading the whole project to JUnit 6.
    // testImplementation("io.modelcontextprotocol.sdk:mcp-test:1.0.0")
}
