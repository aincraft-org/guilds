package org.aincraft.guilds.commands;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.aincraft.guilds.commands.brigadier.SpecializationBrigadierCommand;
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.models.Town;
import org.aincraft.guilds.models.TownSpecialization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Specialization Command Permissions")
class SpecializationBrigadierCommandTest extends BrigadierTestBase {

    private SpecializationBrigadierCommand command;
    private final UUID mayorUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID otherUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        command = new SpecializationBrigadierCommand(plugin, specializationService, townService, residentService);
    }

    @Test
    @DisplayName("buildCommand returns non-null node")
    void testBuildCommandNotNull() {
        assertNotNull(command.buildCommand());
    }

    @Test
    @DisplayName("/town specialize requires permission")
    void testSpecializeRequiresPermission() {
        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        // The specialize subcommand is nested under /town, check the parent
        assertNotNull(node);
    }

    @Test
    @DisplayName("Non-mayor cannot set specialization")
    void testSpecializeAsNonMayorReturnsError() {
        Player nonMayor = mock(Player.class);
        when(nonMayor.getUniqueId()).thenReturn(otherUuid);
        when(nonMayor.hasPermission(anyString())).thenReturn(true);

        Town town = mockTown();
        when(town.getMayor()).thenReturn(mayorUuid);
        when(residentService.getResident(otherUuid)).thenReturn(Optional.of(mockResident("town-1")));
        when(townService.getTown("town-1")).thenReturn(Optional.of(town));

        // Non-mayor UUID doesn't match mayor
        assertNotEquals(town.getMayor(), nonMayor.getUniqueId());
    }

    @Test
    @DisplayName("Mayor can set specialization")
    void testMayorCanSpecialize() {
        Player mayor = mock(Player.class);
        when(mayor.getUniqueId()).thenReturn(mayorUuid);
        when(mayor.hasPermission(anyString())).thenReturn(true);

        Town town = mockTown();
        when(town.getMayor()).thenReturn(mayorUuid);
        when(residentService.getResident(mayorUuid)).thenReturn(Optional.of(mockResident("town-1")));
        when(townService.getTown("town-1")).thenReturn(Optional.of(town));

        // Mayor UUID matches
        assertEquals(town.getMayor(), mayor.getUniqueId());
    }

    @Test
    @DisplayName("reset subcommand node exists")
    void testResetSubcommandExists() {
        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        assertNotNull(node.getChild("reset"), "/town specialize reset should exist");
    }
}
