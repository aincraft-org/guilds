package org.aincraft.guilds.commands;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapCommandTest {

    @Test
    void mapSubcommandWiredUnderGuildAndGuildsParents() {
        String guildCommand = read("guilds-paper/src/main/java/org/aincraft/guilds/commands/brigadier/GuildBrigadierCommand.java");
        String guildsGeneral = read("guilds-paper/src/main/java/org/aincraft/guilds/commands/brigadier/GuildsGeneralBrigadierCommand.java");
        String mapCommand = read("guilds-paper/src/main/java/org/aincraft/guilds/commands/brigadier/MapBrigadierCommand.java");

        assertTrue(guildCommand.contains("mapCommand.buildCommand()"));
        assertTrue(guildsGeneral.contains("mapCommand.buildCommand()"));
        assertTrue(mapCommand.contains("Commands.literal(\"map\")"));
        assertTrue(mapCommand.contains("literal(\"compact\")"));
        assertTrue(mapCommand.contains("literal(\"full\")"));
        assertTrue(mapCommand.contains("literal(\"here\")"));
        assertTrue(mapCommand.contains("openMap(player, GuildClaimScreen.DEFAULT_RADIUS)"));
        assertTrue(mapCommand.contains("openMap(player, GuildClaimScreen.COMPACT_RADIUS)"));
        assertTrue(mapCommand.contains("IntegerArgumentType.getInteger(ctx, \"x\")"));
        assertTrue(mapCommand.contains("IntegerArgumentType.getInteger(ctx, \"z\")"));
        assertTrue(mapCommand.contains("openMapAt(player, chunkX, chunkZ"));
    }

    @Test
    void registryRemovesStandaloneMapRootsAndAddsGAlias() {
        String registry = read("guilds-paper/src/main/java/org/aincraft/guilds/commands/BrigadierCommandRegistry.java");

        assertTrue(registry.contains("Commands.literal(\"g\")"));
        assertTrue(registry.contains("redirect(guildCommand.buildCommand())"));
        assertFalse(registry.contains("guildsmap"));
        assertFalse(registry.contains("mapCommand.buildCommand()"));
        assertFalse(registry.contains("Commands.literal(\"map\")"));
    }

    @Test
    void mapCommandRequiresMapGuiDependency() {
        String pluginYml = read("guilds-paper/src/main/resources/plugin.yml");
        String mapCommand = read("guilds-paper/src/main/java/org/aincraft/guilds/commands/brigadier/MapBrigadierCommand.java");
        String plugin = read("guilds-paper/src/main/java/org/aincraft/guilds/GuildsPlugin.java");

        assertTrue(pluginYml.contains("depend: [MapGUI]"));
        assertFalse(pluginYml.contains("softdepend: [WorldEdit, WorldGuard, triumph-gui, squaremap, MapGUI]"));
        assertFalse(pluginYml.contains("WorldEdit"));
        assertFalse(pluginYml.contains("triumph-gui"));
        // WorldGuard is a deliberate, current softdepend (TerritoryWorldGuardBridge
        // mirrors territories into real WG regions) — unlike WorldEdit/triumph-gui,
        // it must stay.
        assertTrue(pluginYml.contains("softdepend: [squaremap, WorldGuard, Mint, PlaceholderAPI]"));
        assertTrue(mapCommand.contains("MapGui.get()"));
        assertTrue(mapCommand.contains("GuildClaimScreen"));
        assertFalse(mapCommand.contains("MapFollowTask"));
        assertFalse(mapCommand.contains("MapRenderer"));
        assertFalse(mapCommand.contains("MapGuiOpener"));
        assertFalse(mapCommand.contains("NOT_AVAILABLE"));
        assertFalse(mapCommand.contains("isAvailable"));
        assertFalse(plugin.contains("MapFollowTask.stop"));
        assertFalse(plugin.contains("MapGuiOpener"));
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
