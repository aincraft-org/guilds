plugins {
    // Auto-provisions missing JDK toolchains (e.g. guilds requires Java 26)
    // from the foojay JSON-based index.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "azoth-territory"

// Guilds (Towny-style town/nation/tech-tree plugin) merged in as a subproject.
include("guilds")
