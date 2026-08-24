import java.io.FileOutputStream
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

plugins {
    id("io.github.goooler.shadow") version "8.1.8"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

description = "Guilds Paper plugin — Bukkit glue, listeners, commands, and the integrated Guilds subsystem"

dependencies {
    implementation(project(":guilds-api"))
    implementation(project(":guilds-common"))
    implementation("dev.mintychochip.mint:mint-api:${property("mintApiVersion")}")
    compileOnly("io.papermc.paper:paper-api:26.2.build.111-stable")
    testImplementation("dev.mintychochip.mint:mint-api:${property("mintApiVersion")}")

    compileOnly("io.papermc.paper:paper-api:26.2.build.111-stable")
    // squaremap integration (renders territory polygons). squaremap 1.3.15 targets
    // Minecraft 26.2 — matches the paper-api version above. compileOnly: the
    // squaremap jar is provided by the server (downloaded by the runServer task).
    compileOnly("xyz.jpenilla:squaremap-api:1.3.15")
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

// Local test server: ./gradlew :guilds-paper:runServer
// Boots Paper 26.2 with the guilds shadow jar plus the squaremap
// 1.3.15 Paper jar (pinned GitHub release asset) loaded as plugins. squaremap
// serves its live web map on http://localhost:8080 by default.
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

// Optional: use a locally-built squaremap jar instead of the pinned upstream release.
// ./gradlew :guilds-paper:runServer -PsquaremapLocalJar=/path/to/squaremap-paper.jar
val squaremapLocalJar = providers.gradleProperty("squaremapLocalJar").orNull

// The squaremap Paper jar loaded by runServer. By default it is downloaded from the
// pinned upstream GitHub release and SHA-512 verified. Pass -PsquaremapLocalJar to
// load a locally-built jar (e.g. a fork) instead, skipping the download and hash check.
val squaremapJarFile: java.io.File = if (squaremapLocalJar != null) {
    val f = file(squaremapLocalJar)
    require(f.isFile) { "squaremapLocalJar does not point to a file: $f" }
    logger.lifecycle("runServer: using local squaremap jar: $f")
    f
} else {
    val squaremapUrl = "https://github.com/jpenilla/squaremap/releases/download/v1.3.15/squaremap-paper-mc26.2-1.3.15.jar"
    val squaremapFile = layout.buildDirectory.file("run-paper-plugins/squaremap-paper-mc26.2-1.3.15.jar")
    val squaremapSha512 = providers.gradleProperty("squaremapPaperMc26JarSha512").orNull
            ?.takeIf { it.isNotBlank() }
            ?: throw GradleException("squaremapPaperMc26JarSha512 must be set in gradle.properties")

    fun sha512(file: java.io.File): String {
        val digest = MessageDigest.getInstance("SHA-512")
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(8192)
            var n: Int
            while (input.read(buf).also { n = it } > 0) {
                digest.update(buf, 0, n)
            }
        }
        return BigInteger(1, digest.digest()).toString(16).padStart(128, '0')
    }

    tasks.register("verifySquaremapPlugin") {
        group = "verification"
        description = "Downloads and SHA-512 verifies the squaremap Paper plugin"
        inputs.property("sha512", squaremapSha512)
        outputs.file(squaremapFile)
        outputs.upToDateWhen { false }
        doLast {
            val dest = squaremapFile.get().asFile
            dest.parentFile.mkdirs()

            if (dest.exists() && sha512(dest) == squaremapSha512) {
                logger.lifecycle("squaremap plugin already present and SHA-512 verified")
                return@doLast
            }

            val conn = URI.create(squaremapUrl).toURL().openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/octet-stream")
            conn.connectTimeout = 30000
            conn.readTimeout = 60000
            conn.instanceFollowRedirects = true
            conn.connect()
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw GradleException("squaremap download failed: HTTP ${conn.responseCode} from $squaremapUrl")
            }
            val contentLength = conn.contentLengthLong
            if (contentLength <= 0) {
                throw GradleException("squaremap download returned empty or unknown Content-Length")
            }

            conn.inputStream.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output, bufferSize = 8192)
                }
            }
            conn.disconnect()

            val hash = sha512(dest)
            if (hash != squaremapSha512) {
                dest.delete()
                throw GradleException("squaremap SHA-512 mismatch: expected $squaremapSha512, got $hash")
            }
        }
    }

    tasks.runServer {
        dependsOn(tasks.named("verifySquaremapPlugin"))
    }
    squaremapFile.get().asFile
}

tasks.runServer {
    minecraftVersion("26.2")
    runDirectory.set(layout.projectDirectory.dir("run"))
    pluginJars(squaremapJarFile)
    downloadPlugins {
        if (mintPluginOwner != null) {
            github(
                    mintPluginOwner,
                    mintPluginRepository!!,
                    mintPluginTag!!,
                    mintPluginAsset!!,
            )
        }
    }
}

// When using a local squaremap jar with a Rust backend, pass the backend binary
// path and output root as system properties. These can be overridden with
// -PsquaremapBackendBinary and -PsquaremapBackendOutputRoot.
if (squaremapLocalJar != null) {
    val backendBinary = providers.gradleProperty("squaremapBackendBinary").orNull
    val backendOutputRoot = providers.gradleProperty("squaremapBackendOutputRoot")
            .orElse(layout.projectDirectory.dir("run/rust-output").asFile.absolutePath)
    if (backendBinary != null) {
        tasks.runServer {
            systemProperty("squaremap.backendBinary", file(backendBinary).absolutePath)
            systemProperty("squaremap.backendOutputRoot", backendOutputRoot.get())
        }
    }
}
