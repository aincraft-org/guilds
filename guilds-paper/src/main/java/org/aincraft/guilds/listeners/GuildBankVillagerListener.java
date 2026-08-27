package org.aincraft.guilds.listeners;

import org.aincraft.guilds.models.GuildBlock;
import org.aincraft.guilds.models.PlotTypes;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.MintGuildBankService;
import org.aincraft.guilds.services.PlotService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.territory.model.FacilityType;
import org.aincraft.guilds.territory.model.SettlementFacility;
import org.aincraft.guilds.territory.registry.FacilityRegistry;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/** Opens the player's canonical guild bank account at a tagged villager on a bank plot. */
public final class GuildBankVillagerListener implements Listener {
    private final JavaPlugin plugin;
    private final GuildService guildService;
    private final ResidentService residentService;
    private final PlotService plotService;
    private volatile MintGuildBankService bank;
    private volatile FacilityRegistry facilities;
    private volatile TerritoryRegistry territories;
    private final String scoreboardTag;

    public GuildBankVillagerListener(JavaPlugin plugin, GuildService guildService,
                                     ResidentService residentService, PlotService plotService,
                                     MintGuildBankService bank, String scoreboardTag) {
        this.plugin = Objects.requireNonNull(plugin);
        this.guildService = Objects.requireNonNull(guildService);
        this.residentService = Objects.requireNonNull(residentService);
        this.plotService = Objects.requireNonNull(plotService);
        this.bank = bank;
        this.scoreboardTag = scoreboardTag == null || scoreboardTag.isBlank() ? "GUILD_BANK" : scoreboardTag;
    }

    public void setBank(MintGuildBankService bank) { this.bank = bank; }

    public void setBankBuildings(FacilityRegistry facilities, TerritoryRegistry territories) {
        this.facilities = facilities;
        this.territories = territories;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        MintGuildBankService currentBank = bank;
        if (currentBank == null) {
            return;
        }
        currentBank.ensurePlayerAccount(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)
                || !villager.getScoreboardTags().contains(scoreboardTag)) return;
        // Cancel immediately for every tagged banker so vanilla trading never opens
        event.setCancelled(true);
        MintGuildBankService currentBank = bank;
        if (currentBank == null) {
            event.getPlayer().sendMessage(ChatColor.RED + "Mint guild bank is unavailable.");
            return;
        }
        Player player = event.getPlayer();
        var resident = residentService.getResident(player.getUniqueId());
        if (resident.isEmpty() || !resident.get().hasGuild()) return;
        var guild = guildService.getGuild(resident.get().getGuild());
        if (guild.isEmpty()) return;
        if (!isAtGuildBank(villager.getLocation(), guild.get().getId())) {
            player.sendMessage(ChatColor.RED + "Talk to a banker at your guild bank building to open an account.");
            return;
        }
        currentBank.openAccount(player.getUniqueId(), guild.get().getId()).whenComplete((result, error) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (error != null || result == null) player.sendMessage(ChatColor.RED + "Mint guild bank is unavailable.");
                    else if (result.status() == MintGuildBankService.Status.COMMITTED) player.sendMessage(ChatColor.GREEN + "Guild bank account opened.");
                    else if (result.status() == MintGuildBankService.Status.UNAUTHORIZED) player.sendMessage(ChatColor.RED + "You are not a current guild member.");
                    else player.sendMessage(ChatColor.RED + "Mint guild bank is unavailable.");
                }));
    }

    private boolean isAtGuildBank(Location location, String guildId) {
        if (location == null || location.getWorld() == null || guildId == null) {
            return false;
        }
        if (plotService.getGuildBlockAtLocation(location.getWorld().getName(), location.getBlockX(), location.getBlockZ())
                .filter(plot -> guildId.equals(plot.getGuildId()))
                .filter(GuildBankVillagerListener::isBankPlot)
                .isPresent()) {
            return true;
        }
        return isNearOwnedBankBuilding(location, guildId);
    }

    private boolean isNearOwnedBankBuilding(Location location, String guildId) {
        FacilityRegistry currentFacilities = facilities;
        TerritoryRegistry currentTerritories = territories;
        if (currentFacilities == null || currentTerritories == null || location.getWorld() == null) {
            return false;
        }
        String world = location.getWorld().getName();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        for (SettlementFacility facility : currentFacilities.list()) {
            if (facility.type() != FacilityType.BANK || !facility.worldId().equals(world)) {
                continue;
            }
            long dx = Math.abs((long) facility.x() - x);
            long dy = Math.abs((long) facility.y() - y);
            long dz = Math.abs((long) facility.z() - z);
            if (Math.max(Math.max(dx, dy), dz) > 8) {
                continue;
            }
            var territory = currentTerritories.get(facility.territoryId());
            if (territory.isEmpty()) {
                continue;
            }
            var governed = territory.get().governedByGuildId();
            if (governed.isEmpty() || governed.filter(guildId::equals).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBankPlot(GuildBlock plot) {
        return PlotTypes.BANK.equalsIgnoreCase(plot.getPlotType())
                || PlotTypes.BANK.equalsIgnoreCase(plot.getPlotTypeDefinition());
    }
}
