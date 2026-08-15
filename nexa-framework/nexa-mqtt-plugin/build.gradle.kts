plugins {
    java
    id("com.gradleup.shadow") version "8.3.0"
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(files("../nexa-api/build/libs/nexa-api.jar"))
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.shadowJar {
    archiveBaseName.set("nexa-mqtt-plugin")
    archiveClassifier.set("") 
    archiveVersion.set("")
}
