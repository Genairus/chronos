import org.gradle.api.artifacts.ProjectDependency

// ── Boundary verification ──────────────────────────────────────────────────────
// Run `./gradlew check` at the root to enforce architectural boundaries.
// The tasks below are registered at the root level and wired into the root
// `check` lifecycle so that `./gradlew check` (or `./gradlew build`) always
// validates module-layer boundaries alongside the per-subproject test suites.

val verifyArchBoundaries by tasks.registering(Exec::class) {
    group = "verification"
    description = "Verifies module-layer boundary rules (audit-import-boundaries.sh --strict)"
    commandLine("bash", "${rootDir}/scripts/audit-import-boundaries.sh", "--strict")
    workingDir = rootDir
}

val verifyNoLegacyImports by tasks.registering(Exec::class) {
    group = "verification"
    description = "Verifies no legacy com.genairus.chronos.model.* imports remain (audit-forbidden-imports.sh)"
    commandLine("bash", "${rootDir}/scripts/audit-forbidden-imports.sh")
    workingDir = rootDir
}

// ── Module boundary tables ────────────────────────────────────────────────────

// Permanently + temporarily allowed project dependencies (any configuration).
// Only modules listed here are checked; modules absent from the map are skipped.
val allowedProjectDeps: Map<String, Set<String>> = mapOf(
    "chronos-core"       to emptySet(),
    "chronos-ir"         to setOf("chronos-core"),
    "chronos-parser"     to setOf("chronos-core"),
    "chronos-compiler"   to setOf("chronos-core", "chronos-ir", "chronos-parser", "chronos-validator"),
    "chronos-generators" to setOf("chronos-core", "chronos-ir"),
    "chronos-artifacts"  to setOf("chronos-core", "chronos-ir", "chronos-compiler"),
    "chronos-cli"        to setOf("chronos-core", "chronos-ir", "chronos-compiler",
                                   "chronos-generators", "chronos-artifacts",
                                   "chronos-validator")             // TEMP until CLI goes via compiler pipeline
)

// Deps in this map are in allowedProjectDeps only temporarily.
// verifyModuleBoundaries prints them as "TEMP ALLOW" so they stay visible.
val tempAllowedProjectDeps: Map<String, Set<String>> = mapOf(
    "chronos-cli" to setOf("chronos-validator")                     // TEMP until CLI goes via compiler pipeline
)

// Deps allowed ONLY in test configurations (testImplementation, testRuntimeOnly).
// If they appear in a main config (implementation, api, …) it is still a violation.
val testOnlyAllowedProjectDeps: Map<String, Set<String>> = mapOf(
    "chronos-generators" to setOf("chronos-compiler")   // compile .chronos fixtures in tests only
)

val verifyModuleBoundaries by tasks.registering {
    group = "verification"
    description = "Fails the build if any subproject introduces an undeclared project dependency"

    // Accessing `subprojects` at execution time is not configuration-cache compatible;
    // mark the task accordingly so Gradle does not attempt to serialize it.
    notCompatibleWithConfigurationCache("Inspects live project dependency declarations at execution time")

    doLast {
        val mainConfigs = setOf("implementation", "api", "compileOnly", "runtimeOnly")
        val testConfigs = setOf("testImplementation", "testRuntimeOnly")

        val violations = mutableListOf<String>()
        val tempAllows = mutableListOf<String>()

        for (sub in subprojects) {
            val allowed  = allowedProjectDeps[sub.name]         ?: continue
            val temp     = tempAllowedProjectDeps[sub.name]     ?: emptySet()
            val testOnly = testOnlyAllowedProjectDeps[sub.name] ?: emptySet()

            for (configName in mainConfigs + testConfigs) {
                val config = sub.configurations.findByName(configName) ?: continue
                val isTest = configName in testConfigs

                for (dep in config.dependencies.withType<ProjectDependency>()) {
                    val depName = dep.name   // project name, e.g. "chronos-core"
                    when {
                        depName in allowed && depName in temp ->
                            tempAllows += "${sub.name} -> $depName  [in $configName]"
                        depName in allowed -> { /* permanently allowed — silent */ }
                        isTest && depName in testOnly -> { /* test-only allowed — silent */ }
                        else ->
                            violations += "${sub.name} -> $depName  [in $configName]"
                    }
                }
            }
        }

        if (tempAllows.isNotEmpty()) {
            logger.warn(
                "verifyModuleBoundaries: TEMP ALLOWed dependencies (must be removed in a future phase):\n" +
                tempAllows.joinToString("\n") { "  TEMP ALLOW  $it" }
            )
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Module boundary violations detected:\n" +
                violations.joinToString("\n") { "  $it" }
            )
        }
        logger.lifecycle("verifyModuleBoundaries: boundaries OK.")
    }
}

// Wire boundary checks into the root check task so they run with `./gradlew check`.
// The root project has no Java plugin, so we register a minimal check task here.
val check by tasks.registering {
    group = "verification"
    description = "Runs all verification checks including architectural boundary audits"
    dependsOn(verifyArchBoundaries, verifyNoLegacyImports, verifyModuleBoundaries)
    // Also depend on every subproject's check task
    dependsOn(subprojects.map { "${it.path}:check" })
}

// ── Subprojects ────────────────────────────────────────────────────────────────

subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")

    group = "com.genairus.chronos"
    version = "0.1.0"

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.10.0")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        finalizedBy("jacocoTestReport")
    }

    tasks.withType<JacocoReport> {
        dependsOn("test")
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
}