// ARCHIVED: Guilds is integrated into the root azoth-territory plugin.
// Production sources live under ../../src/main/java/org/aincraft/towny/
// This directory is not a separately packaged Paper plugin.
tasks.register("check") {
    doLast {
        throw GradleException(
            "guilds is not a separate plugin product. Build the root project: ./gradlew shadowJar"
        )
    }
}
