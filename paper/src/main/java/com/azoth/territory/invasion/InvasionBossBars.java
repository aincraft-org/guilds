package com.azoth.territory.invasion;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class InvasionBossBars {
    private final int nearbyRadius;
    private final Map<UUID, Entry> entries = new HashMap<>();

    public InvasionBossBars(int nearbyRadius) { if (nearbyRadius < 0) throw new IllegalArgumentException(); this.nearbyRadius = nearbyRadius; }

    public BossBar bar(InvasionRecord record, int waveCount) {
        Entry entry = entries.get(record.invasionId());
        if (entry == null) {
            entry = new Entry(BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS), Math.max(record.currentWaveEntities().size(), 1));
            entries.put(record.invasionId(), entry);
        }
        update(entry, record, waveCount);
        return entry.bar;
    }
    public BossBar open(InvasionRecord record, int waveCount, int spawnedTotal) {
        Entry entry = entries.get(record.invasionId());
        if (entry == null) {
            entry = new Entry(BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS), Math.max(spawnedTotal, record.currentWaveEntities().size()));
            entries.put(record.invasionId(), entry);
        }
        update(entry, record, waveCount);
        return entry.bar;
    }

    public void update(InvasionRecord record, int waveCount) { Entry entry = entries.get(record.invasionId()); if (entry != null) update(entry, record, waveCount); }

    public void reconcile(InvasionRecord record, int waveCount, Set<UUID> residents) {
        if (record.status() != InvasionStatus.ACTIVE) { remove(record); return; }
        Entry entry = entries.get(record.invasionId());
        if (entry == null) entry = new Entry(BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS), record.currentWaveEntities().size());
        entries.put(record.invasionId(), entry);
        update(entry, record, waveCount);
        Set<UUID> desired = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (shouldShow(player, record, residents)) { desired.add(player.getUniqueId()); if (!entry.viewers.contains(player.getUniqueId())) player.showBossBar(entry.bar); }
            else if (entry.viewers.contains(player.getUniqueId())) player.hideBossBar(entry.bar);
        }
        entry.viewers.clear(); entry.viewers.addAll(desired);
    }

    public void remove(InvasionRecord record) {
        Entry entry = entries.remove(record.invasionId());
        if (entry == null) return;
        for (UUID id : entry.viewers) { Player player = Bukkit.getPlayer(id); if (player != null) player.hideBossBar(entry.bar); }
    }

    public boolean shouldShow(Player player, InvasionRecord record, Set<UUID> residents) {
        if (record.status() != InvasionStatus.ACTIVE) return false;
        if (residents.contains(player.getUniqueId())) return true;
        if (!record.worldId().equals(player.getWorld().getName())) return false;
        return player.getLocation().distanceSquared(new org.bukkit.Location(player.getWorld(), record.x(), record.y(), record.z())) <= (double) nearbyRadius * nearbyRadius;
    }
    private static void update(Entry entry, InvasionRecord record, int waveCount) {
        int living = record.currentWaveEntities().size();
        entry.bar.name(Component.text(record.guildName() + " Invasion — Wave " + (record.wave() + 1) + "/" + waveCount + " — Damage " + record.damage().percent() + "%"));
        entry.bar.progress(living == 0 ? 1f : entry.spawnedTotal == 0 ? 1f : clamp((float) living / entry.spawnedTotal));
        entry.bar.color(record.damage().percent() >= 75 ? BossBar.Color.PURPLE : BossBar.Color.RED);
    }

    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
    private static final class Entry {
        private final BossBar bar; private final int spawnedTotal; private final Set<UUID> viewers = new HashSet<>();
        private Entry(BossBar bar, int spawnedTotal) { this.bar = bar; this.spawnedTotal = Math.max(0, spawnedTotal); }
    }
}
