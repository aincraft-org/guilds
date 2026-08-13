pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement { repositories {
    maven { url = uri("/tmp/aincraft-mint/build/maven-repo") }
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/aincraft-org/mint")
        credentials {
            username = System.getenv("GITHUB_ACTOR") ?: providers.gradleProperty("gpr.user").orNull ?: ""
            password = System.getenv("GITHUB_TOKEN") ?: providers.gradleProperty("gpr.key").orNull ?: ""
        }
    }
    mavenCentral()
} }
// api: public contracts + value models (model, decree, registry, permission/economy contracts)
// common: Paper-free shared implementation (persistence, economy, governance logic, web submodule)
// paper: the single Paper plugin (Bukkit glue + integrated Guilds subsystem)
include("api", "common", "paper")
