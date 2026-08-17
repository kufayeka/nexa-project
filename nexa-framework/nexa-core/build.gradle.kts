plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(project(":nexa-compiler"))

    implementation(libs.jackson.databind)
    api(project(":nexa-api"))
    implementation(project(":nexa-tags"))
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
