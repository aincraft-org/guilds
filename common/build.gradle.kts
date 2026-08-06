plugins {
    `java-library`
}

description = "Azoth Territory shared implementation — Paper-free persistence, economy, governance logic, and web submodule"

dependencies {
    api(project(":api"))

    // Domain persistence + web payloads; Paper ships Gson at runtime but we
    // shade it for self-containment (Guilds web uses it too).
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
