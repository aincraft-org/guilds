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
    void mapCommandUsesLazyMapGuiOpenerBoundary() {
        String mapCommand = read("guilds-paper/src/main/java/org/aincraft/guilds/commands/brigadier/MapBrigadierCommand.java");
        String opener = read("guilds-paper/src/main/java/org/aincraft/guilds/gui/MapGuiOpener.java");
        String plugin = read("guilds-paper/src/main/java/org/aincraft/guilds/GuildsPlugin.java");

        assertTrue(mapCommand.contains("MapGuiOpener"));
        assertFalse(mapCommand.contains("de.flog99.mapgui"));
        assertFalse(mapCommand.contains("GuildClaimScreen"));
        assertFalse(mapCommand.contains("MapFollowTask"));
        assertTrue(opener.contains("Class.forName"));
        assertTrue(opener.contains("MapGuiRuntime"));
        assertTrue(opener.contains("stopIfPresent"));
        assertFalse(plugin.contains("MapFollowTask"));
        assertTrue(plugin.contains("MapGuiOpener.stopIfPresent"));
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
