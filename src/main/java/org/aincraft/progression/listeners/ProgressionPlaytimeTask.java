package org.aincraft.progression.listeners;

import com.google.inject.Inject;
import org.aincraft.ChunkKey;
import org.aincraft.Guild;
import org.aincraft.progression.ProgressionConfig;
import org.aincraft.progression.ProgressionService;
import org.aincraft.progression.XpSource;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.TerritoryService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Objects;

/**
 * Periodic task that awards XP to players for being online in their guild's territory.
 * Runs every check-interval-seconds (default: 60 seconds).
 */
public class ProgressionPlaytimeTask extends BukkitRunnable {
    private final ProgressionService progressionService;
    private final GuildMemberService memberService;
    private final TerritoryService territoryService;
    private final ProgressionConfig config;

    @Inject
    public ProgressionPlaytimeTask(ProgressionService progressionService,
                                    GuildMemberService memberService,
                                    TerritoryService territoryService,
                                    ProgressionConfig config) {
        this.progressionService = Objects.requireNonNull(progressionService, "Progression service cannot be null");
        this.memberService = Objects.requireNonNull(memberService, "Member service cannot be null");
        this.territoryService = Objects.requireNonNull(territoryService, "Territory service cannot be null");
        this.config = Objects.requireNonNull(config, "Config cannot be null");
    }

    @Override
    public void run() {
        if (!config.isPlaytimeEnabled()) {
            return;
        }

        long xpPerMinute = config.getPlaytimeXpPerMinute();
        int intervalSeconds = config.getPlaytimeCheckInterval();

        // Calculate XP for this interval: (xp_per_minute / 60) * interval_seconds
        long xpForInterval = (xpPerMinute * intervalSeconds) / 60;

        if (xpForInterval <= 0) {
            return;
        }

        // Award XP to all online players in their guild's territory
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Check if player is in a guild
            Guild guild = memberService.getPlayerGuild(player.getUniqueId());
            if (guild == null) {
                continue;
            }

            // Check if player is in their guild's territory
            ChunkKey chunkKey = ChunkKey.from(player.getLocation().getChunk());
            Guild chunkOwner = territoryService.getChunkOwner(chunkKey);

            if (chunkOwner != null && chunkOwner.getId().equals(guild.getId())) {
                // Player is in their guild's territory
                progressionService.awardXp(guild.getId(), player.getUniqueId(), XpSource.PLAYTIME, xpForInterval);
            }
        }
    }
}
