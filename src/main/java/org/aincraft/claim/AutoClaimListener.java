package org.aincraft.claim;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import org.aincraft.ChunkKey;
import org.aincraft.ClaimResult;
import org.aincraft.Guild;
import org.aincraft.claim.events.PlayerEnterClaimEvent;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.TerritoryService;
import org.aincraft.subregion.Subregion;
import org.aincraft.subregion.SubregionService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Listens for players entering chunks and triggers auto-claim or auto-unclaim if enabled.
 * Automatically disables auto modes on any failure and notifies the player.
 */
public class AutoClaimListener implements Listener {
    private final TerritoryService territoryService;
    private final GuildMemberService memberService;
    private final AutoClaimManager autoClaimManager;
    private final SubregionService subregionService;

    @Inject
    public AutoClaimListener(TerritoryService territoryService, GuildMemberService memberService, AutoClaimManager autoClaimManager, SubregionService subregionService) {
        this.territoryService = Objects.requireNonNull(territoryService, "Territory service cannot be null");
        this.memberService = Objects.requireNonNull(memberService, "Member service cannot be null");
        this.autoClaimManager = Objects.requireNonNull(autoClaimManager, "Auto claim manager cannot be null");
        this.subregionService = Objects.requireNonNull(subregionService, "Subregion service cannot be null");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnterClaim(PlayerEnterClaimEvent event) {
        Player player = event.getPlayer();
        AutoClaimMode mode = autoClaimManager.getMode(player.getUniqueId());
        if (mode == AutoClaimMode.OFF) return;

        ClaimState newState = event.getNewState();
        ClaimState oldState = event.getPreviousState();
        if (oldState == null) return;

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            autoClaimManager.disable(player.getUniqueId());
            Messages.send(player, MessageKey.AUTO_NOT_IN_GUILD);
            return;
        }

        switch (mode) {
            case AUTO_CLAIM -> handleAutoClaim(event, player, guild);
            case AUTO_UNCLAIM -> handleAutoUnclaim(event, player, guild);
        }
    }

    private void handleAutoClaim(PlayerEnterClaimEvent event, Player player, Guild guild) {
        if (event.getNewState().guildId() != null) return; // Not entering wilderness

        ChunkKey chunk = ChunkKey.from(event.getTo().getChunk());
        ClaimResult result = territoryService.claimChunk(guild.getId(), player.getUniqueId(), chunk);

        switch (result.getStatus()) {
            case SUCCESS -> Messages.send(player, MessageKey.CLAIM_SUCCESS, chunk.x(), chunk.z());
            case ALREADY_OWNED -> {} // Silently continue
            default -> {
                autoClaimManager.disable(player.getUniqueId());
                Messages.send(player, MessageKey.AUTO_DISABLED);
            }
        }
    }

    private void handleAutoUnclaim(PlayerEnterClaimEvent event, Player player, Guild guild) {
        ClaimState newState = event.getNewState();
        if (newState.guildId() == null || !newState.guildId().equals(guild.getId())) return;

        ChunkKey chunk = ChunkKey.from(event.getTo().getChunk());

        List<Subregion> subregions = subregionService.getSubregionsInChunk(chunk);
        if (!subregions.isEmpty()) {
            autoClaimManager.disable(player.getUniqueId());
            Messages.send(player, MessageKey.AUTO_DISABLED);
            return;
        }

        if (territoryService.unclaimChunk(guild.getId(), player.getUniqueId(), chunk)) {
            Messages.send(player, MessageKey.CLAIM_UNCLAIMED, chunk.x(), chunk.z());
        } else {
            autoClaimManager.disable(player.getUniqueId());
            Messages.send(player, MessageKey.AUTO_DISABLED);
        }
    }

    private String getErrorMessage(ClaimResult result) {
        return switch (result.getStatus()) {
            case NOT_ADJACENT -> "chunk not adjacent to guild territory";
            case NO_PERMISSION -> "you don't have CLAIM permission";
            case ALREADY_CLAIMED -> "chunk already claimed by another guild";
            case LIMIT_EXCEEDED -> "guild chunk limit reached";
            case FAILURE -> result.getReason();
            default -> "unknown error";
        };
    }
}
