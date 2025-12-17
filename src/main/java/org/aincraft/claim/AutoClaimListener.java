package org.aincraft.claim;

import com.google.inject.Inject;
import org.aincraft.ChunkKey;
import org.aincraft.ClaimResult;
import org.aincraft.Guild;
import org.aincraft.GuildService;
import org.aincraft.claim.events.PlayerEnterClaimEvent;
import org.aincraft.commands.MessageFormatter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Objects;

/**
 * Listens for players entering new chunks and triggers auto-claim if enabled.
 * Automatically disables auto-claim on any failure and notifies the player.
 */
public class AutoClaimListener implements Listener {
    private final GuildService guildService;
    private final AutoClaimManager autoClaimManager;

    @Inject
    public AutoClaimListener(GuildService guildService, AutoClaimManager autoClaimManager) {
        this.guildService = Objects.requireNonNull(guildService, "Guild service cannot be null");
        this.autoClaimManager = Objects.requireNonNull(autoClaimManager, "Auto claim manager cannot be null");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnterClaim(PlayerEnterClaimEvent event) {
        Player player = event.getPlayer();

        // Check if player has auto-claim enabled
        if (!autoClaimManager.isAutoClaimEnabled(player.getUniqueId())) {
            return;
        }

        ClaimState newState = event.getNewState();
        ClaimState previousState = event.getPreviousState();

        // Only trigger when entering wilderness (unclaimed chunk)
        // If newState.guildId() is null, it means wilderness
        if (newState.guildId() != null) {
            return; // Not entering wilderness, skip
        }

        // Must be moving from somewhere (not initial login)
        if (previousState == null) {
            return;
        }

        // Get player's guild
        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            // Player not in guild, disable auto-claim
            autoClaimManager.disableAutoClaim(player.getUniqueId());
            player.sendMessage(MessageFormatter.deserialize("<red>Auto-claim disabled: you are not in a guild</red>"));
            return;
        }

        // Extract chunk from location
        ChunkKey chunk = ChunkKey.from(event.getTo().getChunk());

        // Attempt to claim the chunk
        ClaimResult result = guildService.claimChunk(guild.getId(), player.getUniqueId(), chunk);

        // Handle result
        switch (result.getStatus()) {
            case SUCCESS -> {
                // Send success message unless in silent mode
                if (!autoClaimManager.isSilentMode(player.getUniqueId())) {
                    player.sendMessage(MessageFormatter.deserialize(
                            "<green>Claimed chunk at <gold>" + chunk.x() + ", " + chunk.z() + "</gold></green>"));
                }
            }
            case ALREADY_OWNED -> {
                // Already own this chunk, silently continue
            }
            case NOT_ADJACENT -> {
                autoClaimManager.disableAutoClaim(player.getUniqueId());
                player.sendMessage(MessageFormatter.deserialize(
                        "<red>Auto-claim disabled: chunk not adjacent to guild territory</red>"));
            }
            case NO_PERMISSION -> {
                autoClaimManager.disableAutoClaim(player.getUniqueId());
                player.sendMessage(MessageFormatter.deserialize(
                        "<red>Auto-claim disabled: you don't have CLAIM permission</red>"));
            }
            case ALREADY_CLAIMED -> {
                autoClaimManager.disableAutoClaim(player.getUniqueId());
                player.sendMessage(MessageFormatter.deserialize(
                        "<red>Auto-claim disabled: chunk already claimed by another guild</red>"));
            }
            case LIMIT_EXCEEDED -> {
                autoClaimManager.disableAutoClaim(player.getUniqueId());
                player.sendMessage(MessageFormatter.deserialize(
                        "<red>Auto-claim disabled: guild chunk limit reached</red>"));
            }
            case FAILURE -> {
                autoClaimManager.disableAutoClaim(player.getUniqueId());
                player.sendMessage(MessageFormatter.deserialize(
                        "<red>Auto-claim disabled: " + result.getReason() + "</red>"));
            }
        }
    }
}
