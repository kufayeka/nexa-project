plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":nexa-api"))
    implementation("org.ow2.asm:asm:9.9")
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
}
