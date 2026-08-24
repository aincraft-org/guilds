package org.aincraft.guilds.territory;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ensures live plugin startup wires guild storage through the composition root. */
class PluginStorageWiringTest {
    @Test
    void startBuildingsWiresRegistryStorageValidatorThroughGuildsServices() throws Exception {
        Path path = Path.of("src/main/java/org/aincraft/guilds/GuildsPlugin.java");
        assertTrue(Files.isRegularFile(path));
        String source = Files.readString(path);
        assertTrue(source.contains("guilds.wireStorage(facilities, governance, anchors)"));
    }
}
