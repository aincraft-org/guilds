package org.aincraft.progression.listeners;

import com.google.inject.Inject;
import org.aincraft.ChunkKey;
import org.aincraft.Guild;
import org.aincraft.progression.ProgressionConfig;
import org.aincraft.progression.ProgressionService;
import org.aincraft.progression.XpSource;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.TerritoryService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Objects;

/**
 * Event listener for awarding guild XP from player activities.
 * Awards XP for mob kills and block mining in guild territory.
 */
public class ProgressionXpListener implements Listener {
    private final ProgressionService progressionService;
    private final GuildMemberService memberService;
    private final TerritoryService territoryService;
    private final ProgressionConfig config;

    @Inject
    public ProgressionXpListener(ProgressionService progressionService,
                                  GuildMemberService memberService,
                                  TerritoryService territoryService,
                                  ProgressionConfig config) {
        this.progressionService = Objects.requireNonNull(progressionService, "Progression service cannot be null");
        this.memberService = Objects.requireNonNull(memberService, "Member service cannot be null");
        this.territoryService = Objects.requireNonNull(territoryService, "Territory service cannot be null");
        this.config = Objects.requireNonNull(config, "Config cannot be null");
    }

    /**
     * Awards XP for mob kills.
     * Only awards if killer is in a guild and kill occurs in their guild's territory.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!config.isMobKillEnabled()) {
            return;
        }

        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }

        // Check if player is in a guild
        Guild guild = memberService.getPlayerGuild(killer.getUniqueId());
        if (guild == null) {
            return;
        }

        // Check if kill location is in guild's territory
        ChunkKey chunkKey = ChunkKey.from(event.getEntity().getLocation().getChunk());
        Guild chunkOwner = territoryService.getChunkOwner(chunkKey);

        if (chunkOwner == null || !chunkOwner.getId().equals(guild.getId())) {
            return; // Not in guild territory
        }

        // Calculate XP
        String entityType = event.getEntityType().name();
        double multiplier = config.getMobKillMultiplier(entityType);
        long baseXp = config.getMobKillBaseXp();
        long xp = (long) (baseXp * multiplier);

        // Award XP
        progressionService.awardXp(guild.getId(), killer.getUniqueId(), XpSource.MOB_KILL, xp);
    }

    /**
     * Awards XP for block mining.
     * Only awards if player is in a guild and mining occurs in their guild's territory.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!config.isBlockMiningEnabled()) {
            return;
        }

        Player player = event.getPlayer();

        // Check if player is in a guild
        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            return;
        }

        // Check if mining location is in guild's territory
        ChunkKey chunkKey = ChunkKey.from(event.getBlock().getChunk());
        Guild chunkOwner = territoryService.getChunkOwner(chunkKey);

        if (chunkOwner == null || !chunkOwner.getId().equals(guild.getId())) {
            return; // Not in guild territory
        }

        // Calculate XP
        String blockType = event.getBlock().getType().name();
        double multiplier = config.getBlockMiningMultiplier(blockType);

        // Only award XP for blocks with configured multipliers > 0
        if (multiplier <= 0) {
            return;
        }

        long baseXp = config.getBlockMiningBaseXp();
        long xp = (long) (baseXp * multiplier);

        // Award XP
        progressionService.awardXp(guild.getId(), player.getUniqueId(), XpSource.BLOCK_MINING, xp);
    }
}
