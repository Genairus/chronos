plugins {
    java
}

dependencies {
    implementation(project(":chronos-core"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.18.2")
    implementation("com.fasterxml.jackson.module:jackson-module-parameter-names:2.18.2")
    testImplementation(project(":chronos-parser"))
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}
