package com.azoth.territory.standing;

import com.azoth.territory.model.LookupResult;
import com.azoth.territory.permission.GovernanceRegistry;
import com.azoth.territory.permission.GuildBody;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Optional;

/**
 * Applies the standing tier's harvest multiplier to block and mob drops
 * for governing-guild members inside the territory (spec §6). Multiplies
 * base drops only; Fortune/Looting are never re-rolled.
 */
public final class HarvestBonusListener implements Listener {

    private final GovernanceRegistry governance;
    private final StandingEngine engine;

    public HarvestBonusListener(GovernanceRegistry governance, StandingEngine engine) {
        this.governance = governance;
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        double multiplier = multiplierAt(player, event.getBlock().getLocation());
        if (multiplier <= 1.0) {
            return;
        }
        Block block = event.getBlock();
        // Base drops without any tool enchantment (no-arg getDrops = empty hand):
        Collection<ItemStack> base = block.getDrops();
        for (ItemStack drop : base) {
            int bonus = (int) Math.floor(drop.getAmount() * (multiplier - 1.0));
            if (bonus > 0) {
                ItemStack extra = drop.clone();
                extra.setAmount(bonus);
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), extra);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) {
            return;
        }
        Player player = event.getEntity().getKiller();
        double multiplier = multiplierAt(player, event.getEntity().getLocation());
        if (multiplier <= 1.0) {
            return;
        }
        for (ItemStack drop : event.getDrops()) {
            int bonus = (int) Math.floor(drop.getAmount() * (multiplier - 1.0));
            if (bonus > 0) {
                ItemStack extra = drop.clone();
                extra.setAmount(bonus);
                event.getEntity().getWorld().dropItemNaturally(
                        event.getEntity().getLocation().add(0.5, 0.5, 0.5), extra);
            }
        }
    }

    /** Harvest multiplier for the player at the location, or 1.0 when ineligible. */
    private double multiplierAt(Player player, Location location) {
        Optional<String> guildId = governance.primaryGuildForMember(player.getUniqueId().toString())
                .map(GuildBody::id);
        if (guildId.isEmpty()) {
            return 1.0;
        }
        LookupResult result = governance.territories().resolve(
                location.getWorld().getName(), location.getBlockX(), location.getBlockZ());
        if (!result.isContained() || result.territoryId().isEmpty()) {
            return 1.0;
        }
        return engine.harvestMultiplierFor(result.territoryId().orElseThrow(), guildId.get());
    }
}
