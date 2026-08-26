plugins {
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

description = "Guilds local test server — run-paper harness that boots Paper with the guilds shadow jar as the test plugin"

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.111-stable")
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

// Local test server: ./gradlew :guilds-test:runServer
// Boots Paper 26.2 with the guilds shadow jar plus the squaremap
// 1.3.15 Paper jar (pinned GitHub release asset) loaded as plugins. squaremap
// serves its live web map on http://localhost:18080 when using the local Rust backend.
val mintPluginOwner = providers.gradleProperty("mintPluginOwner").orNull
val mintPluginRepository = providers.gradleProperty("mintPluginRepository").orNull
val mintPluginTag = providers.gradleProperty("mintPluginTag").orNull
val mintPluginAsset = providers.gradleProperty("mintPluginAsset").orNull
val mintPluginCoordinates = listOf(
        mintPluginOwner,
        mintPluginRepository,
        mintPluginTag,
        mintPluginAsset,
)
require(mintPluginCoordinates.all { it == null } || mintPluginCoordinates.all { !it.isNullOrBlank() }) {
    "Mint plugin coordinates must be provided together: " +
            "mintPluginOwner, mintPluginRepository, mintPluginTag, mintPluginAsset"
}

// The guilds shadow jar is the plugin under test; the guilds-test jar is the
// test plugin loaded alongside it. The shadow jar is wired as a task provider
// so every runServer boot builds and loads the current plugin rather than a
// stale jar left in build/libs.
val guildsPaperProject = project(":guilds-paper")
val guildsShadowJar = guildsPaperProject.tasks.named<Jar>("shadowJar")
val testPluginJar: java.io.File =
    layout.projectDirectory.dir("build/libs")
        .file("guilds-test-${project.version}.jar")
        .asFile

// squaremap is loaded as a plugin. Default to the upstream Java squaremap release
// (no Rust sidecar); set -PsquaremapLocalRust=true to use the locally-built
// Rust-backed jar (run/squaremapmap-paper-rust-local.jar) with its sidecar.
val useLocalRustSquaremap = providers.gradleProperty("squaremapLocalRust")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)
val squaremapJarFile: java.io.File =
    if (useLocalRustSquaremap.get()) {
        layout.projectDirectory.file("run/squaremap-paper-rust-local.jar").asFile
    } else {
        layout.projectDirectory.file("run/squaremap-paper-upstream.jar").asFile
    }

tasks.runServer {
    minecraftVersion("26.2")
    runDirectory.set(layout.projectDirectory.dir("run"))
    pluginJars(guildsShadowJar.flatMap { it.archiveFile }, testPluginJar, squaremapJarFile)
    dependsOn(guildsShadowJar, tasks.jar)
    downloadPlugins {
        if (mintPluginOwner != null) {
            github(
                    mintPluginOwner,
                    mintPluginRepository!!,
                    mintPluginTag!!,
                    mintPluginAsset!!,
            )
        }
        // MapGUI is a hard dependency of the Guilds plugin (plugin.yml `depend`).
        // run-paper downloads the release jar into run/plugins so the server boots.
        github("FloG99", "MapGUI", "v2.0.0", "MapGUI-2.0.0.jar")
    }
}

// The local Rust-backed squaremap is mandatory: pass backend binary and
// output root as JVM system properties. Current squaremap serves the SPA from
// SQUAREMAP_WEB_ROOT (plugin web dir) and tiles from SQUAREMAP_OUTPUT_ROOT
// (rust-output). syncSquaremapWebToRustOutput still copies the SPA into
// rust-output so a sidecar without web_root keeps working.
val sidecarPath = providers.gradleProperty("squaremapBackendBinary")
    .orElse(layout.projectDirectory.file("run/squaremap-server").asFile.absolutePath)
val backendOutputRoot = providers.gradleProperty("squaremapBackendOutputRoot")
    .orElse(layout.projectDirectory.dir("run/rust-output").asFile.absolutePath)
val backendWebRoot = providers.gradleProperty("squaremapBackendWebRoot")
    .orElse(layout.projectDirectory.dir("run/plugins/squaremap/web").asFile.absolutePath)
val localJar = squaremapJarFile

