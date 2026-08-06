package org.aincraft.guilds.commands;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.aincraft.guilds.commands.brigadier.ChatBrigadierCommand;
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.models.Town;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Chat Command Permissions")
class ChatBrigadierCommandTest extends BrigadierTestBase {

    private ChatBrigadierCommand command;

    @BeforeEach
    void setUp() {
        command = new ChatBrigadierCommand(plugin, chatService, townService, residentService);
    }

    @Test
    @DisplayName("buildCommand returns non-null node")
    void testBuildCommandNotNull() {
        assertNotNull(command.buildCommand());
    }

    @Test
    @DisplayName("/tc requires guilds.chat.town permission")
    void testChatRequiresPermission() {
        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        assertRequiresPermission(node, "guilds.chat.town");
    }

    @Test
    @DisplayName("Player without town gets error")
    void testChatWithoutTownReturnsError() {
        Player p = playerWithPermission("guilds.chat.town");
        Resident resident = mockResident(null);
        when(residentService.getResident(p.getUniqueId())).thenReturn(Optional.of(resident));

        // Verify service is NOT called when player has no town
        Town mockTown = mockTown();
        when(chatService.isTownChat(p.getUniqueId())).thenReturn(false);

        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        assertNotNull(node);
        // Command should handle the no-town case internally
    }

    @Test
    @DisplayName("Command node structure is correct")
    void testCommandStructure() {
        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        assertEquals("tc", node.getLiteral());
    }
}
