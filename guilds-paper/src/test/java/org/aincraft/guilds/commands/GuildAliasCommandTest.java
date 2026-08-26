package org.aincraft.guilds.commands;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildAliasCommandTest {
    @Test
    void gRootAliasRedirectsIntoGuildTree() {
        String registry = read("guilds-paper/src/main/java/org/aincraft/guilds/commands/BrigadierCommandRegistry.java");

        assertTrue(registry.contains("Commands.literal(\"g\")"));
        assertTrue(registry.contains("redirect(guildCommand.buildCommand())"));
    }

    @Test
    void newSubcommandMirrorsCreateSurface() {
        String guildCommand = read("guilds-paper/src/main/java/org/aincraft/guilds/commands/brigadier/GuildBrigadierCommand.java");

        assertTrue(guildCommand.contains("literal(\"create\")"));
        assertTrue(guildCommand.contains("literal(\"new\")"));
        assertTrue(guildCommand.contains("hasPermission(\"guilds.guild.create\")"));
        assertTrue(guildCommand.contains("executes(this::handleCreate)"));
        assertTrue(guildCommand.contains("/g new"));
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
