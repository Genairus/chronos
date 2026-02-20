// ── Boundary verification ──────────────────────────────────────────────────────
// Run `./gradlew check` at the root to enforce architectural boundaries.
// The two tasks below are registered at the root level and wired into the root
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

// Wire boundary checks into the root check task so they run with `./gradlew check`.
// The root project has no Java plugin, so we register a minimal check task here.
val check by tasks.registering {
    group = "verification"
    description = "Runs all verification checks including architectural boundary audits"
    dependsOn(verifyArchBoundaries, verifyNoLegacyImports)
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