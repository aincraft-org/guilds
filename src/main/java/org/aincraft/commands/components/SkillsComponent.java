package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.aincraft.service.GuildMemberService;
import org.aincraft.skilltree.SkillTreeRegistry;
import org.aincraft.skilltree.SkillTreeService;
import org.aincraft.skilltree.gui.SkillTreeGUI;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Command component for /g skills.
 * Opens the skill tree GUI.
 */
public class SkillsComponent implements GuildCommand {

    private final GuildMemberService memberService;
    private final SkillTreeService skillTreeService;
    private final SkillTreeRegistry registry;

    @Inject
    public SkillsComponent(
            GuildMemberService memberService,
            SkillTreeService skillTreeService,
            SkillTreeRegistry registry
    ) {
        this.memberService = Objects.requireNonNull(memberService);
        this.skillTreeService = Objects.requireNonNull(skillTreeService);
        this.registry = Objects.requireNonNull(registry);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return true;
        }

        // Open skill tree GUI
        SkillTreeGUI gui = new SkillTreeGUI(guild, player, skillTreeService, registry);
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
