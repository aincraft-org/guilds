pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement {
    repositories {
        if (providers.gradleProperty("useLocalMintRepo").orNull == "true") {
            maven { url = uri("/tmp/aincraft-mint/build/maven-repo") }
        }
        val actor = System.getenv("MINT_PACKAGES_ACTOR") ?: System.getenv("GITHUB_ACTOR")
        val token = System.getenv("MINT_PACKAGES_TOKEN") ?: System.getenv("GITHUB_TOKEN")
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/aincraft-org/mint")
            credentials {
                username = actor?.takeIf { it.isNotBlank() } ?: ""
                password = token?.takeIf { it.isNotBlank() } ?: ""
            }
        }
        mavenCentral()
    }
}
// api: public contracts + value models (model, registry, permission/economy contracts)
// common: Paper-free shared implementation (persistence, economy, governance logic, web submodule)
// paper: the single Paper plugin (Bukkit glue + integrated Guilds subsystem)
include("api", "common", "paper")
