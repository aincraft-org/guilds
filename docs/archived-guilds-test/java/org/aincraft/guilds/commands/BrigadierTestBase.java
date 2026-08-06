package org.aincraft.guilds.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.aincraft.guilds.GuildsPlugin;
import org.aincraft.guilds.models.*;
import org.aincraft.guilds.services.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.mockito.Mockito.*;

/**
 * Base class for Brigadier command tests.
 * Provides mocked services and permission helper methods.
 */
@ExtendWith(MockitoExtension.class)
public abstract class BrigadierTestBase {

    @Mock protected GuildsPlugin plugin;
    @Mock protected TownService townService;
    @Mock protected ResidentService residentService;
    @Mock protected NationService nationService;
    @Mock protected TechTreeService techTreeService;
    @Mock protected ChatService chatService;
    @Mock protected SpecializationService specializationService;
    @Mock protected QuestService questService;
    @Mock protected BlueprintService blueprintService;
    @Mock protected Player player;

    @BeforeEach
    void setUpBase() {
        lenient().when(player.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        lenient().when(player.getName()).thenReturn("TestPlayer");
    }

    protected Player playerWithPermission(String... permissions) {
        for (String perm : permissions) {
            when(player.hasPermission(perm)).thenReturn(true);
        }
        return player;
    }

    protected Player playerWithoutPermission(String... permissions) {
        for (String perm : permissions) {
            when(player.hasPermission(perm)).thenReturn(false);
        }
        return player;
    }

    protected MockCommandSourceStack source(Player player) {
        return new MockCommandSourceStack(player);
    }

    /**
     * Test that a command node requires a specific permission.
     */
    protected void assertRequiresPermission(LiteralCommandNode<CommandSourceStack> node, String permission) {
        // Player with permission should pass
        Player authorized = playerWithPermission(permission);
        assert node.getRequirement().test(source(authorized)) : "Should pass with permission: " + permission;

        // Player without permission should fail
        Player unauthorized = playerWithoutPermission(permission);
        assert !node.getRequirement().test(source(unauthorized)) : "Should fail without permission: " + permission;
    }

    /**
     * Helper to create a mock Town.
     */
    protected Town mockTown() {
        Town town = mock(Town.class);
        when(town.getId()).thenReturn("town-1");
        when(town.getName()).thenReturn("TestTown");
        when(town.getMayor()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        when(town.getLevel()).thenReturn(5);
        when(town.getTechPoints()).thenReturn(42);
        return town;
    }

    /**
     * Helper to create a mock Resident in a town.
     */
    protected Resident mockResident(String townId) {
        Resident resident = mock(Resident.class);
        when(resident.hasTown()).thenReturn(townId != null);
        when(resident.getTown()).thenReturn(townId);
        return resident;
    }
}
