// chronos-model has no external dependencies.
// The root build.gradle.kts already applies the java plugin and configures
// Java 21, repositories, and JUnit for all subprojects.
plugins {
    java
}

dependencies {
    testImplementation(project(":chronos-parser"))
}
