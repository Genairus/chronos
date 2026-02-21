import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    java
    application
    id("org.graalvm.buildtools.native") version "0.9.28"
}

// ── Version constant generation ────────────────────────────────────────────
// Writes ChronosVersion.java into build/generated/sources/version so the
// @Command(version = ...) annotation stays in sync with the Gradle project
// version without duplicating the string in source.
//
// Uses an abstract task class so the task action is configuration-cache
// compatible (no implicit capture of the build script instance).

abstract class GenerateVersionSourceTask : DefaultTask() {
    @get:Input
    abstract val versionValue: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val ver = versionValue.get()
        val pkgDir = outputDir.get().file("com/genairus/chronos/cli").asFile
        pkgDir.mkdirs()
        File(pkgDir, "ChronosVersion.java").writeText(
            """
            package com.genairus.chronos.cli;

            /** Generated at build time from the Gradle project version — do not edit. */
            public final class ChronosVersion {
                public static final String VERSION = "$ver";
                private ChronosVersion() {}
            }
            """.trimIndent() + "\n"
        )
    }
}

val generatedVersionDir = layout.buildDirectory.dir("generated/sources/version")

val generateVersionSource by tasks.registering(GenerateVersionSourceTask::class) {
    versionValue.set(project.version.toString())
    outputDir.set(generatedVersionDir)
}

sourceSets.main {
    java.srcDir(generatedVersionDir)
}

tasks.compileJava {
    dependsOn(generateVersionSource)
}

application {
    mainClass.set("com.genairus.chronos.cli.ChronosCli")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

dependencies {
    implementation(project(":chronos-core"))
    implementation(project(":chronos-ir"))
    implementation(project(":chronos-compiler"))
    implementation(project(":chronos-validator"))
    implementation(project(":chronos-generators"))
    implementation(project(":chronos-artifacts"))
    implementation("info.picocli:picocli:4.7.5")
    annotationProcessor("info.picocli:picocli-codegen:4.7.5")  // Graal-enables the jar
    implementation("com.google.code.gson:gson:2.10.1")
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("chronos")
            mainClass.set("com.genairus.chronos.cli.ChronosCli")
            buildArgs.add("--no-fallback")
            buildArgs.add("-H:+ReportExceptionStackTraces")
        }
    }
}
