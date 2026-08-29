package org.aincraft.guilds.commands.brigadier;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildBrigadierCommandTest {
    @Test
    void spawnBypassIsRemovedButSetSpawnRemains() throws Exception {
        String source = read("guilds-paper/src/main/java/org/aincraft/guilds/commands/brigadier/GuildBrigadierCommand.java");

        assertFalse(source.contains("Commands.literal(\"spawn\")"));
        assertFalse(source.contains("handleOwnSpawn"));
        assertFalse(source.contains("handleGuildSpawn"));
        assertFalse(source.contains("guilds.guild.spawn"));
        assertTrue(source.contains("Commands.literal(\"setspawn\")"));
        assertTrue(source.contains("guildService.setGuildSpawn"));
    }

    private static String read(String file) throws Exception {
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path direct = cwd.resolve(file);
        if (Files.isRegularFile(direct)) {
            return Files.readString(direct);
        }
        return Files.readString(cwd.resolve("..", file));
    }
}
