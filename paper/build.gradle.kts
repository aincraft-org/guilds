plugins {
    id("io.github.goooler.shadow") version "8.1.8"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

description = "Azoth Territory Paper plugin — Bukkit glue, listeners, commands, and the integrated Guilds subsystem"

dependencies {
    implementation(project(":api"))
    implementation(project(":common"))
    compileOnly("dev.mintychochip.mint:mint-api:${property("mintApiVersion")}")
    testCompileOnly("dev.mintychochip.mint:mint-api:${property("mintApiVersion")}")

    compileOnly("io.papermc.paper:paper-api:26.2.build.111-stable")
    // squaremap integration (renders territory polygons). squaremap 1.3.15 targets
    // Minecraft 26.2 — matches the paper-api version above. compileOnly: the
    // squaremap jar is provided by the server (downloaded by the runServer task).
    compileOnly("xyz.jpenilla:squaremap-api:1.3.15")
    // Compile-time only for the Vault economy bridge; Vault is a softdepend.
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    implementation("org.slf4j:slf4j-simple:2.0.16")
    testImplementation("io.papermc.paper:paper-api:26.2.build.111-stable")
    testImplementation("xyz.jpenilla:squaremap-api:1.3.15")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.23.0")
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

// Local test server: ./gradlew :paper:runServer
// Boots Paper 26.2 with the azoth-territory shadow jar plus the squaremap
// 1.3.15 Paper jar (pinned GitHub release asset) loaded as plugins. squaremap
// serves its live web map on http://localhost:8080 by default.
tasks.runServer {
    minecraftVersion("26.2")
    runDirectory.set(layout.projectDirectory.dir("run"))
    downloadPlugins {
        github("jpenilla", "squaremap", "v1.3.15", "squaremap-paper-mc26.2-1.3.15.jar")
    }
}
