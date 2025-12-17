package org.aincraft.claim;

import com.google.inject.Inject;
import org.aincraft.ChunkKey;
import org.aincraft.Guild;
import org.aincraft.GuildService;
import org.aincraft.claim.events.PlayerEnterClaimEvent;
import org.aincraft.commands.MessageFormatter;
import org.aincraft.subregion.Subregion;
import org.aincraft.subregion.SubregionService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Objects;

/**
 * Listens for players entering chunks and triggers auto-unclaim if enabled.
 * Automatically disables auto-unclaim on any failure and notifies the player.
 */
public class AutoUnclaimListener implements Listener {
    private final GuildService guildService;
    private final AutoUnclaimManager autoUnclaimManager;
    private final SubregionService subregionService;

    @Inject
    public AutoUnclaimListener(GuildService guildService, AutoUnclaimManager autoUnclaimManager, SubregionService subregionService) {
        this.guildService = Objects.requireNonNull(guildService, "Guild service cannot be null");
        this.autoUnclaimManager = Objects.requireNonNull(autoUnclaimManager, "Auto unclaim manager cannot be null");
        this.subregionService = Objects.requireNonNull(subregionService, "Subregion service cannot be null");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnterClaim(PlayerEnterClaimEvent event) {
        Player player = event.getPlayer();

        // Check if player has auto-unclaim enabled
        if (!autoUnclaimManager.isAutoUnclaimEnabled(player.getUniqueId())) {
            return;
        }

        ClaimState newState = event.getNewState();
        ClaimState previousState = event.getPreviousState();

        // Only trigger when entering a claimed chunk
        if (newState.guildId() == null) {
            return; // Entering wilderness, skip
        }

        // Must be moving from somewhere (not initial login)
        if (previousState == null) {
            return;
        }

        // Get player's guild
        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            // Player not in guild, disable auto-unclaim
            autoUnclaimManager.disableAutoUnclaim(player.getUniqueId());
            player.sendMessage(MessageFormatter.deserialize("<red>Auto-unclaim disabled: you are not in a guild</red>"));
            return;
        }

        // Only unclaim if entering OWN guild's territory
        if (!newState.guildId().equals(guild.getId())) {
            return; // Not own guild's territory, skip
        }

        // Extract chunk from location
        ChunkKey chunk = ChunkKey.from(event.getTo().getChunk());

        // Check for subregions in this chunk - blocks unclaim
        List<Subregion> subregions = subregionService.getSubregionsInChunk(chunk);
        if (!subregions.isEmpty()) {
            autoUnclaimManager.disableAutoUnclaim(player.getUniqueId());
            player.sendMessage(MessageFormatter.deserialize(
                    "<red>Auto-unclaim disabled: chunk contains " + subregions.size() + " subregion(s)</red>"));
            return;
        }

        // Attempt to unclaim the chunk
        boolean success = guildService.unclaimChunk(guild.getId(), player.getUniqueId(), chunk);

        // Handle result
        if (success) {
            // Send success message unless in silent mode
            if (!autoUnclaimManager.isSilentMode(player.getUniqueId())) {
                player.sendMessage(MessageFormatter.deserialize(
                        "<green>Unclaimed chunk at <gold>" + chunk.x() + ", " + chunk.z() + "</gold></green>"));
            }
        } else {
            // Failed - disable and notify
            autoUnclaimManager.disableAutoUnclaim(player.getUniqueId());
            player.sendMessage(MessageFormatter.deserialize(
                    "<red>Auto-unclaim disabled: you don't have UNCLAIM permission</red>"));
        }
    }
}
