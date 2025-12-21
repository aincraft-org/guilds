package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.util.List;
import org.aincraft.Guild;
import org.aincraft.service.GuildLifecycleService;
import org.aincraft.service.GuildMemberService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
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
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to disband guilds"));
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());

        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ You are not in a guild"));
            return true;
        }

        if (!guild.isOwner(player.getUniqueId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Only the guild owner can disband the guild"));
            return true;
        }

        // Check for subregions before disbanding
        List<Subregion> subregions = subregionService.getGuildSubregions(guild.getId());
        if (!subregions.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                    "✗ Cannot disband guild - it contains " + subregions.size() + " subregion(s):"));
            for (Subregion region : subregions) {
                player.sendMessage(MessageFormatter.deserialize(
                        "<gray>  • <gold>" + region.getName() + "</gold></gray>"));
            }
            player.sendMessage(MessageFormatter.deserialize(
                    "<gray>Delete subregions first with <yellow>/g region delete <name></yellow></gray>"));
            return true;
        }

        if (lifecycleService.deleteGuild(guild.getId(), player.getUniqueId())) {
            player.sendMessage(MessageFormatter.deserialize("<green>✓ Guild '<gold>" + guild.getName() + "</gold>' disbanded</green>"));
            return true;
        }

        player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Failed to disband guild"));
        return true;
    }
}
