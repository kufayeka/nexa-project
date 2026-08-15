plugins {
    `java-library`
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    implementation(project(":nexa-api"))
    implementation("io.javalin:javalin:6.1.3")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("io.moquette:moquette-broker:0.16")
    implementation("org.slf4j:slf4j-api:2.0.12")
}