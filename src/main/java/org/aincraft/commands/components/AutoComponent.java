package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.claim.AutoClaimManager;
import org.aincraft.claim.AutoClaimMode;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
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
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to use auto mode"));
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return true;
        }

        // No subcommand - show current mode
        if (args.length == 1) {
            AutoClaimMode currentMode = autoClaimManager.getMode(player.getUniqueId());
            String modeText = switch (currentMode) {
                case OFF -> "<gray>OFF</gray>";
                case AUTO_CLAIM -> "<green>AUTO_CLAIM</green>";
                case AUTO_UNCLAIM -> "<yellow>AUTO_UNCLAIM</yellow>";
            };
            player.sendMessage(MessageFormatter.deserialize("<gold>Current auto mode:</gold> " + modeText));
            return true;
        }

        // Parse subcommand
        String subcommand = args[1].toLowerCase();
        switch (subcommand) {
            case "claim" -> {
                autoClaimManager.setMode(player.getUniqueId(), AutoClaimMode.AUTO_CLAIM);
                player.sendMessage(MessageFormatter.deserialize("<green>Auto-claim enabled</green>"));
            }
            case "unclaim" -> {
                autoClaimManager.setMode(player.getUniqueId(), AutoClaimMode.AUTO_UNCLAIM);
                player.sendMessage(MessageFormatter.deserialize("<yellow>Auto-unclaim enabled</yellow>"));
            }
            case "off" -> {
                autoClaimManager.setMode(player.getUniqueId(), AutoClaimMode.OFF);
                player.sendMessage(MessageFormatter.deserialize("<gray>Auto mode disabled</gray>"));
            }
            default -> {
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: " + getUsage()));
            }
        }

        return true;
    }
}
