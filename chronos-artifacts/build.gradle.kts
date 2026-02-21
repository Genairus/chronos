plugins {
    `java-library`
}

dependencies {
    // IrCompilationUnit, SourceUnit, CompileAllResult
    implementation(project(":chronos-compiler"))
    // IrJsonCodec (static toJson/fromJson) lives in chronos-ir
    implementation(project(":chronos-ir"))

    testImplementation(project(":chronos-core"))
    testImplementation(project(":chronos-compiler"))
}
