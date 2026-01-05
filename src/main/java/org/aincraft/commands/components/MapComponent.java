package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.commands.GuildCommand;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
import org.aincraft.map.GuildMapRenderer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command handler for displaying the guild map.
 * Handles `/g map [size]` with optional size parameter (1-5).
 */
public class MapComponent implements GuildCommand {
    private final GuildMapRenderer mapRenderer;
    private static final int MAX_SIZE = 5;
    private static final int MIN_SIZE = 1;
    private static final int DEFAULT_SIZE = 1;

    @Inject
    public MapComponent(GuildMapRenderer mapRenderer) {
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
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        // Check permission
        if (!player.hasPermission(getPermission())) {
            Messages.send(player, MessageKey.ERROR_NO_PERMISSION);
            return true;
        }

        // Parse size parameter
        int size = DEFAULT_SIZE;
        if (args.length > 1) {
            try {
                size = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                Messages.send(player, MessageKey.MAP_INVALID_SIZE, MIN_SIZE, MAX_SIZE);
                return true;
            }
        }

        // Validate size
        if (size < MIN_SIZE || size > MAX_SIZE) {
            Messages.send(player, MessageKey.MAP_SIZE_RANGE, MIN_SIZE, MAX_SIZE);
            return true;
        }

        // Render and send map
        mapRenderer.renderMap(player, size);
        return true;
    }
}
