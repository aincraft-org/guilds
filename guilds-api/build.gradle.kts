plugins {
    `java-library`
}

description = "Guilds public API — value models, decree effects, registries, and contracts"

dependencies {
    // Serialization of decree effects (DecreeEffectsCodec); implementation-scope only,
    // the published API surface stays free of external types.
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
