plugins {
    id("io.github.goooler.shadow") version "8.1.8"
}

description = "Guilds Paper plugin — Bukkit glue, listeners, commands, and the integrated Guilds subsystem"

dependencies {
    implementation(project(":guilds-api"))
    implementation(project(":guilds-common"))
    implementation("dev.mintychochip.mint:mint-api:${property("mintApiVersion")}")
    compileOnly("io.papermc.paper:paper-api:26.2.build.111-stable")
    testImplementation("dev.mintychochip.mint:mint-api:${property("mintApiVersion")}")

    compileOnly("io.papermc.paper:paper-api:26.2.build.111-stable")
    // squaremap integration (renders territory polygons). squaremap-api 1.3.15
    // targets Minecraft 26.2 — matches the paper-api version above. compileOnly:
    // the squaremap jar is provided by the locally-built jar runServer loads.
    compileOnly("xyz.jpenilla:squaremap-api:1.3.15")
    compileOnly("io.github.flog99:mapgui-api:2.0.0")
    implementation("org.slf4j:slf4j-simple:2.0.16")

    testImplementation("io.papermc.paper:paper-api:26.2.build.111-stable")
    testImplementation("xyz.jpenilla:squaremap-api:1.3.15")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
tasks.named<Jar>("sourcesJar") {
    archiveBaseName.set("guilds")
    archiveVersion.set(project.version.toString())
}


tasks.jar {
    // Thin jar kept for sources/debug; delivery unit is shadowJar
    archiveBaseName.set("guilds")
    archiveClassifier.set("thin")
    manifest {
        attributes(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
        )
    }
}

tasks.shadowJar {
    archiveBaseName.set("guilds")
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
