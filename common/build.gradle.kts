plugins {
    `java-library`
}

description = "Azoth Territory shared implementation — Paper-free persistence, economy, governance logic, and web submodule"

dependencies {
    api(project(":api"))

    // Domain persistence + territory web payloads; Paper ships Gson at runtime
    // but we shade it for self-containment.
    implementation("com.google.code.gson:gson:2.11.0")

    // Remote SQL stores. Both drivers are shaded so deployments can select either backend.
    api("com.zaxxer:HikariCP:5.1.0")
    implementation("org.postgresql:postgresql:42.7.13")
    implementation("com.mysql:mysql-connector-j:9.4.0")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
