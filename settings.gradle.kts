pluginManagement {
    includeBuild("../plugin-multiplexer/network")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
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
// guilds-api: public contracts + value models (model, decree, registry, permission/economy contracts)
// guilds-common: Paper-free shared implementation (persistence, economy, governance logic, web submodule)
// guilds-paper: the single Paper plugin (Bukkit glue + integrated Guilds subsystem)
// guilds-test: run-paper harness booting Paper with the guilds shadow jar as the test plugin
include("guilds-api", "guilds-common", "guilds-paper", "guilds-test")
