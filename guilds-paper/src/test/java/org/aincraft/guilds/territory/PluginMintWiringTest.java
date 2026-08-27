package org.aincraft.guilds.territory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PluginMintWiringTest {
    @Test
    void usesGitHubPackagesAndPinnedMintVersion() {
        String build = read("guilds-paper/build.gradle.kts");
        String paperPlugin = read("guilds-paper/src/main/resources/paper-plugin.yml");
        String settings = read("settings.gradle.kts");
        String props = read("gradle.properties");
        assertTrue(settings.contains("maven.pkg.github.com/aincraft-org/mint"));
        assertTrue(build.contains("mintApiVersion"));
        assertTrue(props.contains("mintApiVersion="));
        assertTrue(props.contains("mintPaperVersion="));
        // mint-api must be compileOnly (not implementation/shaded) so MintClientReceiver
        // class identity matches between Guilds and the Mint plugin at runtime.
        assertTrue(build.contains("compileOnly(\"dev.mintychochip.mint:mint-api"));
        assertFalse(build.contains("implementation(\"dev.mintychochip.mint:mint-api"));
        // paper-plugin.yml must declare Mint with join-classpath for shared classloader.
        assertTrue(paperPlugin.contains("Mint:"));
        assertTrue(paperPlugin.contains("join-classpath: true"));
    }

    @Test
    void mintConfigFailsClosedWithoutBinding() {
        String s = read("guilds-paper/src/main/java/org/aincraft/guilds/GuildsPlugin.java");
        assertTrue(s.contains("Mode.MINT"));
        assertTrue(s.contains("bindMintClient"));
        assertTrue(s.contains("MintClientReceiver.class"));
        assertTrue(s.contains("setAsyncSettlement"));
        assertTrue(s.contains("getServicesManager().register"));
    }

    @Test
    void guildServicesAcceptTrustedMintRail() {
        String s = read("guilds-paper/src/main/java/org/aincraft/guilds/GuildsServices.java");
        assertTrue(s.contains("MintClientLease"));
        assertTrue(s.contains("withMintLease"));
    }

    @Test
    void runServerResolvesMintPaperFromMaven() {
        String build = read("guilds-test/build.gradle.kts");
        String props = read("gradle.properties");
        assertTrue(build.contains("mintPlugin"));
        assertTrue(build.contains("dev.mintychochip.mint:mint-paper"));
        assertTrue(props.contains("mintPaperVersion="));
    }

    @Test
    void runServerWritesMintConfigsBeforeBoot() {
        String build = read("guilds-test/build.gradle.kts");
        assertTrue(build.contains("writeMintConfigs"));
        assertTrue(build.contains("mode: MINT"));
        assertTrue(build.contains("mint:coins"));
        assertTrue(build.contains("plugin: Guilds"));
    }

    @Test
    void runServerWiresMintJarAndConfigsIntoPluginJars() {
        String build = read("guilds-test/build.gradle.kts");
        assertTrue(build.contains("mintPaperJarFile"));
        assertTrue(build.contains("pluginJars("));
        assertTrue(build.contains("dependsOn(guildsShadowJar, tasks.jar, writeMintConfigs)"));
        assertFalse(build.contains("github(\"aincraft-org\""));
    }

    @Test
    void runServerRequiresLocalRustSquaremap() {
        String build = read("guilds-test/build.gradle.kts");
        String props = read("gradle.properties");
        assertTrue(build.contains("run/squaremap-paper-rust-local.jar"));
        assertTrue(build.contains("run/squaremap-server"));
        assertTrue(build.contains("build-squaremap-local.sh"));
        assertFalse(build.contains("verifySquaremapPlugin"));
        assertFalse(build.contains("squaremapPaperMc26JarSha512"));
        assertFalse(build.contains("releases/download/v1.3.15/squaremap-paper"));
        assertFalse(props.contains("squaremapPaperMc26JarSha512"));
        assertFalse(build.contains("squaremapLocalJar"));
    }

    @Test
    void movementListenerReceivesTerritoryRegistry() {
        String s = read("guilds-paper/src/main/java/org/aincraft/guilds/listeners/PlayerMovementListener.java");
        assertTrue(s.contains("TerritoryRegistry"));
        assertTrue(s.contains("sendTitle"));
    }

    private static String read(String f) {
        try {
            return Files.readString(Path.of(System.getProperty("user.dir")).resolve("..", f));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
