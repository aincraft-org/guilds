package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.claim.AutoClaimManager;
import org.aincraft.claim.AutoClaimMode;
import org.aincraft.commands.GuildCommand;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
import org.aincraft.service.GuildMemberService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Component for managing auto-claim mode (OFF, AUTO_CLAIM, AUTO_UNCLAIM).
 * Provides explicit state selection via /g auto [claim|unclaim|off].
 */
public class AutoComponent implements GuildCommand {
    private final GuildMemberService memberService;
    private final AutoClaimManager autoClaimManager;

    @Inject
    public AutoComponent(GuildMemberService memberService, AutoClaimManager autoClaimManager) {
        this.memberService = memberService;
        this.autoClaimManager = autoClaimManager;
    }

    @Override
    public String getName() {
        return "auto";
    }

    @Override
    public String getPermission() {
        return "guilds.claim";
    }

    @Override
    public String getUsage() {
        return "/g auto [claim|unclaim|off]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Messages.send(player, MessageKey.CLAIM_NO_PERMISSION);
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Messages.send(player, MessageKey.ERROR_NOT_IN_GUILD);
            return true;
        }

        // No subcommand - show current mode
        if (args.length == 1) {
            AutoClaimMode currentMode = autoClaimManager.getMode(player.getUniqueId());
            Messages.send(player, MessageKey.AUTO_CURRENT_MODE);
            return true;
        }

        // Parse subcommand
        String subcommand = args[1].toLowerCase();
        switch (subcommand) {
            case "claim" -> {
                autoClaimManager.setMode(player.getUniqueId(), AutoClaimMode.AUTO_CLAIM);
                Messages.send(player, MessageKey.AUTO_ENABLED_CLAIM);
            }
            case "unclaim" -> {
                autoClaimManager.setMode(player.getUniqueId(), AutoClaimMode.AUTO_UNCLAIM);
                Messages.send(player, MessageKey.AUTO_ENABLED_UNCLAIM);
            }
            case "off" -> {
                autoClaimManager.setMode(player.getUniqueId(), AutoClaimMode.OFF);
                Messages.send(player, MessageKey.AUTO_DISABLED);
            }
            default -> {
                Messages.send(player, MessageKey.ERROR_USAGE, getUsage());
            }
        }

        return true;
    }
}
