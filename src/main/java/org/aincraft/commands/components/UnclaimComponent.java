package org.aincraft.commands.components;

import org.aincraft.ChunkKey;
import org.aincraft.Guild;
import org.aincraft.GuildService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.aincraft.subregion.Subregion;
import org.aincraft.subregion.SubregionService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Component for unclaiming the chunk the player is standing in.
 * Blocks unclaim if subregions exist in the chunk.
 */
public class UnclaimComponent implements GuildCommand {
    private final GuildService guildService;
    private final SubregionService subregionService;

    public UnclaimComponent(GuildService guildService, SubregionService subregionService) {
        this.guildService = guildService;
        this.subregionService = subregionService;
    }

    @Override
    public String getName() {
        return "unclaim";
    }

    @Override
    public String getPermission() {
        return "guilds.unclaim";
    }

    @Override
    public String getUsage() {
        return "/g unclaim [all]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to unclaim chunks"));
            return true;
        }

        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return true;
        }

        // Check if "all" argument is provided
        if (args.length > 1 && "all".equalsIgnoreCase(args[1])) {
            if (guildService.unclaimAllChunks(guild.getId(), player.getUniqueId())) {
                int chunkCount = guildService.getGuildChunkCount(guild.getId());
                player.sendMessage(MessageFormatter.deserialize("<green>Unclaimed all chunks for <gold>" + guild.getName() + "</gold></green>"));
            } else {
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Failed to unclaim all chunks. You may lack UNCLAIM permission."));
            }
        } else {
            // Unclaim single chunk at player's location
            ChunkKey chunk = ChunkKey.from(player.getLocation().getChunk());

            Guild chunkOwner = guildService.getChunkOwner(chunk);
            if (chunkOwner == null) {
                player.sendMessage(MessageFormatter.format(MessageFormatter.WARNING, "This chunk is not claimed"));
                return true;
            }

            if (!chunkOwner.getId().equals(guild.getId())) {
                player.sendMessage(MessageFormatter.deserialize("<red>This chunk is claimed by <gold>" + chunkOwner.getName() +
                        "</gold>, not your guild</red>"));
                return true;
            }

            // Check for subregions in this chunk
            List<Subregion> subregions = subregionService.getSubregionsInChunk(chunk);
            if (!subregions.isEmpty()) {
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                        "Cannot unclaim chunk - it contains " + subregions.size() + " subregion(s):"));
                for (Subregion region : subregions) {
                    player.sendMessage(MessageFormatter.deserialize(
                            "<gray>  • <gold>" + region.getName() + "</gold></gray>"));
                }
                player.sendMessage(MessageFormatter.deserialize(
                        "<gray>Delete subregions first with <yellow>/g region delete <name></yellow></gray>"));
                return true;
            }

            if (guildService.unclaimChunk(guild.getId(), player.getUniqueId(), chunk)) {
                player.sendMessage(MessageFormatter.deserialize("<green>Unclaimed chunk at <gold>" + chunk.x() + ", " + chunk.z() + "</gold></green>"));
            } else {
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Failed to unclaim chunk. You may lack UNCLAIM permission."));
            }
        }

        return true;
    }
}
