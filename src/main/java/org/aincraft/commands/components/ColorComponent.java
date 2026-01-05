package org.aincraft.commands.components;

import com.google.inject.Inject;
import net.kyori.adventure.text.format.NamedTextColor;
import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
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
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Messages.send(player, MessageKey.ERROR_NO_PERMISSION);
            return true;
        }

        if (args.length < 2) {
            Messages.send(player, MessageKey.ERROR_USAGE, getUsage());
            return false;
        }

        // Get player's guild
        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Messages.send(player, MessageKey.ERROR_NOT_IN_GUILD);
            return true;
        }

        // Check if player is guild owner
        if (!guild.isOwner(player.getUniqueId())) {
            Messages.send(player, MessageKey.ERROR_NOT_GUILD_OWNER);
            return true;
        }

        String colorInput = args[1].toLowerCase();

        // Handle clear command
        if (colorInput.equals("clear")) {
            guild.setColor(null);
            lifecycleService.save(guild);
            Messages.send(player, MessageKey.GUILD_COLOR_CLEARED);
            return true;
        }

        // Validate color
        if (!isValidColor(colorInput)) {
            Messages.send(player, MessageKey.GUILD_COLOR_INVALID);
            return true;
        }

        guild.setColor(colorInput);
        lifecycleService.save(guild);
        Messages.send(player, MessageKey.GUILD_COLOR_SET, colorInput);
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

        // Check if named color
        return NamedTextColor.NAMES.value(color) != null;
    }

    /**
     * Gets a list of available named colors.
     */
    private String getAvailableColors() {
        return "black, dark_blue, dark_green, dark_aqua, dark_red, dark_purple, gold, " +
               "gray, dark_gray, blue, green, aqua, red, light_purple, yellow, white";
    }
}
