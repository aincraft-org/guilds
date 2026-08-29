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
    void mapCommandSupportsOptionalMapGuiFallback() {
        String pluginYml = read("guilds-paper/src/main/resources/plugin.yml");
        String paperPluginYml = read("guilds-paper/src/main/resources/paper-plugin.yml");
        String mapCommand = read("guilds-paper/src/main/java/org/aincraft/guilds/commands/brigadier/MapBrigadierCommand.java");

        assertTrue(pluginYml.contains("softdepend: [MapGUI, squaremap, WorldGuard, Mint, PlaceholderAPI]"));
        assertFalse(pluginYml.contains("depend: [MapGUI]"));
        assertTrue(paperPluginYml.contains("MapGUI:") && paperPluginYml.contains("required: false"));
        assertTrue(mapCommand.contains("isMapGuiPresent"));
        assertTrue(mapCommand.contains("MapRenderer"));
        assertTrue(mapCommand.contains("openAsciiMap(player, radius)"));
        assertTrue(mapCommand.contains("openAsciiMapAt(player, chunkX, chunkZ, radius)"));
        assertTrue(mapCommand.contains("renderCompactMap"));
        assertTrue(mapCommand.contains("renderMap"));
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
