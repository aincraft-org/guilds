package org.aincraft.commands.components;

import org.aincraft.Guild;
import org.aincraft.GuildService;
import org.aincraft.claim.AutoClaimManager;
import org.aincraft.claim.AutoClaimState;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Component for toggling auto-claim on/off with optional silent mode.
 */
public class ClaimToggleComponent implements GuildCommand {
    private final GuildService guildService;
    private final AutoClaimManager autoClaimManager;

    public ClaimToggleComponent(GuildService guildService, AutoClaimManager autoClaimManager) {
        this.guildService = guildService;
        this.autoClaimManager = autoClaimManager;
    }

    @Override
    public String getName() {
        return "toggle";
    }

    @Override
    public String getPermission() {
        return "guilds.claim";
    }

    @Override
    public String getUsage() {
        return "/g claim toggle [silent]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to use auto-claim"));
            return true;
        }

        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return true;
        }

        // Parse silent flag - check if "silent" argument is present
        boolean silent = false;
        if (args.length >= 3) {
            silent = args[2].equalsIgnoreCase("silent");
        }

        // Toggle auto-claim
        AutoClaimState newState = autoClaimManager.toggleAutoClaim(player.getUniqueId(), silent);

        // Send feedback
        if (newState.isEnabled()) {
            if (newState.isSilent()) {
                player.sendMessage(MessageFormatter.deserialize("<green>Auto-claim enabled in <yellow>silent mode</yellow></green>"));
            } else {
                player.sendMessage(MessageFormatter.deserialize("<green>Auto-claim enabled</green>"));
            }
        } else {
            player.sendMessage(MessageFormatter.deserialize("<red>Auto-claim disabled</red>"));
        }

        return true;
    }
}
