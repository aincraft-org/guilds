package com.azoth.territory.invasion;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public final class InvasionBossBars {
    private final int nearbyRadius;
    public InvasionBossBars(int nearbyRadius) { if (nearbyRadius < 0) throw new IllegalArgumentException(); this.nearbyRadius = nearbyRadius; }
    public BossBar bar(InvasionRecord record, int waveCount) {
        int spawned = record.currentWaveEntities().size();
        float progress = 1f;
        return BossBar.bossBar(Component.text(record.guildName() + " Invasion — Wave " + (record.wave() + 1) + "/" + waveCount
                        + " — Damage " + record.damage().percent() + "%"), progress,
                record.damage().percent() >= 75 ? BossBar.Color.PURPLE : BossBar.Color.RED, BossBar.Overlay.PROGRESS);
    }
    public boolean shouldShow(Player player, InvasionRecord record, Set<UUID> residents) {
        if (residents.contains(player.getUniqueId())) return true;
        if (!record.worldId().equals(player.getWorld().getName())) return false;
        return player.getLocation().distanceSquared(new org.bukkit.Location(player.getWorld(), record.x(), record.y(), record.z())) <= (double) nearbyRadius * nearbyRadius;
    }
    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
}
