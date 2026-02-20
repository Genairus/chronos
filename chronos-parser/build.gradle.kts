plugins {
    java
    antlr
}

dependencies {
    antlr("org.antlr:antlr4:4.13.1")
    api("org.antlr:antlr4-runtime:4.13.1")
    implementation(project(":chronos-core"))
}

tasks.generateGrammarSource {
    arguments = listOf("-visitor", "-no-listener", "-package", "com.genairus.chronos.parser.generated")
}