plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":nexa-core"))
    api(project(":nexa-script-engine"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.test {
    useJUnitPlatform()
}
