plugins {
    `java-library`
}

dependencies {
    implementation(project(":chronos-core"))
    implementation(project(":chronos-ir"))
    implementation(project(":chronos-parser"))
    implementation(project(":chronos-validator"))
}

tasks.test {
    systemProperty("chronos.rootDir", rootProject.projectDir.absolutePath)
}
