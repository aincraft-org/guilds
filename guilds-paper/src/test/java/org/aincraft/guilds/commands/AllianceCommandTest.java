package org.aincraft.guilds.commands;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AllianceCommandTest {

    @Test
    void createRequiresTargetGuildAndAcceptSubcommand() {
        String command = read("guilds-paper/src/main/java/org/aincraft/guilds/commands/brigadier/AllianceBrigadierCommand.java");

        assertTrue(command.contains("literal(\"create\")"));
        assertTrue(command.contains("argument(\"guild\""));
        assertTrue(command.contains("literal(\"accept\")"));
        assertTrue(command.contains("executes(this::handleAccept)"));
        assertTrue(command.contains("proposalStore.propose("));
        assertTrue(command.contains("proposalStore.accept("));
    }

    @Test
    void requirementCommandPersistsMinGuilds() {
        String command = read("guilds-paper/src/main/java/org/aincraft/guilds/commands/brigadier/AllianceBrigadierCommand.java");
        String config = read("guilds-paper/src/main/resources/config.yml");

        assertTrue(config.contains("min-guilds: 2"));
        assertTrue(command.contains("literal(\"requirement\")"));
        assertTrue(command.contains("executes(this::handleRequirement)"));
        assertTrue(command.contains("plugin.getConfig().set(\"alliance.min-guilds\""));
        assertTrue(command.contains("plugin.saveConfig()"));
        assertTrue(command.contains("guilds.admin.alliance"));
    }

    @Test
    void registryAddsAAliasAndKeepsAllianceAndN() {
        String registry = read("guilds-paper/src/main/java/org/aincraft/guilds/commands/BrigadierCommandRegistry.java");

        assertTrue(registry.contains("commands.register(allianceCommand.buildCommand())"));
        assertTrue(registry.contains("Commands.literal(\"n\")"));
        assertTrue(registry.contains("Commands.literal(\"a\")"));
        assertTrue(registry.contains("redirect(allianceCommand.buildCommand())"));
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
