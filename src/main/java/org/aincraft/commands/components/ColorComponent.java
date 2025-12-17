package org.aincraft.commands.components;

import net.kyori.adventure.text.format.NamedTextColor;
import org.aincraft.Guild;
import org.aincraft.GuildService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command component for setting a guild's color.
 * Usage: /g color <hex-color or color-name>
 * Examples: /g color #FF0000 or /g color red
 */
public class ColorComponent implements GuildCommand {
    private final GuildService guildService;

    public ColorComponent(GuildService guildService) {
        this.guildService = guildService;
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
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to change guild color"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: " + getUsage()));
            return false;
        }

        // Get player's guild
        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return true;
        }

        // Check if player is guild owner
        if (!guild.isOwner(player.getUniqueId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only the guild owner can change guild color"));
            return true;
        }

        String colorInput = args[1].toLowerCase();

        // Handle clear command
        if (colorInput.equals("clear")) {
            guild.setColor(null);
            guildService.save(guild);
            player.sendMessage(MessageFormatter.deserialize("<green>Guild color cleared</green>"));
            return true;
        }

        // Validate color
        if (!isValidColor(colorInput)) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Invalid color format. Use hex (#RRGGBB) or a named color"));
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Named colors: " + getAvailableColors()));
            return true;
        }

        guild.setColor(colorInput);
        guildService.save(guild);
        player.sendMessage(MessageFormatter.deserialize("<green>Guild color set to <gold>" + colorInput + "</gold></green>"));
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
