plugins {
    `java-library`
    id("com.gradleup.shadow") version "8.3.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":nexa-api"))
    implementation("io.javalin:javalin:6.1.3")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("io.moquette:moquette-broker:0.16")
    implementation("org.slf4j:slf4j-simple:2.0.12")
}

tasks.shadowJar {
    archiveBaseName.set("nexa-control-plugin")
    archiveClassifier.set("") 
    archiveVersion.set("")
}