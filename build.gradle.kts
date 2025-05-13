plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "1.4.20"
    id("com.gradleup.shadow") version "8.3.0"
}

group = "kr.lanthanide"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    implementation("net.minestom:minestom-snapshots:a1d1920a04")
    implementation("net.kyori:adventure-text-minimessage:4.20.0")

    implementation("org.postgresql:postgresql:42.7.5")
    implementation("net.postgis:postgis-jdbc:2024.1.0")
    implementation("org.locationtech.jts:jts-core:1.20.0")
    implementation("com.zaxxer:HikariCP:6.3.0")

    implementation("com.charleskorn.kaml:kaml:0.77.1")
    implementation("de.articdive:jnoise-pipeline:4.1.0")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks {
    jar {
        manifest {
            attributes["Main-Class"] = "kr.lanthanide.wanderland.MainKt"
        }
    }

    build {
        dependsOn(shadowJar)
    }
    shadowJar {
        mergeServiceFiles()
        archiveClassifier.set("") // Prevent the '-all' suffix on the shadowjar file.
    }
}