plugins {
    // Auto-provisions missing JDK toolchains from the foojay JSON-based index.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
// api: public contracts + value models (model, decree, registry, permission/economy contracts)
// common: Paper-free shared implementation (persistence, economy, governance logic, web submodule)
// paper: the single Paper plugin (Bukkit glue + integrated Guilds subsystem)
include("api", "common", "paper")
