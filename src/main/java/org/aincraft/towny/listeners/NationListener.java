package org.aincraft.towny.listeners;

import com.google.inject.Inject;
import org.aincraft.towny.models.Nation;
import org.aincraft.towny.models.Resident;
import org.aincraft.towny.models.Town;
import org.aincraft.towny.services.NationService;
import org.aincraft.towny.services.ResidentService;
import org.aincraft.towny.services.TownService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Nation-related event listener handling PvP rules between allied/enemy nations.
 */
public class NationListener implements Listener {

    private final NationService nationService;
    private final TownService townService;
    private final ResidentService residentService;

    @Inject
    public NationListener(NationService nationService, TownService townService, ResidentService residentService) {
        this.nationService = nationService;
        this.townService = townService;
        this.residentService = residentService;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) {
            return;
        }

        // Find nations for both players
        Nation victimNation = getPlayerNation(victim);
        Nation attackerNation = getPlayerNation(attacker);

        if (victimNation == null || attackerNation == null) {
            return;
        }

        // Same nation — reduce damage
        if (victimNation.getId().equals(attackerNation.getId())) {
            event.setDamage(event.getDamage() * 0.5);
            return;
        }

        // Allied nations — reduce damage
        if (victimNation.isAlly(attackerNation.getName())) {
            event.setDamage(event.getDamage() * 0.5);
        }
    }

    private Nation getPlayerNation(Player player) {
        return residentService.getResident(player.getUniqueId())
                .filter(Resident::hasTown)
                .flatMap(r -> townService.getTown(r.getTown()))
                .flatMap(town -> nationService.getAllNations().stream()
                        .filter(n -> n.hasTown(town.getId()))
                        .findFirst())
                .orElse(null);
    }
}
