plugins {
    java
    id("io.github.goooler.shadow") version "8.1.8"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "org.aincraft"
version = "1.0.0"

repositories {
    gradlePluginPortal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.7-R0.1-SNAPSHOT")

    // Guice DI
    implementation("com.google.inject:guice:7.0.0")
    annotationProcessor("com.google.inject:guice:7.0.0")

    // Database
    implementation("com.zaxxer:HikariCP:5.1.0")
    runtimeOnly("org.mariadb.jdbc:mariadb-java-client:3.3.1")
    runtimeOnly("org.postgresql:postgresql:42.7.1")

    // Caffeine caching
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // HTTP Client and JSON
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Triumph GUI
    implementation("dev.triumphteam:triumph-gui:3.1.13")

    // Testing
    testCompileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
    testImplementation("org.junit.platform:junit-platform-commons:1.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")

    // MockBukkit for Bukkit testing (using v1.20 for compatibility)
    testImplementation("com.github.seeseemelk:MockBukkit-v1.20:3.88.0")

    // Additional testing utilities
    testImplementation("org.assertj:assertj-core:3.25.3")

    // H2 in-memory database for testing
    testImplementation("com.h2database:h2:2.2.224")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    shadowJar {
        archiveBaseName.set("Guilds")
        archiveClassifier.set("")
        archiveVersion.set("")

        // Note: Relocations disabled due to shadow plugin compatibility issues with Java 21
        // This is acceptable for testing; consider upgrading shadow plugin or downgrading Java for production
        // relocate("com.google.inject", "org.aincraft.towny.libs.guice")
        // relocate("org.aopalliance", "org.aincraft.towny.libs.aopalliance")
        // relocate("com.github.benmanes.caffeine", "org.aincraft.towny.libs.caffeine")
        // relocate("com.zaxxer.hikari", "org.aincraft.towny.libs.hikari")
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        // Use Paper 1.21.11
        minecraftVersion("1.21.11")

        // Use the shadowJar output
        pluginJars.from(shadowJar.flatMap { it.archiveFile })
    }

    test {
        useJUnitPlatform()
    }
}