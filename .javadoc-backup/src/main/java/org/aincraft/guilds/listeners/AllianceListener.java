package org.aincraft.guilds.listeners;


import org.aincraft.guilds.models.Alliance;
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.services.AllianceService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.GuildService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Alliance-related event listener handling PvP rules between allied/enemy alliances.
 */
public class AllianceListener implements Listener {

    private final AllianceService allianceService;
    private final GuildService guildService;
    private final ResidentService residentService;


    public AllianceListener(AllianceService allianceService, GuildService guildService, ResidentService residentService) {
        this.allianceService = allianceService;
        this.guildService = guildService;
        this.residentService = residentService;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) {
            return;
        }

        // Find alliances for both players
        Alliance victimAlliance = getPlayerAlliance(victim);
        Alliance attackerAlliance = getPlayerAlliance(attacker);

        if (victimAlliance == null || attackerAlliance == null) {
            return;
        }

        // Same alliance — reduce damage
        if (victimAlliance.getId().equals(attackerAlliance.getId())) {
            event.setDamage(event.getDamage() * 0.5);
            return;
        }

        // Allied alliances — reduce damage
        if (victimAlliance.isAlly(attackerAlliance.getName())) {
            event.setDamage(event.getDamage() * 0.5);
        }
    }

    private Alliance getPlayerAlliance(Player player) {
        return residentService.getResident(player.getUniqueId())
                .filter(Resident::hasGuild)
                .flatMap(r -> guildService.getGuild(r.getGuild()))
                .flatMap(guild -> allianceService.getAllAlliances().stream()
                        .filter(n -> n.hasGuild(guild.getId()))
                        .findFirst())
                .orElse(null);
    }
}
