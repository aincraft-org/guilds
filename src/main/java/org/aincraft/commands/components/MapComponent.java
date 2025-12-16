package org.aincraft.commands.components;

import org.aincraft.GuildService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.aincraft.map.GuildMapRenderer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command handler for displaying the guild map.
 * Handles `/g map [size]` with optional size parameter (1-5).
 */
public class MapComponent implements GuildCommand {
    private final GuildService guildService;
    private final GuildMapRenderer mapRenderer;
    private static final int MAX_SIZE = 5;
    private static final int MIN_SIZE = 1;
    private static final int DEFAULT_SIZE = 1;

    public MapComponent(GuildService guildService, GuildMapRenderer mapRenderer) {
        this.guildService = guildService;
        this.mapRenderer = mapRenderer;
    }

    @Override
    public String getName() {
        return "map";
    }

    @Override
    public String getPermission() {
        return "guilds.map";
    }

    @Override
    public String getUsage() {
        return "/g map [size]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // Verify player sender
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        // Check permission
        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to view the map"));
            return true;
        }

        // Parse size parameter
        int size = DEFAULT_SIZE;
        if (args.length > 1) {
            try {
                size = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                    "Invalid size: must be a number between " + MIN_SIZE + " and " + MAX_SIZE));
                return true;
            }
        }

        // Validate size
        if (size < MIN_SIZE || size > MAX_SIZE) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                "Map size must be between " + MIN_SIZE + " and " + MAX_SIZE));
            return true;
        }

        // Render and send map
        mapRenderer.renderMap(player, size);
        return true;
    }
}
