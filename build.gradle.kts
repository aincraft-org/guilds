import com.github.spotbugs.snom.SpotBugsTask
import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.plugins.quality.PmdExtension
import org.gradle.api.tasks.testing.Test

plugins {
    id("net.ltgt.errorprone") version "5.1.0" apply false
    id("com.github.spotbugs") version "6.5.10" apply false
}

group = "com.azoth"
version = providers.gradleProperty("releaseVersion").orElse("26.8.13").get()
description = "Azoth Territory — large polygonal/chunk territories with Wilderness and Claimable zones, plus an integrated Guilds subsystem"

// Shared configuration for the api / common / paper modules.
// Delivery unit stays the single shadowed Paper plugin JAR built by :paper.
subprojects {
    apply(plugin = "java")

    apply(plugin = "pmd")
    apply(plugin = "checkstyle")
    apply(plugin = "com.github.spotbugs")
    apply(plugin = "net.ltgt.errorprone")

    dependencies {
        add("errorprone", "com.google.errorprone:error_prone_core:2.50.0")
        add("spotbugs", "com.github.spotbugs:spotbugs:4.10.3")
    }
    extensions.configure<PmdExtension> {
        toolVersion = "7.26.0"
    }
    tasks.withType<Pmd>().configureEach {
        ruleSetFiles = files(rootProject.file("config/pmd/pmd.xml"))
        ruleSets = emptyList()
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    tasks.withType<Checkstyle>().configureEach {
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
    tasks.withType<SpotBugsTask>().configureEach {
        effort.set(Effort.MAX)
        // Gate and reports use high-confidence findings; lower-confidence findings stay outside this policy.
        reportLevel.set(Confidence.HIGH)
        excludeFilter.set(rootProject.file("config/spotbugs/exclude.xml"))
        reports {
            create("xml") {
                required.set(true)
            }
            create("html") {
                required.set(true)
            }
        }
    }

    group = rootProject.group
    version = rootProject.version

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(26))
        }
        withSourcesJar()
    }

    repositories {
        maven("/tmp/aincraft-mint/build/maven-repo")
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/aincraft-org/mint")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: providers.gradleProperty("gpr.user").orNull ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: providers.gradleProperty("gpr.key").orNull ?: ""
            }
        }
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
