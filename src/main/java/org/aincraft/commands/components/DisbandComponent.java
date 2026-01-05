package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.util.List;
import org.aincraft.Guild;
import org.aincraft.service.GuildLifecycleService;
import org.aincraft.service.GuildMemberService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
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
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Messages.send(player, MessageKey.ERROR_NO_PERMISSION);
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());

        if (guild == null) {
            Messages.send(player, MessageKey.ERROR_NOT_IN_GUILD);
            return true;
        }

        if (!guild.isOwner(player.getUniqueId())) {
            Messages.send(player, MessageKey.ERROR_NOT_GUILD_OWNER);
            return true;
        }

        // Check for subregions before disbanding
        List<Subregion> subregions = subregionService.getGuildSubregions(guild.getId());
        if (!subregions.isEmpty()) {
            player.sendMessage(Messages.get(MessageKey.ERROR_NO_PERMISSION,
                "Cannot disband guild - it contains " + subregions.size() + " subregion(s)"));
            for (Subregion region : subregions) {
                player.sendMessage(Messages.get(MessageKey.ERROR_NO_PERMISSION, region.getName()));
            }
            return true;
        }

        if (lifecycleService.deleteGuild(guild.getId(), player.getUniqueId())) {
            Messages.send(player, MessageKey.GUILD_DISBANDED);
            return true;
        }

        Messages.send(player, MessageKey.ERROR_NO_PERMISSION);
        return true;
    }
}
