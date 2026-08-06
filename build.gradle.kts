plugins {
    java
    id("io.github.goooler.shadow") version "8.1.8"
}

group = "com.azoth"
version = "1.0.0-SNAPSHOT"
description = "Azoth Territory — large polygonal/chunk territories with Wilderness and Claimable zones, plus an integrated Guilds subsystem"

java {
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

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    // Compile-time only for domain persistence; Paper ships Gson at runtime.
    compileOnly("com.google.code.gson:gson:2.11.0")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    // Guilds runtime libraries — shaded into the single plugin JAR
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("io.javalin:javalin:6.3.0")
    implementation("org.slf4j:slf4j-simple:2.0.16")
    // Gson also needed at runtime by Guilds web; Paper may provide it, but shade for self-containment
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.google.code.gson:gson:2.11.0")
    testImplementation("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.processResources {
    val props = mapOf(
        "version" to version,
        "description" to (project.description ?: ""),
    )
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    // Thin jar kept for sources/debug; delivery unit is shadowJar
    archiveBaseName.set("azoth-territory")
    archiveClassifier.set("thin")
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
        )
    }
}

tasks.shadowJar {
    archiveBaseName.set("azoth-territory")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
    mergeServiceFiles()
    // Relocations optional; keep plain packages for simplicity (matches prior Guilds packaging)
}

tasks.named("build") {
    dependsOn(tasks.shadowJar)
}

// Prefer shadow artifact as the assembled plugin product
tasks.assemble {
    dependsOn(tasks.shadowJar)
}
