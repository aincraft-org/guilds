package dev.mintychochip.territory.influence;

import dev.mintychochip.territory.model.LookupResult;
import dev.mintychochip.territory.permission.GovernanceRegistry;
import dev.mintychochip.guilds.Guild;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;

import java.util.Optional;

/**
 * Maps player activity events onto {@link InfluenceEngine#accrue} (spec §4).
 * PvP kills carry the victim's primary guild so same-alliance kills accrue
 * nothing (the engine enforces the gate).
 */
public final class InfluenceListener implements Listener {

    private final GovernanceRegistry governance;
    private final InfluenceEngine engine;

    public InfluenceListener(GovernanceRegistry governance, InfluenceEngine engine) {
        this.governance = governance;
        this.engine = engine;
    }

    public InfluenceEngine engine() {
        return engine;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) {
            return;
        }
        String victimGuild = primaryGuild(victim.getUniqueId().toString()).orElse(null);
        accrueAt(victim.getLocation(), killer.getUniqueId().toString(),
                InfluenceSource.PVP_KILL, victimGuild);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            return; // handled by onPlayerDeath
        }
        if (!(event.getEntity().getKiller() instanceof Player killer)) {
            return;
        }
        accrueAt(event.getEntity().getLocation(), killer.getUniqueId().toString(),
                InfluenceSource.PVE_KILL, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        accrueAt(event.getBlock().getLocation(), event.getPlayer().getUniqueId().toString(),
                InfluenceSource.BLOCK_BREAK, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        accrueAt(event.getBlock().getLocation(), event.getPlayer().getUniqueId().toString(),
                InfluenceSource.BLOCK_PLACE, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        accrueAt(player.getLocation(), player.getUniqueId().toString(), InfluenceSource.CRAFT, null);
    }

    private void accrueAt(Location location, String holderId, InfluenceSource source, String victimGuildId) {
        if (location.getWorld() == null) {
            return;
        }
        LookupResult result = governance.territories().resolve(
                location.getWorld().getName(), location.getBlockX(), location.getBlockZ());
        if (!result.isContained()) {
            return;
        }
        Optional<String> guildId = primaryGuild(holderId);
        if (guildId.isEmpty()) {
            return;
        }
        engine.accrue(result.territoryId().orElseThrow(), guildId.get(), source,
                System.currentTimeMillis(), victimGuildId);
    }

    private Optional<String> primaryGuild(String holderId) {
        return governance.primaryGuildForMember(holderId).map(Guild::id);
    }
}
