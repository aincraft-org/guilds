package org.aincraft.guilds.commands;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildTopCommandTest {

    @Test
    void guildCommandExposesTopAlliances() {
        String command = read("guilds-paper/src/main/java/org/aincraft/guilds/commands/brigadier/GuildBrigadierCommand.java");

        assertTrue(command.contains("literal(\"top\")"));
        assertTrue(command.contains("literal(\"alliances\")"));
        assertTrue(command.contains("executes(this::handleTopAlliances)"));
        assertTrue(command.contains("TopRankings.alliancesByGuildCount"));
    }

    @Test
    void guildsGeneralTopKeepsExistingCriteriaAndAddsAlliances() {
        String command = read("guilds-paper/src/main/java/org/aincraft/guilds/commands/brigadier/GuildsGeneralBrigadierCommand.java");

        assertTrue(command.contains("literal(\"residents\")"));
        assertTrue(command.contains("literal(\"guilds\")"));
        assertTrue(command.contains("literal(\"land\")"));
        assertTrue(command.contains("literal(\"alliances\")"));
        assertTrue(command.contains("executes(this::handleTopAlliances)"));
        assertTrue(command.contains("TopRankings.alliancesByGuildCount"));
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
