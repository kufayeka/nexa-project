plugins {
    java
    id("com.gradleup.shadow") version "8.3.0"
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(project(":nexa-api"))
    implementation("com.intelligt.modbus:jlibmodbus:1.2.9.11") {
        exclude(group = "com.google.android.things", module = "androidthings")
    }

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.shadowJar {
    archiveBaseName.set("nexa-modbus-plugin")
    archiveClassifier.set("") 
    archiveVersion.set("")
}

tasks.test {
    useJUnitPlatform()
}

