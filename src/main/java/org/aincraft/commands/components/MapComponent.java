package org.aincraft.commands.components;

import com.google.inject.Inject;
import dev.mintychochip.mint.Mint;
import org.aincraft.commands.GuildCommand;
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
        if (!(sender instanceof Player player)) {
            Mint.sendMessage(sender, "<error>This command can only be used by players</error>");
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Mint.sendMessage(player, "<error>You don't have permission to use this command</error>");
            return true;
        }

        int size = DEFAULT_SIZE;
        if (args.length > 1) {
            try {
                size = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                Mint.sendMessage(player, "<error>Invalid size. Must be between <secondary>" + MIN_SIZE + "</secondary> and <secondary>" + MAX_SIZE + "</secondary></error>");
                return true;
            }
        }

        if (size < MIN_SIZE || size > MAX_SIZE) {
            Mint.sendMessage(player, "<error>Size must be between <secondary>" + MIN_SIZE + "</secondary> and <secondary>" + MAX_SIZE + "</secondary></error>");
            return true;
        }

        // Render and send map
        mapRenderer.renderMap(player, size);
        return true;
    }
}
