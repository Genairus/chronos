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