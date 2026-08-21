package org.aincraft.guilds.commands;

import org.aincraft.guilds.storage.StorageFacilityOpener;
import org.aincraft.guilds.territory.building.BuildingListener;
import org.aincraft.guilds.territory.command.TerritoryCommand;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageCommandTest {
    @Test
    void guildAndTownAliasExposeLocalStorageCommand() {
        String guildCommand = read("paper/src/main/java/org/aincraft/guilds/commands/brigadier/GuildBrigadierCommand.java");
        String registry = read("paper/src/main/java/org/aincraft/guilds/commands/BrigadierCommandRegistry.java");

        assertTrue(guildCommand.contains("literal(\"storage\")"));
        assertTrue(guildCommand.contains("handleStorage"));
        assertTrue(guildCommand.contains("tryOpenAtLocation(player)"));
        assertTrue(guildCommand.contains("setStorageFacilityOpener"));
        assertTrue(registry.contains("Commands.literal(\"t\")"));
        assertTrue(registry.contains("redirect(guildCommand.buildCommand())"));
    }

    @Test
    void storageCommandNeverOpensWithoutLocalAnchorResolution() {
        String guildCommand = read("paper/src/main/java/org/aincraft/guilds/commands/brigadier/GuildBrigadierCommand.java");
        String opener = read("paper/src/main/java/org/aincraft/guilds/storage/StorageFacilityOpener.java");

        assertTrue(guildCommand.contains("StorageFacilityOpener opener = storageFacilityOpener"));
        assertTrue(opener.contains("tryOpenAtLocation(Player player)"));
        assertTrue(opener.contains("activeStorageAt("));
        assertTrue(opener.contains("You must stand at an active guild storage facility."));
    }

    @Test
    void buildingListenerUsesSharedStorageOpener() {
        String listener = read("paper/src/main/java/org/aincraft/guilds/territory/building/BuildingListener.java");
        String plugin = read("paper/src/main/java/org/aincraft/guilds/GuildsPlugin.java");
        String services = read("paper/src/main/java/org/aincraft/guilds/GuildsServices.java");

        assertTrue(listener.contains("StorageFacilityOpener"));
        assertTrue(listener.contains("storageOpener.tryOpen(player, facility)"));
        assertTrue(plugin.contains("guilds.getStorageFacilityOpener()"));
        assertTrue(services.contains("new StorageFacilityOpener("));
        assertTrue(services.contains("RegistryStorageFacilityAccessValidator"));
    }

    private static String read(String file) {
        try {
            Path cwd = Path.of(System.getProperty("user.dir"));
            Path direct = cwd.resolve(file);
            if (Files.isRegularFile(direct)) {
                return Files.readString(direct);
            }
            return Files.readString(cwd.resolve("..", file));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
