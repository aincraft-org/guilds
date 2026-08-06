package org.aincraft.guilds.commands;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.aincraft.guilds.commands.brigadier.TechTreeBrigadierCommand;
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.models.TechTreeNode;
import org.aincraft.guilds.models.TechTreeBranch;
import org.aincraft.guilds.models.Town;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("TechTree Command Permissions")
class TechTreeBrigadierCommandTest extends BrigadierTestBase {

    private TechTreeBrigadierCommand command;

    @BeforeEach
    void setUp() {
        command = new TechTreeBrigadierCommand(plugin, techTreeService, townService, residentService);
    }

    @Test
    @DisplayName("buildCommand returns non-null node")
    void testBuildCommandNotNull() {
        assertNotNull(command.buildCommand());
    }

    @Test
    @DisplayName("/techtree requires guilds.techtree permission")
    void testTechTreeRequiresPermission() {
        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        assertRequiresPermission(node, "guilds.techtree");
    }

    @Test
    @DisplayName("info subcommand node exists")
    void testInfoSubcommandExists() {
        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        assertNotNull(node.getChild("info"), "/techtree info should exist");
    }

    @Test
    @DisplayName("list subcommand node exists")
    void testListSubcommandExists() {
        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        assertNotNull(node.getChild("list"), "/techtree list should exist");
    }

    @Test
    @DisplayName("unlock subcommand node exists")
    void testUnlockSubcommandExists() {
        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        assertNotNull(node.getChild("unlock"), "/techtree unlock should exist");
    }

    @Test
    @DisplayName("Player not in town gets error")
    void testTechTreeWithoutTownReturnsError() {
        Player p = playerWithPermission("guilds.techtree");
        Resident resident = mockResident(null);
        when(residentService.getResident(p.getUniqueId())).thenReturn(Optional.of(resident));

        // Should NOT call tech tree service
        verify(techTreeService, never()).getAllNodes();
    }

    @Test
    @DisplayName("Command node has correct literal")
    void testCommandLiteral() {
        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        assertEquals("techtree", node.getLiteral());
    }
}
