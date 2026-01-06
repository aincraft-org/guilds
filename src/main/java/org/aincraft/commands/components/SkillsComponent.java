package org.aincraft.commands.components;

import com.google.inject.Inject;
import dev.mintychochip.mint.Mint;
import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
import org.aincraft.service.GuildMemberService;
import org.aincraft.skilltree.SkillTreeRegistry;
import org.aincraft.skilltree.SkillTreeService;
import org.aincraft.skilltree.gui.SkillTreeGUI;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Command component for /g skills.
 * Opens the skill tree GUI for the player's guild.
 * Single Responsibility: Skill command routing to GUI.
 */
public class SkillsComponent implements GuildCommand {

    private final GuildMemberService memberService;
    private final SkillTreeService skillTreeService;
    private final SkillTreeRegistry skillTreeRegistry;

    @Inject
    public SkillsComponent(
            GuildMemberService memberService,
            SkillTreeService skillTreeService,
            SkillTreeRegistry skillTreeRegistry
    ) {
        this.memberService = Objects.requireNonNull(memberService, "GuildMemberService cannot be null");
        this.skillTreeService = Objects.requireNonNull(skillTreeService, "SkillTreeService cannot be null");
        this.skillTreeRegistry = Objects.requireNonNull(skillTreeRegistry, "SkillTreeRegistry cannot be null");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Mint.sendMessage(sender, "<error>This command is for players only</error>");
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Mint.sendMessage(player, "<error>You are not in a guild</error>");
            return true;
        }

        // Open skill tree GUI
        SkillTreeGUI gui = new SkillTreeGUI(guild.getId(), player, skillTreeService, skillTreeRegistry);
        gui.open();
        return true;
    }

    @Override
    public String getName() {
        return "skills";
    }

    @Override
    public String getPermission() {
        return "guilds.skills";
    }

    @Override
    public String getUsage() {
        return "/g skills";
    }
}
