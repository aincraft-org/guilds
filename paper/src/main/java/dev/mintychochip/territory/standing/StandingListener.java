package dev.mintychochip.territory.standing;

import dev.mintychochip.territory.model.LookupResult;
import dev.mintychochip.territory.permission.GovernanceRegistry;
import dev.mintychochip.guilds.Guild;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Optional;

/**
 * Maps player activity events onto {@link StandingEngine#accrue} (spec §4).
 * Only members of the territory's governing guild accrue standing.
 */
public final class StandingListener implements Listener {

    private final GovernanceRegistry governance;
    private final StandingEngine engine;

    public StandingListener(GovernanceRegistry governance, StandingEngine engine) {
        this.governance = governance;
        this.engine = engine;
    }

    public StandingEngine engine() {
        return engine;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        accrueAt(event.getEntity().getLocation(), killer.getUniqueId().toString(),
                StandingSource.PVP_KILL);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            return; // players handled by PlayerDeathEvent
        }
        if (event.getEntity().getKiller() == null) {
            return;
        }
        accrueAt(event.getEntity().getLocation(), event.getEntity().getKiller().getUniqueId().toString(),
                StandingSource.PVE_KILL);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        accrueAt(event.getBlock().getLocation(), event.getPlayer().getUniqueId().toString(),
                StandingSource.BLOCK_BREAK);
    }

    private void accrueAt(Location location, String holderId, StandingSource source) {
        Optional<String> guildId = primaryGuild(holderId);
        if (guildId.isEmpty()) {
            return;
        }
        LookupResult result = governance.territories().resolve(
                location.getWorld().getName(), location.getBlockX(), location.getBlockZ());
        if (!result.isContained() || result.territoryId().isEmpty()) {
            return;
        }
        engine.accrue(result.territoryId().orElseThrow(), guildId.get(), source);
    }

    private Optional<String> primaryGuild(String holderId) {
        return governance.primaryGuildForMember(holderId).map(Guild::id);
    }
}
