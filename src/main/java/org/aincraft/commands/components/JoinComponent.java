package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
import dev.mintychochip.mint.Mint;
import org.aincraft.service.GuildLifecycleService;
import org.aincraft.service.GuildMemberService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Component for joining a guild.
 */
public class JoinComponent implements GuildCommand {
    private final GuildLifecycleService lifecycleService;
    private final GuildMemberService memberService;

    @Inject
    public JoinComponent(GuildLifecycleService lifecycleService, GuildMemberService memberService) {
        this.lifecycleService = lifecycleService;
        this.memberService = memberService;
    }

    @Override
    public String getName() {
        return "join";
    }

    @Override
    public String getPermission() {
        return "guilds.join";
    }

    @Override
    public String getUsage() {
        return "/g join <guild-name>";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Mint.sendMessage(sender, "<error>Only players can use this command</error>");
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Mint.sendMessage(player, "<error>You don't have <accent>permission</accent> to join guilds</error>");
            return true;
        }

        if (args.length < 2) {
            Mint.sendMessage(player, "<error>Usage: <accent>/g join <guild-name></accent></error>");
            return false;
        }

        String guildName = args[1];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            Mint.sendMessage(player, "<error>Guild not found: <secondary>" + guildName + "</secondary></error>");
            return true;
        }

        // Check if guild is private
        if (!guild.isPublic()) {
            Mint.sendMessage(player, "<error>You don't have permission to join this guild</error>");
            return true;
        }

        if (memberService.joinGuild(guild.getId(), player.getUniqueId())) {
            Mint.sendMessage(player, "<success>You joined <secondary>" + guild.getName() + "</secondary>!</success>");
            return true;
        }

        Mint.sendMessage(player, "<error>You don't have permission to join this guild</error>");
        return true;
    }
}
