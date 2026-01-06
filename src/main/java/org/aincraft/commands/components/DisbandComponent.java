package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.util.List;
import org.aincraft.Guild;
import org.aincraft.service.GuildLifecycleService;
import org.aincraft.service.GuildMemberService;
import org.aincraft.commands.GuildCommand;
import dev.mintychochip.mint.Mint;
import org.aincraft.subregion.Subregion;
import org.aincraft.subregion.SubregionService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Component for disbanding a guild.
 * Blocks disband if the guild has subregions.
 */
public class DisbandComponent implements GuildCommand {
    private final GuildMemberService memberService;
    private final GuildLifecycleService lifecycleService;
    private final SubregionService subregionService;

    @Inject
    public DisbandComponent(GuildMemberService memberService, GuildLifecycleService lifecycleService, SubregionService subregionService) {
        this.memberService = memberService;
        this.lifecycleService = lifecycleService;
        this.subregionService = subregionService;
    }

    @Override
    public String getName() {
        return "disband";
    }

    @Override
    public String getPermission() {
        return "guilds.disband";
    }

    @Override
    public String getUsage() {
        return "/g disband";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Mint.sendMessage(sender, "<error>Only players can use this command</error>");
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Mint.sendMessage(player, "<error>You don't have <accent>permission</accent> to disband guilds</error>");
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());

        if (guild == null) {
            Mint.sendMessage(player, "<error>You are not in a guild</error>");
            return true;
        }

        if (!guild.isOwner(player.getUniqueId())) {
            Mint.sendMessage(player, "<error>Only the <secondary>guild owner</secondary> can do this</error>");
            return true;
        }

        // Check for subregions before disbanding
        List<Subregion> subregions = subregionService.getGuildSubregions(guild.getId());
        if (!subregions.isEmpty()) {
            Mint.sendMessage(player, "<error>Cannot disband guild - it contains <primary>" + subregions.size() + "</primary> subregion(s)</error>");
            for (Subregion region : subregions) {
                Mint.sendMessage(player, "<error>- <secondary>" + region.getName() + "</secondary></error>");
            }
            return true;
        }

        if (lifecycleService.deleteGuild(guild.getId(), player.getUniqueId())) {
            Mint.sendMessage(player, "<success>Your guild has been disbanded</success>");
            return true;
        }

        Mint.sendMessage(player, "<error>You don't have permission to disband guilds</error>");
        return true;
    }
}
