plugins {
    java
    id("com.gradleup.shadow") version "8.3.0"
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(project(":nexa-api"))
    compileOnly(project(":nexa-script-engine"))
    compileOnly(project(":nexa-core"))
    compileOnly(libs.jackson.databind)


    testImplementation(project(":nexa-api"))
    testImplementation(project(":nexa-script-engine"))
    testImplementation(project(":nexa-core"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.shadowJar {
    archiveBaseName.set("nexa-asset-manager")
    archiveClassifier.set("") 
    archiveVersion.set("")
}

tasks.test {
    useJUnitPlatform()
}
