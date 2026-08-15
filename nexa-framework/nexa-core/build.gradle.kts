plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    
    // Add nexa-script-engine to tests so ServiceLoader works for integration tests
    testImplementation(project(":nexa-script-engine"))

    implementation(libs.jackson.databind)
    api(project(":nexa-api"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    minHeapSize = "8m"
    maxHeapSize = "512m"
    systemProperties(System.getProperties().map { it.key.toString() to it.value }.toMap())
}