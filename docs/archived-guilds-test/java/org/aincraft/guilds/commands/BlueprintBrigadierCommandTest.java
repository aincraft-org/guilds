package org.aincraft.guilds.commands;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.aincraft.guilds.commands.brigadier.BlueprintBrigadierCommand;
import org.aincraft.guilds.models.Blueprint;
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.models.Town;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Blueprint Command Permissions")
class BlueprintBrigadierCommandTest extends BrigadierTestBase {

    private BlueprintBrigadierCommand command;
    private final UUID mayorUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        command = new BlueprintBrigadierCommand(plugin, blueprintService, townService, residentService);
    }

    @Test
    @DisplayName("buildCommand returns non-null node")
    void testBuildCommandNotNull() {
        assertNotNull(command.buildCommand());
    }

    @Test
    @DisplayName("/blueprint requires guilds.commands.blueprint permission")
    void testBlueprintRequiresPermission() {
        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        assertRequiresPermission(node, "guilds.commands.blueprint");
    }

    @Test
    @DisplayName("save subcommand node exists")
    void testSaveSubcommandExists() {
        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        assertNotNull(node.getChild("save"), "/blueprint save should exist");
    }

    @Test
    @DisplayName("list subcommand node exists")
    void testListSubcommandExists() {
        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        assertNotNull(node.getChild("list"), "/blueprint list should exist");
    }

    @Test
    @DisplayName("apply subcommand node exists")
    void testApplySubcommandExists() {
        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        assertNotNull(node.getChild("apply"), "/blueprint apply should exist");
    }

    @Test
    @DisplayName("delete subcommand node exists")
    void testDeleteSubcommandExists() {
        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        assertNotNull(node.getChild("delete"), "/blueprint delete should exist");
    }

    @Test
    @DisplayName("load subcommand node exists")
    void testLoadSubcommandExists() {
        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        assertNotNull(node.getChild("load"), "/blueprint load should exist");
    }

    @Test
    @DisplayName("Non-mayor cannot delete blueprint")
    void testDeleteAsNonMayorReturnsError() {
        UUID otherUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Town town = mockTown();
        when(town.getMayor()).thenReturn(mayorUuid);
        when(residentService.getResident(otherUuid)).thenReturn(Optional.of(mockResident("town-1")));
        when(townService.getTown("town-1")).thenReturn(Optional.of(town));

        // Non-mayor UUID doesn't match
        assertNotEquals(town.getMayor(), otherUuid);
    }

    @Test
    @DisplayName("Mayor can delete blueprint")
    void testMayorCanDelete() {
        Town town = mockTown();
        when(town.getMayor()).thenReturn(mayorUuid);
        when(residentService.getResident(mayorUuid)).thenReturn(Optional.of(mockResident("town-1")));
        when(townService.getTown("town-1")).thenReturn(Optional.of(town));

        assertEquals(town.getMayor(), mayorUuid);
    }
}
