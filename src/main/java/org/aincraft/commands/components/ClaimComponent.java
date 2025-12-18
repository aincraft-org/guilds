package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.ChunkKey;
import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.TerritoryService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Component for claiming the chunk the player is standing in.
 */
public class ClaimComponent implements GuildCommand {
    private final GuildMemberService memberService;
    private final TerritoryService territoryService;

    @Inject
    public ClaimComponent(GuildMemberService memberService, TerritoryService territoryService) {
        this.memberService = memberService;
        this.territoryService = territoryService;
    }

    @Override
    public String getName() {
        return "claim";
    }

    @Override
    public String getPermission() {
        return "guilds.claim";
    }

    @Override
    public String getUsage() {
        return "/g claim";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to claim chunks"));
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return true;
        }

        ChunkKey chunk = ChunkKey.from(player.getLocation().getChunk());

        Guild existingOwner = territoryService.getChunkOwner(chunk);
        if (existingOwner != null) {
            if (existingOwner.getId().equals(guild.getId())) {
                player.sendMessage(MessageFormatter.format(MessageFormatter.WARNING, "Your guild already owns this chunk"));
            } else {
                player.sendMessage(MessageFormatter.deserialize("<red>This chunk is claimed by <gold>" + existingOwner.getName() + "</gold></red>"));
            }
            return true;
        }

        org.aincraft.ClaimResult result = territoryService.claimChunk(guild.getId(), player.getUniqueId(), chunk);
        if (result.isSuccess()) {
            player.sendMessage(MessageFormatter.deserialize("<green>Claimed chunk at <gold>" + chunk.x() + ", " + chunk.z() +
                    "</gold> for <gold>" + guild.getName() + "</gold></green>"));
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, result.getReason()));
        }

        return true;
    }
}
