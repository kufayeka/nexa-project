plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    // Jackson annotations bisa ditambahkan jika ke depannya dibutuhkan oleh plugin DTO
}
