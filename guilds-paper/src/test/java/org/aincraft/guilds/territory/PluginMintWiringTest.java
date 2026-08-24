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
        String settings = read("settings.gradle.kts");
        String props = read("gradle.properties");
        assertTrue(settings.contains("maven.pkg.github.com/aincraft-org/mint"));
        assertTrue(build.contains("mintApiVersion"));
        assertTrue(props.contains("mintApiVersion="));
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
    void runServerUsesExplicitMintGithubCoordinates() {
        String build = read("guilds-test/build.gradle.kts");
        assertTrue(build.contains("mintPluginOwner"));
        assertTrue(build.contains("mintPluginRepository"));
        assertTrue(build.contains("mintPluginTag"));
        assertTrue(build.contains("mintPluginAsset"));
        assertTrue(build.contains("github("));
    }

    @Test
    void runServerRejectsPartialMintCoordinates() {
        String build = read("guilds-test/build.gradle.kts");
        assertTrue(build.contains("Mint plugin coordinates must be provided together"));
        assertTrue(build.contains("mintPluginOwner"));
        assertTrue(build.contains("mintPluginAsset"));
    }

    @Test
    void runServerDoesNotInventMintCoordinates() {
        String build = read("guilds-test/build.gradle.kts");
        assertFalse(build.contains("github(\"aincraft-org\""));
        assertFalse(build.contains("mint-paper"));
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
