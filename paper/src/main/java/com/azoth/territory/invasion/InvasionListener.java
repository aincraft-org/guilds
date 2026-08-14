package com.azoth.territory.invasion;

import org.aincraft.guilds.models.GuildBlock;
import org.aincraft.guilds.services.PlotService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Set;
import java.util.UUID;

/** Invasion-only destruction path; does not modify ordinary protection policy. */
public final class InvasionListener implements Listener {
    private final InvasionRuntime runtime;
    private final InvasionEngine engine;
    private final PlotService plots;
    private final Set<Material> allowlist;

    public InvasionListener(InvasionRuntime runtime, InvasionEngine engine, PlotService plots, Set<Material> allowlist) {
        this.runtime = runtime; this.engine = engine; this.plots = plots; this.allowlist = Set.copyOf(allowlist);
    }

    @EventHandler public void onDeath(EntityDeathEvent event) { runtime.onEntityDeath(event.getEntity(), System.currentTimeMillis()); }

    @EventHandler public void onExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        UUID id = InvasionMobTags.invasionId(entity.getPersistentDataContainer()).orElse(null);
        String guild = InvasionMobTags.guildId(entity.getPersistentDataContainer()).orElse(null);
        if (id == null || guild == null) return;
        event.blockList().removeIf(block -> !destroy(id, guild, block));
        event.setYield(0);
    }

    @EventHandler public void onChange(EntityChangeBlockEvent event) {
        Entity entity = event.getEntity();
        UUID id = InvasionMobTags.invasionId(entity.getPersistentDataContainer()).orElse(null);
        String guild = InvasionMobTags.guildId(entity.getPersistentDataContainer()).orElse(null);
        if (id == null || guild == null || !destroy(id, guild, event.getBlock())) event.setCancelled(true);
    }

    private boolean destroy(UUID id, String guild, Block block) {
        try {
            if (!runtime.status(guild).map(s -> s.invasionId().equals(id) && s.status() == InvasionStatus.ACTIVE).orElse(false)) return false;
            if (!allowlist.contains(block.getType())) return false;
            GuildBlock claim = plots.getGuildBlock(block.getChunk().getX(), block.getChunk().getZ(), block.getWorld().getName()).orElse(null);
            if (claim == null || !guild.equals(claim.getGuildId())) return false;
            InvasionTransition transition = engine.recordDestroyedBlock(id, System.currentTimeMillis());
            if (transition == InvasionTransition.NO_CHANGE) {
                runtime.cancel(guild, System.currentTimeMillis());
                return false;
            }
            if (transition == InvasionTransition.DEVASTATED) runtime.finish(guild, id);
            return true;
        } catch (RuntimeException failure) {
            runtime.cancel(guild, System.currentTimeMillis());
            return false;
        }
    }
}