val ensureSquaremapWebAssets = tasks.register<Exec>("ensureSquaremapWebAssets") {
    group = "run"
    description = "Extract bundled squaremap web UI into run/plugins/squaremap/web on first run"
    val webDir = layout.projectDirectory.dir("run/plugins/squaremap/web")
    val pluginDir = layout.projectDirectory.dir("run/plugins/squaremap")
    doFirst {
        pluginDir.asFile.mkdirs()
    }
    commandLine(
        "unzip",
        "-qo",
        localJar.absolutePath,
        "web/*",
        "-d",
        pluginDir.asFile.absolutePath,
    )
    onlyIf { localJar.isFile && !webDir.asFile.resolve("index.html").isFile }
}

val syncSquaremapWeb = tasks.register<Exec>("syncSquaremapWebToRustOutput") {
    group = "run"
    description = "Copy squaremap web UI assets into rust-output (tiles/ excluded; sidecar renders PNGs live)"
    dependsOn(ensureSquaremapWebAssets)
    val webDir = layout.projectDirectory.dir("run/plugins/squaremap/web")
    val outputRoot = layout.projectDirectory.dir("run/rust-output")
    doFirst {
        outputRoot.asFile.mkdirs()
    }
    commandLine(
        "rsync", "-a",
        "--exclude", "tiles",
        webDir.asFile.absolutePath + "/",
        outputRoot.asFile.absolutePath + "/",
    )
    onlyIf { webDir.asFile.resolve("index.html").isFile }
}

// Optional stale-tile bootstrap: copies bundled web/tiles into rust-output and can
// mask a broken renderer. Off by default; enable only for temporary UI smoke tests:
//   ./gradlew :guilds-test:runServer -PsquaremapBootstrapStaleTiles=true
val bootstrapSquaremapTiles = providers.gradleProperty("squaremapBootstrapStaleTiles")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)
val bootstrapSquaremapTilesTask = tasks.register<Exec>("bootstrapSquaremapTilesIfEmpty") {
    group = "run"
    description = "TEMPORARY: seed rust-output/tiles from bundled web tiles (masks live rendering)"
    dependsOn(syncSquaremapWeb)
    val webTiles = layout.projectDirectory.dir("run/plugins/squaremap/web/tiles")
    val outputTiles = layout.projectDirectory.dir("run/rust-output/tiles")
    doFirst {
        outputTiles.asFile.mkdirs()
    }
    commandLine(
        "rsync", "-a",
        webTiles.asFile.absolutePath + "/",
        outputTiles.asFile.absolutePath + "/",
    )
    onlyIf {
        bootstrapSquaremapTiles.get() &&
            webTiles.asFile.isDirectory &&
            outputTiles.asFile.walkTopDown().none { it.isFile && it.extension == "png" }
    }
}

val syncSquaremapBackendManifest = tasks.register<Exec>("syncSquaremapBackendManifest") {
    group = "run"
    description = "Patch squaremap-backends.json in the local Rust jar to match run/squaremap-server"
    val patchScript = layout.projectDirectory.file("scripts/sync-squaremap-backend-manifest.py")
    inputs.file(localJar)
    inputs.file(sidecarPath)
    inputs.file(patchScript)
    outputs.file(localJar)
    onlyIf {
        localJar.isFile &&
            file(sidecarPath.get()).isFile &&
            patchScript.asFile.isFile
    }
    commandLine(
        "python3",
        patchScript,
        localJar,
        sidecarPath.get(),
    )
}

tasks.runServer {
    doFirst {
        require(guildsShadowJar.get().archiveFile.get().asFile.isFile) {
            "Guilds shadow jar missing: ${guildsShadowJar.get().archiveFile.get().asFile} — build it with ./gradlew :guilds-paper:shadowJar"
        }
        require(testPluginJar.isFile) {
            "Test plugin jar missing: $testPluginJar — build it with ./gradlew :guilds-test:jar"
        }
        require(squaremapJarFile.isFile) {
            "Local squaremap jar missing: $squaremapJarFile — build it with ./scripts/build-squaremap-local.sh"
        }
        if (useLocalRustSquaremap.get()) {
            require(file(sidecarPath.get()).isFile) {
                "Local squaremap backend missing: ${sidecarPath.get()} — build it with ./scripts/build-squaremap-local.sh"
            }
        }
    }
    if (useLocalRustSquaremap.get()) {
        systemProperty("squaremap.backendBinary", sidecarPath.get())
        systemProperty("squaremap.backendOutputRoot", backendOutputRoot.get())
        systemProperty("squaremap.backendWebRoot", backendWebRoot.get())
        dependsOn(syncSquaremapWeb, bootstrapSquaremapTilesTask, syncSquaremapBackendManifest)
    }
}
