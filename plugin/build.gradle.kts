plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

group = "io.github.kaz"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    implementation("net.dv8tion:JDA:5.6.1") {
        exclude(module = "opus-java") // voice support, not needed
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

// JDA must be bundled into the plugin jar, so we ship the shaded jar.
tasks.jar { enabled = false }
tasks.shadowJar { archiveClassifier.set("") }
tasks.build { dependsOn(tasks.shadowJar) }
