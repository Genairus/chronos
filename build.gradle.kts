subprojects {
    apply(plugin = "java")

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
    }
}