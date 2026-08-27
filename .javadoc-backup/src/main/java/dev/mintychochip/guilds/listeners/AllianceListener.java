package dev.mintychochip.guilds.listeners;


import dev.mintychochip.guilds.models.Alliance;
import dev.mintychochip.guilds.models.Resident;
import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.services.AllianceService;
import dev.mintychochip.guilds.services.ResidentService;
import dev.mintychochip.guilds.services.GuildService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Alliance-related event listener handling PvP rules between allied/enemy alliances.
 */
public class AllianceListener implements Listener {

    /** The alliance service. */
    private final AllianceService allianceService;
    /** The guild service. */
    private final GuildService guildService;
    /** The resident service. */
    private final ResidentService residentService;


    /**
     * Creates a new alliance listener instance.
     * @param allianceService the alliance service
     * @param guildService the guild service
     * @param residentService the resident service
     */
    public AllianceListener(AllianceService allianceService, GuildService guildService, ResidentService residentService) {
        this.allianceService = allianceService;
        this.guildService = guildService;
        this.residentService = residentService;
    }

    /**
     * Handles the entity damage.
     * @param event the event
     */
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

    /**
     * Returns the player alliance.
     * @param player the player
     * @return the result
     */
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
