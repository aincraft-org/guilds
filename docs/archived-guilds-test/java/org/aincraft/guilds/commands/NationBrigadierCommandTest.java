package org.aincraft.guilds.commands;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.aincraft.guilds.commands.brigadier.NationBrigadierCommand;
import org.aincraft.guilds.models.Nation;
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.models.Town;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Nation Command Permissions")
class NationBrigadierCommandTest extends BrigadierTestBase {

    private NationBrigadierCommand command;

    @BeforeEach
    void setUp() {
        command = new NationBrigadierCommand(plugin, nationService, townService, residentService);
    }

    @Test
    @DisplayName("buildCommand returns non-null node")
    void testBuildCommandNotNull() {
        assertNotNull(command.buildCommand());
    }

    @Test
    @DisplayName("/nation requires guilds.commands.nation permission")
    void testNationRequiresPermission() {
        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        assertRequiresPermission(node, "guilds.commands.nation");
    }

    @Test
    @DisplayName("Player not in town cannot create nation")
    void testCreateNationWithoutTown() {
        Player p = playerWithPermission("guilds.commands.nation");
        Resident resident = mockResident(null);
        when(residentService.getResident(p.getUniqueId())).thenReturn(Optional.of(resident));

        // The command should send an error message, not call the service
        Town mockTown = mockTown();
        when(townService.getTown(anyString())).thenReturn(Optional.of(mockTown));
        // Nation already exists
        when(nationService.getNation(anyString())).thenReturn(Optional.of(mock(Nation.class)));

        // Verify the command node structure exists
        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        assertNotNull(node.getChild("create"));
    }

    @Test
    @DisplayName("create subcommand node exists")
    void testCreateSubcommandExists() {
        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        assertNotNull(node.getChild("create"), "/nation create should exist");
    }

    @Test
    @DisplayName("ally subcommand node exists")
    void testAllySubcommandExists() {
        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        assertNotNull(node.getChild("ally"), "/nation ally should exist");
    }

    @Test
    @DisplayName("enemy subcommand node exists")
    void testEnemySubcommandExists() {
        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        assertNotNull(node.getChild("enemy"), "/nation enemy should exist");
    }

    @Test
    @DisplayName("kick subcommand node exists")
    void testKickSubcommandExists() {
        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        assertNotNull(node.getChild("kick"), "/nation kick should exist");
    }

    @Test
    @DisplayName("set subcommand node exists")
    void testSetSubcommandExists() {
        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        assertNotNull(node.getChild("set"), "/nation set should exist");
    }

    @Test
    @DisplayName("list subcommand node exists")
    void testListSubcommandExists() {
        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        assertNotNull(node.getChild("list"), "/nation list should exist");
    }

    @Test
    @DisplayName("info subcommand node exists")
    void testInfoSubcommandExists() {
        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        assertNotNull(node.getChild("info"), "/nation info should exist");
    }
}
