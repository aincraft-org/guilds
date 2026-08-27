package org.aincraft.guilds.commands;
import static org.junit.jupiter.api.Assertions.assertTrue; import java.nio.file.Files;
import java.nio.file.Path; import org.junit.jupiter.api.Test;
class GuildBankCommandTest {
    @Test
    void commandSurfaceUsesMintRailAndPermissions() {
        String s = read("guilds-paper/src/main/java/org/aincraft/guilds/commands/brigadier/GuildBrigadierCommand.java");
        assertTrue(s.contains("literal(\"bank\")"));
        assertTrue(s.contains("literal(\"deposit\")"));
        assertTrue(s.contains("literal(\"withdraw\")"));
        assertTrue(s.contains("hasPermission(player.getUniqueId(), permission"));
        assertTrue(s.contains("runTask(plugin"));
    }

    @Test
    void guildInfoShowsMintBankBalanceAndCapacityLimit() {
        String s = read("guilds-paper/src/main/java/org/aincraft/guilds/commands/brigadier/GuildBrigadierCommand.java");
        assertTrue(s.contains("Guild Bank:"));
        assertTrue(s.contains("guildBalance("));
        assertTrue(s.contains("limitFor("));
        assertTrue(s.contains("handleOwnInfo"));
        assertTrue(s.contains("handleGuildInfo"));
        assertTrue(s.contains("appendGuildBank"));
        assertTrue(!s.contains("§fBalance:"));
    }

    private static String read(String f) {
        try {
            Path cwd = Path.of(System.getProperty("user.dir"));
            Path direct = cwd.resolve(f);
            if (Files.isRegularFile(direct)) {
                return Files.readString(direct);
            }
            return Files.readString(cwd.resolve("..", f));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
