plugins {
    java
    application
    id("org.graalvm.buildtools.native") version "0.9.28"
}

application {
    mainClass.set("com.genairus.chronos.cli.ChronosCli")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

dependencies {
    implementation(project(":chronos-parser"))
    implementation(project(":chronos-validator"))
    implementation(project(":chronos-generators"))
    implementation("info.picocli:picocli:4.7.5")
    annotationProcessor("info.picocli:picocli-codegen:4.7.5")  // Graal-enables the jar
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