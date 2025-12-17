package org.aincraft.commands.components;

import org.aincraft.Guild;
import org.aincraft.GuildService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Component for creating a new guild.
 */
public class CreateComponent implements GuildCommand {
    private final GuildService guildService;

    public CreateComponent(GuildService guildService) {
        this.guildService = guildService;
    }

    @Override
    public String getName() {
        return "create";
    }

    @Override
    public String getPermission() {
        return "guilds.create";
    }

    @Override
    public String getUsage() {
        return "/g create <name> [description]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to create guilds"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: " + getUsage()));
            return false;
        }

        String name = args[1];
        String description = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : null;

        Guild guild = guildService.createGuild(name, description, player.getUniqueId());

        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Failed to create guild. Name may already exist or you're already in a guild"));
            return true;
        }

        player.sendMessage(MessageFormatter.format("<green>✓ Guild '<gold>%s</gold>' created successfully!</green>", guild.getName()));

        // Auto-claim the chunk where guild was created
        org.aincraft.ChunkKey chunk = org.aincraft.ChunkKey.from(player.getLocation().getChunk());
        org.aincraft.ClaimResult claimResult = guildService.claimChunk(guild.getId(), player.getUniqueId(), chunk);
        if (claimResult.isSuccess()) {
            player.sendMessage(MessageFormatter.format("<green>✓ Automatically claimed chunk at <gold>%d, %d</gold></green>", chunk.x(), chunk.z()));
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.WARNING, "Could not auto-claim chunk: " + claimResult.getReason()));
        }

        return true;
    }
}
