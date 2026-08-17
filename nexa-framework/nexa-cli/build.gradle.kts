plugins {
    application
    id("com.gradleup.shadow") version "9.2.0"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation(libs.jackson.databind)
    implementation(project(":nexa-core"))

}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass.set("nexa.framework.App")
    applicationDefaultJvmArgs = listOf("-Xms8m", "-Xmx128m")
}

tasks.shadowJar {
    archiveBaseName.set("nexa-cli")
    archiveClassifier.set("")
    archiveVersion.set("")
    
    manifest {
        attributes(
            "Main-Class" to "nexa.framework.NexaStandaloneRunner"
        )
    }
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to application.mainClass.get()
        )
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    minHeapSize = "8m"
    maxHeapSize = "512m"
    systemProperties(System.getProperties().map { it.key.toString() to it.value }.toMap())
}

tasks.register<JavaExec>("runStandalone") {
    group = "application"
    description = "Runs the Nexa Standalone Runner"
    mainClass.set("nexa.framework.NexaStandaloneRunner")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
    workingDir = projectDir
    systemProperties(System.getProperties().map { it.key.toString() to it.value }.toMap())
    if (project.hasProperty("appArgs")) {
        args = (project.property("appArgs") as String).split(" ")
    }
}
