plugins {
    `java-library`
    id("com.gradleup.shadow") version "8.3.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":nexa-api"))
    // Compile-time only: used to expose the real workspace schema in OpenAPI.
    // The runtime already provides nexa-core, so it must not be bundled into the control plugin JAR.
    compileOnly(project(":nexa-core"))
    implementation("io.javalin:javalin:6.1.3")
    implementation("io.javalin.community.openapi:javalin-openapi-plugin:6.1.3")
    implementation("io.javalin.community.openapi:javalin-swagger-plugin:6.1.3")
    annotationProcessor("io.javalin.community.openapi:openapi-annotation-processor:6.1.3")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("io.moquette:moquette-broker:0.16")
    implementation("org.slf4j:slf4j-simple:2.0.12")
}

tasks.shadowJar {
    archiveBaseName.set("nexa-control-plugin")
    archiveClassifier.set("")
    archiveVersion.set("")
}
