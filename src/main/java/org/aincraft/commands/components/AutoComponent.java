package org.aincraft.commands.components;

import com.google.inject.Inject;
import dev.mintychochip.mint.Mint;
import org.aincraft.Guild;
import org.aincraft.claim.AutoClaimManager;
import org.aincraft.claim.AutoClaimMode;
import org.aincraft.commands.GuildCommand;
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
            Mint.sendMessage(sender, "<error>This command can only be used by players.</error>");
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Mint.sendMessage(player, "<error>You don't have <accent>permission</accent> to claim chunks.</error>");
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Mint.sendMessage(player, "<error>You are not in a guild.</error>");
            return true;
        }

        if (args.length == 1) {
            AutoClaimMode currentMode = autoClaimManager.getMode(player.getUniqueId());
            Mint.sendMessage(player, "<primary>Current auto-claim mode: <accent>" + currentMode + "</accent></primary>");
            return true;
        }

        String subcommand = args[1].toLowerCase();
        switch (subcommand) {
            case "claim" -> {
                autoClaimManager.setMode(player.getUniqueId(), AutoClaimMode.AUTO_CLAIM);
                Mint.sendMessage(player, "<success>Auto-claim enabled.</success>");
            }
            case "unclaim" -> {
                autoClaimManager.setMode(player.getUniqueId(), AutoClaimMode.AUTO_UNCLAIM);
                Mint.sendMessage(player, "<success>Auto-unclaim enabled.</success>");
            }
            case "off" -> {
                autoClaimManager.setMode(player.getUniqueId(), AutoClaimMode.OFF);
                Mint.sendMessage(player, "<neutral>Auto-claim disabled.</neutral>");
            }
            default -> {
                Mint.sendMessage(player, "<error>Usage: " + getUsage() + "</error>");
            }
        }

        return true;
    }
}
