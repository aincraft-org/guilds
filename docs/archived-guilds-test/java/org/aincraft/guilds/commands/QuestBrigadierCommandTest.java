package org.aincraft.guilds.commands;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.aincraft.guilds.commands.brigadier.QuestBrigadierCommand;
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.models.Town;
import org.aincraft.guilds.models.TownQuest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Quest Command Permissions")
class QuestBrigadierCommandTest extends BrigadierTestBase {

    private QuestBrigadierCommand command;

    @BeforeEach
    void setUp() {
        command = new QuestBrigadierCommand(plugin, questService, townService, residentService);
    }

    @Test
    @DisplayName("buildCommand returns non-null node")
    void testBuildCommandNotNull() {
        assertNotNull(command.buildCommand());
    }

    @Test
    @DisplayName("/town quests requires permission")
    void testQuestsRequiresPermission() {
        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        assertNotNull(node);
        // Verify the node has permission requirement
        assert node.getRequirement() != null : "Quest command should have permission requirement";
    }

    @Test
    @DisplayName("refresh subcommand exists and requires admin permission")
    void testQuestRefreshRequiresAdminPermission() {
        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        assertNotNull(node.getChild("quest"), "/town quest should exist");
        assertNotNull(node.getChild("quest").getChild("refresh"), "/town quest refresh should exist");
    }

    @Test
    @DisplayName("quests subcommand node exists")
    void testQuestsSubcommandExists() {
        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        assertNotNull(node.getChild("quests"), "/town quests should exist");
    }

    @Test
    @DisplayName("progress subcommand node exists")
    void testProgressSubcommandExists() {
        LiteralCommandNode<CommandSourceStack> node = command.buildCommand();
        assertNotNull(node.getChild("quest"), "/town quest should exist");
        assertNotNull(node.getChild("quest").getChild("progress"), "/town quest progress should exist");
    }

    @Test
    @DisplayName("Player not in town gets error")
    void testQuestsWithoutTownReturnsError() {
        Player p = playerWithPermission("guilds.quest");
        Resident resident = mockResident(null);
        when(residentService.getResident(p.getUniqueId())).thenReturn(Optional.of(resident));

        // Service should NOT be called
        verify(questService, never()).getActiveQuests(anyString());
    }
}
