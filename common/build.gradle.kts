plugins {
    `java-library`
}

description = "Azoth Territory shared implementation — Paper-free persistence, economy, governance logic, and web submodule"

dependencies {
    api(project(":api"))

    // Domain persistence + territory web payloads; Paper ships Gson at runtime
    // but we shade it for self-containment.
    implementation("com.google.code.gson:gson:2.11.0")

    // Remote PostgreSQL store for territory persistence. HikariCP is declared
    // as `api` because the paper module's guilds subsystem compiles against it.
    api("com.zaxxer:HikariCP:5.1.0")
    implementation("org.postgresql:postgresql:42.7.13")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
