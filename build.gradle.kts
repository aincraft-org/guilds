import org.gradle.api.tasks.testing.Test

group = "com.azoth"
version = "1.0.0-SNAPSHOT"
description = "Azoth Territory — large polygonal/chunk territories with Wilderness and Claimable zones, plus an integrated Guilds subsystem"

// Shared configuration for the api / common / paper modules.
// Delivery unit stays the single shadowed Paper plugin JAR built by :paper.
subprojects {
    apply(plugin = "java")

    group = rootProject.group
    version = rootProject.version

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withSourcesJar()
    }

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        maven("https://jitpack.io")
    }

    tasks.named<Test>("test") {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
        }
    }
}
