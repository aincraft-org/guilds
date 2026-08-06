plugins {
    `java-library`
}

description = "Azoth Territory embedded map UI and REST API — Paper-free JDK HttpServer submodule"

dependencies {
    // Registry, repository, and TerritoryJson live in common (api is transitive).
    api(project(":common"))

    // REST payloads and handler JSON shaping; also needed at test time.
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
