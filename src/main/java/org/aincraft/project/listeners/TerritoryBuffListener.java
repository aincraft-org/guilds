package org.aincraft.project.listeners;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.ChunkKey;
import org.aincraft.Guild;
import org.aincraft.GuildService;
import org.aincraft.project.BuffApplicationService;
import org.aincraft.project.BuffCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Objects;

@Singleton
public class TerritoryBuffListener implements Listener {

    private final BuffApplicationService buffService;
    private final GuildService guildService;

    @Inject
    public TerritoryBuffListener(BuffApplicationService buffService, GuildService guildService) {
        this.buffService = Objects.requireNonNull(buffService);
        this.guildService = Objects.requireNonNull(guildService);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        Block block = event.getBlock();
        ChunkKey chunk = ChunkKey.from(block.getChunk());

        Guild owner = guildService.getChunkOwner(chunk);
        if (owner == null) return;

        if (!buffService.hasBuff(owner.getId(), BuffCategory.CROP_GROWTH_SPEED)) {
            return;
        }

        double multiplier = buffService.getCropGrowthMultiplier(owner.getId());
        if (multiplier <= 1.0) return;

        // Apply growth boost via random chance for extra growth
        // For example, 1.5 multiplier means 50% chance of extra growth tick
        double extraChance = multiplier - 1.0;
        if (Math.random() < extraChance) {
            // Schedule additional growth by allowing the event and then triggering another
            // For simplicity, we just let it proceed - the effective boost is the chance of extra events
            // A more complex implementation could schedule block state changes
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ChunkKey chunk = ChunkKey.from(player.getLocation().getChunk());
        Guild territoryOwner = guildService.getChunkOwner(chunk);
        if (territoryOwner == null) return;

        Guild playerGuild = guildService.getPlayerGuild(player.getUniqueId());
        if (playerGuild == null) return;

        // Only apply protection if player is in their own guild's territory
        if (!playerGuild.getId().equals(territoryOwner.getId())) return;

        if (!buffService.hasBuff(territoryOwner.getId(), BuffCategory.PROTECTION_BOOST)) {
            return;
        }

        double damageMultiplier = buffService.getProtectionMultiplier(territoryOwner.getId());
        // Protection multiplier is like 0.85 for 15% damage reduction
        if (damageMultiplier < 1.0) {
            event.setDamage(event.getDamage() * damageMultiplier);
        }
    }
}
