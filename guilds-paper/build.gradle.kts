plugins {
    id("io.github.goooler.shadow") version "8.1.8"
}

description = "Guilds Paper plugin — Bukkit glue, listeners, commands, and the integrated Guilds subsystem"

dependencies {
    implementation(project(":guilds-api"))
    implementation(project(":guilds-common"))
    compileOnly("dev.mintychochip.mint:mint-api:${property("mintApiVersion")}")
    compileOnly("io.papermc.paper:paper-api:26.2.build.111-stable")
    testImplementation("dev.mintychochip.mint:mint-api:${property("mintApiVersion")}")

    compileOnly("io.papermc.paper:paper-api:26.2.build.111-stable")
    // squaremap integration (renders territory polygons). squaremap-api 1.3.15
    // targets Minecraft 26.2 — matches the paper-api version above. compileOnly:
    // the squaremap jar is provided by the locally-built jar runServer loads.
    compileOnly("xyz.jpenilla:squaremap-api:1.3.15")
    compileOnly("io.github.flog99:mapgui-api:2.0.0")
    // PlaceholderAPI is optional at runtime; the server supplies the plugin jar.
    compileOnly("me.clip:placeholderapi:${property("placeholderApiVersion")}")
    // WorldGuard integration (mirrors territory boundaries into real WG regions).
    // Soft dependency: worldguard-bukkit bundles the API (com.sk89q.worldguard.*)
    // plus its WorldEdit dependency (com.sk89q.worldedit.*) needed for region math.
    // compileOnly: the actual jar is provided by the server's plugins/ folder at
    // runtime. WorldGuard/WorldEdit pin "strictly" to the Guava/Gson versions
    // Mojang bundled in the Paper version they were built against; that strict
    // constraint conflicts with this project's newer paper-api. Since these are
    // never on the runtime classpath here (compileOnly), drop those transitive
    // constraints rather than the classes we actually need.
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.18") {
        exclude(group = "com.google.guava", module = "guava")
        exclude(group = "com.google.code.gson", module = "gson")
    }
    implementation("org.slf4j:slf4j-simple:2.0.16")

    testImplementation("io.papermc.paper:paper-api:26.2.build.111-stable")
    testImplementation("xyz.jpenilla:squaremap-api:1.3.15")
    testImplementation("io.github.flog99:mapgui-api:2.0.0")
    testImplementation("com.sk89q.worldguard:worldguard-bukkit:7.0.18") {
        exclude(group = "com.google.guava", module = "guava")
        exclude(group = "com.google.code.gson", module = "gson")
    }
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("me.clip:placeholderapi:${property("placeholderApiVersion")}")
    testImplementation("org.mockito:mockito-junit-jupiter:5.23.0")
}

tasks.processResources {
    val props = mapOf(
            "version" to version,
            "description" to (project.description ?: ""),
    )
    inputs.properties(props)
    filesMatching(listOf("plugin.yml", "paper-plugin.yml")) {
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
