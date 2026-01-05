package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.ChunkKey;
import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
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
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Messages.send(player, MessageKey.CLAIM_NO_PERMISSION);
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Messages.send(player, MessageKey.ERROR_NOT_IN_GUILD);
            return true;
        }

        ChunkKey chunk = ChunkKey.from(player.getLocation().getChunk());

        Guild existingOwner = territoryService.getChunkOwner(chunk);
        if (existingOwner != null) {
            if (existingOwner.getId().equals(guild.getId())) {
                Messages.send(player, MessageKey.CLAIM_ALREADY_OWNED);
            } else {
                Messages.send(player, MessageKey.CLAIM_ALREADY_CLAIMED, existingOwner.getName());
            }
            return true;
        }

        org.aincraft.ClaimResult result = territoryService.claimChunk(guild.getId(), player.getUniqueId(), chunk);
        if (result.isSuccess()) {
            Messages.send(player, MessageKey.CLAIM_SUCCESS, chunk.x(), chunk.z());
        } else {
            Messages.send(player, MessageKey.ERROR_NO_PERMISSION, result.getReason());
        }

        return true;
    }
}
