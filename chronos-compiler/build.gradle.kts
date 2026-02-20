plugins {
    `java-library`
}

dependencies {
    implementation(project(":chronos-core"))
    implementation(project(":chronos-model"))
    implementation(project(":chronos-parser"))
    implementation(project(":chronos-validator"))
}
