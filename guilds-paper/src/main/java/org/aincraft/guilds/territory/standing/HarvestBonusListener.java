package org.aincraft.guilds.territory.standing;

import org.aincraft.guilds.territory.model.LookupResult;
import org.aincraft.guilds.territory.permission.GovernanceRegistry;
import org.aincraft.guilds.territory.permission.GuildBody;
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
import java.util.List;
import java.util.Optional;

/**
 * Applies the standing tier's harvest multiplier to block and mob drops
 * for governing-guild members inside the territory. Block drops use the
 * player's tool-aware Bukkit calculation. Mob drops intentionally use the
 * canonical {@link EntityDeathEvent#getDrops()} result: Bukkit exposes that
 * list after vanilla loot (including Looting), so this listener leaves the
 * originals unchanged, appends standing copies, and never regenerates loot.
 */
public final class HarvestBonusListener implements Listener {

    private final GovernanceRegistry governance;
    private final StandingEngine engine;

    public HarvestBonusListener(GovernanceRegistry governance, StandingEngine engine) {
        this.governance = governance;
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        double multiplier = multiplierAt(player, event.getBlock().getLocation());
        if (multiplier <= 1.0) {
            return;
        }
        Block block = event.getBlock();
        ItemStack tool = player.getInventory().getItemInMainHand();
        Collection<ItemStack> base = block.getDrops(tool, player);
        for (ItemStack drop : base) {
            int bonus = (int) Math.floor(drop.getAmount() * (multiplier - 1.0));
            if (bonus > 0) {
                ItemStack extra = drop.clone();
                extra.setAmount(bonus);
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), extra);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            return;
        }
        if (event.getEntity().getKiller() == null) {
            return;
        }
        Player player = event.getEntity().getKiller();
        double multiplier = multiplierAt(player, event.getEntity().getLocation());
        if (multiplier <= 1.0) {
            return;
        }
        for (ItemStack drop : List.copyOf(event.getDrops())) {
            int bonus = (int) Math.floor(drop.getAmount() * (multiplier - 1.0));
            if (bonus > 0) {
                ItemStack extra = drop.clone();
                extra.setAmount(bonus);
                event.getDrops().add(extra);
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
