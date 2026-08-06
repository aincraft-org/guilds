package org.aincraft.guilds.listeners;


import org.aincraft.guilds.models.Nation;
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.services.NationService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.GuildService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Nation-related event listener handling PvP rules between allied/enemy nations.
 */
public class NationListener implements Listener {

    private final NationService nationService;
    private final GuildService guildService;
    private final ResidentService residentService;


    public NationListener(NationService nationService, GuildService guildService, ResidentService residentService) {
        this.nationService = nationService;
        this.guildService = guildService;
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
                .filter(Resident::hasGuild)
                .flatMap(r -> guildService.getGuild(r.getGuild()))
                .flatMap(guild -> nationService.getAllNations().stream()
                        .filter(n -> n.hasGuild(guild.getId()))
                        .findFirst())
                .orElse(null);
    }
}
