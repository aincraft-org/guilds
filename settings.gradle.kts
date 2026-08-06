plugins {
    // Auto-provisions missing JDK toolchains from the foojay JSON-based index.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "azoth-territory"

// Guilds production sources live under root src/ (single Paper plugin).
// The historical guilds/ tree is not a separately packaged plugin product.
