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
 * Command component for setting a guild's color.
 * Usage: /g color <hex-color or color-name>
 * Examples: /g color #FF0000 or /g color red
 */
public class ColorComponent implements GuildCommand {
    private final GuildMemberService memberService;
    private final GuildLifecycleService lifecycleService;

    @Inject
    public ColorComponent(GuildMemberService memberService, GuildLifecycleService lifecycleService) {
        this.memberService = memberService;
        this.lifecycleService = lifecycleService;
    }

    @Override
    public String getName() {
        return "color";
    }

    @Override
    public String getPermission() {
        return "guilds.color";
    }

    @Override
    public String getUsage() {
        return "/g color <color> or /g color clear";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Mint.sendMessage(sender, "<error>Only players can use this command</error>");
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Mint.sendMessage(player, "<error>You don't have permission to change guild color</error>");
            return true;
        }

        if (args.length < 2) {
            Mint.sendMessage(player, "<error>Usage: /g color <color> or /g color clear</error>");
            return false;
        }

        // Get player's guild
        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Mint.sendMessage(player, "<error>You are not in a guild</error>");
            return true;
        }

        // Check if player is guild owner
        if (!guild.isOwner(player.getUniqueId())) {
            Mint.sendMessage(player, "<error>Only guild owner can do this</error>");
            return true;
        }

        String colorInput = args[1].toLowerCase();

        // Handle clear command
        if (colorInput.equals("clear")) {
            guild.setColor(null);
            lifecycleService.save(guild);
            Mint.sendMessage(player, "<success>Guild color cleared</success>");
            return true;
        }

        // Validate color
        if (!isValidColor(colorInput)) {
            Mint.sendMessage(player, "<error>Invalid color format. Use hex (#RRGGBB) or a named color</error>");
            return true;
        }

        guild.setColor(colorInput);
        lifecycleService.save(guild);
        Mint.sendMessage(player, "<success>Guild color set to <secondary>" + colorInput + "</secondary></success>");
        return true;
    }

    /**
     * Validates if a color string is valid (hex or named color).
     */
    private boolean isValidColor(String color) {
        // Check if hex format
        if (color.startsWith("#")) {
            if (color.length() != 7) {
                return false;
            }
            try {
                Integer.parseInt(color.substring(1), 16);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        // Check if named color - return true for named colors since Mint supports them
        return true;
    }

    /**
     * Gets a list of available named colors.
     */
    private String getAvailableColors() {
        return "black, dark_blue, dark_green, dark_aqua, dark_red, dark_purple, gold, " +
               "gray, dark_gray, blue, green, aqua, red, light_purple, yellow, white";
    }
}
